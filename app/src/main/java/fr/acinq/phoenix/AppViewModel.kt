/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.Futures
import akka.pattern.Patterns
import akka.util.Timeout
import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.msopentech.thali.toronionproxy.OnionProxyManager
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.eclair.*
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.*
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.PeerConnected
import fr.acinq.eclair.io.PeerDisconnected
import fr.acinq.eclair.payment.*
import fr.acinq.eclair.payment.receive.MultiPartHandler
import fr.acinq.eclair.payment.relay.Relayer
import fr.acinq.eclair.payment.send.PaymentInitiator
import fr.acinq.eclair.wire.*
import fr.acinq.phoenix.background.ChannelsWatcher
import fr.acinq.phoenix.events.*
import fr.acinq.phoenix.events.PayToOpenResponse
import fr.acinq.phoenix.main.InAppNotifications
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.encrypt.EncryptedSeed
import fr.acinq.phoenix.utils.tor.TorConnectionStatus
import fr.acinq.phoenix.utils.tor.TorEventHandler
import fr.acinq.phoenix.utils.tor.TorHelper
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import scala.Option
import scala.Tuple2
import scala.collection.JavaConverters
import scala.collection.immutable.Seq
import scala.collection.immutable.`Seq$`
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Either
import scala.util.Left
import scodec.bits.`ByteVector$`
import java.io.IOException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.system.measureTimeMillis
import scala.collection.immutable.List as ScalaList

data class TrampolineFeeSetting(val feeBase: MilliSatoshi, val feePercent: Double, val cltvExpiry: CltvExpiryDelta)
data class SwapInSettings(val feePercent: Double)
data class Xpub(val xpub: String, val path: String)
data class NetworkInfo(var networkConnected: Boolean, val electrumServer: ElectrumServer?, val lightningConnected: Boolean, val torConnections: HashMap<String, TorConnectionStatus>)
data class ElectrumServer(val electrumAddress: String, val blockHeight: Int, val tipTime: Long)

sealed class KitState {
  object Off : KitState()
  sealed class Bootstrap : KitState() {
    object Init : Bootstrap()
    object Tor : Bootstrap()
    object Node : Bootstrap()
  }

  data class Started(val kit: Kit, val api: Eclair, val xpub: Xpub) : KitState()
  sealed class Error : KitState() {
    data class Generic(val message: String) : Error()
    data class Tor(val message: String) : Error()
    data class InvalidElectrumAddress(val address: String) : Error()
    object InvalidBiometric : Error()
    object WrongPassword : Error()
    object NoConnectivity : Error()
    object UnreadableData : Error()
  }
}

class AppViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(AppViewModel::class.java)

  private val shortTimeout = Timeout(Duration.create(10, TimeUnit.SECONDS))
  private val longTimeout = Timeout(Duration.create(30, TimeUnit.SECONDS))

  val currentURIIntent = MutableLiveData<String>()
  val currentNav = MutableLiveData<Int>()
  val networkInfo = MutableLiveData<NetworkInfo>()
  val pendingSwapIns = MutableLiveData(HashMap<String, SwapInPending>())
  val payments = MutableLiveData<List<PlainPayment>>()
  val notifications = MutableLiveData(HashSet<InAppNotifications>())
  val navigationEvent = SingleLiveEvent<Any>()
  val trampolineFeeSettings = MutableLiveData<List<TrampolineFeeSetting>>()
  val swapInSettings = MutableLiveData<SwapInSettings>()
  val balance = MutableLiveData<MilliSatoshi>()
  val state = MutableLiveData<KitState>()
  val torManager = MutableLiveData<OnionProxyManager>()
  val kit: Kit? get() = if (state.value is KitState.Started) (state.value as KitState.Started).kit else null
  val api: Eclair? get() = if (state.value is KitState.Started) (state.value as KitState.Started).api else null

  init {
    currentNav.value = R.id.startup_fragment
    state.value = KitState.Off
    networkInfo.value = Constants.DEFAULT_NETWORK_INFO
    balance.value = MilliSatoshi(0)
    trampolineFeeSettings.value = Constants.DEFAULT_TRAMPOLINE_SETTINGS
    swapInSettings.value = Constants.DEFAULT_SWAP_IN_SETTINGS
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    checkWalletContext()
  }

  override fun onCleared() {
    EventBus.getDefault().unregister(this)
    shutdown()

    super.onCleared()
    log.debug("appkit has been cleared")
  }

  fun hasWalletBeenSetup(context: Context): Boolean {
    return Wallet.getSeedFile(context).exists()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentSent) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentFailed) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentReceived) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PayToOpenRequestEvent) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: SwapInPending) {
    pendingSwapIns.value?.run {
      put(event.bitcoinAddress(), event)
      pendingSwapIns.postValue(this)
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: SwapInConfirmed) {
    // a confirmed swap-in means that a channel was opened ; this event is stored as an incoming payment in payment database.
    kit?.run {
      try {
        log.info("saving successful swap-in=$event as incoming payment")
        val pr = doGeneratePaymentRequest(
          description = "On-chain payment to ${event.bitcoinAddress()}",
          amount_opt = Option.apply(event.amount()),
          routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>(),
          timeout = Timeout(Duration.create(10, TimeUnit.MINUTES)),
          paymentType = PaymentType.SwapIn())
        nodeParams().db().payments().receiveIncomingPayment(pr.paymentHash(), event.amount(), System.currentTimeMillis())
        log.info("successful swap-in=$event as been saved with payment_hash=${pr.paymentHash()}, amount=${pr.amount()}")
        pendingSwapIns.value?.remove(event.bitcoinAddress())
        navigationEvent.postValue(PaymentReceived(pr.paymentHash(),
          ScalaList.empty<PaymentReceived.PartialPayment>().`$colon$colon`(PaymentReceived.PartialPayment(event.amount(), ByteVector32.Zeroes(), System.currentTimeMillis()))))
      } catch (e: Exception) {
        log.error("failed to create and settle payment request placeholder for ${event.bitcoinAddress()}: ", e)
      }
    } ?: log.error("could not create and settle placeholder for on-chain payment because kit is not initialized")
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: BalanceEvent) {
    balance.postValue(event.balance)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.ElectrumReady) {
    log.debug("received electrum ready=$event")
    networkInfo.value = networkInfo.value?.copy(electrumServer = ElectrumServer(electrumAddress = event.serverAddress().toString(), blockHeight = event.height(), tipTime = event.tip().time()))
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.`ElectrumDisconnected$`) {
    log.debug("received electrum disconnected $event")
    networkInfo.value = networkInfo.value?.copy(electrumServer = null)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PeerConnected) {
    log.debug("received peer connected $event")
    networkInfo.value = networkInfo.value?.copy(lightningConnected = true)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PeerDisconnected) {
    log.debug("received peer disconnected $event")
    networkInfo.value = networkInfo.value?.copy(lightningConnected = false)
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: ChannelClosingEvent) {
    // store channel closing event as an outgoing payment in database.
    kit?.run {
      val id = UUID.randomUUID()
      val preimage = `package$`.`MODULE$`.randomBytes32()
      val paymentHash = Crypto.hash256(preimage.bytes())
      val date = System.currentTimeMillis()
      val fakeRecipientId = `package$`.`MODULE$`.randomKey().publicKey()
      val paymentCounterpart = OutgoingPayment(
        /* id and parent id */ id, id,
        /* use arbitrary external id to designate payment as channel closing counterpart */ Option.apply("closing-${event.channelId}"),
        /* placeholder payment hash */ paymentHash,
        /* type of payment */ "ClosingChannel",
        /* balance */ event.balance,
        /* recipient amount */ event.balance,
        /* fake recipient id */ fakeRecipientId,
        /* creation date */ date,
        /* payment request */ Option.empty(),
        /* payment is always successful */ OutgoingPaymentStatus.`Pending$`.`MODULE$`)
      nodeParams().db().payments().addOutgoingPayment(paymentCounterpart)

      val partialPayment = PaymentSent.PartialPayment(id, event.balance, MilliSatoshi(0), ByteVector32.Zeroes(), Option.empty(), date)
      val paymentCounterpartSent = PaymentSent(id, paymentHash, preimage, event.balance, fakeRecipientId,
        ScalaList.empty<PaymentSent.PartialPayment>().`$colon$colon`(partialPayment))
      nodeParams().db().payments().updateOutgoingPayment(paymentCounterpartSent)
    }
  }

  fun refreshPayments() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        kit?.let {
          var p: List<PlainPayment>? = ArrayList()
          val t = measureTimeMillis {
            p = JavaConverters.seqAsJavaListConverter(it.nodeParams().db().payments().listPaymentsOverview(50)).asJava()
          }
          log.debug("list payments in ${t}ms")
          payments.postValue(p)
        } ?: log.info("kit non initialized, cannot list payments")
      }
    }
  }

  suspend fun getSentPaymentsFromParentId(parentId: UUID): List<OutgoingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          var payments: List<OutgoingPayment> = ArrayList()
          val t = measureTimeMillis {
            payments = JavaConverters.seqAsJavaListConverter(nodeParams().db().payments().listOutgoingPayments(parentId)).asJava()
          }
          log.debug("get sent payment details in ${t}ms")
          payments
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  suspend fun getReceivedPayment(paymentHash: ByteVector32): Option<IncomingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          var payment: Option<IncomingPayment> = Option.empty()
          val t = measureTimeMillis {
            payment = nodeParams().db().payments().getIncomingPayment(paymentHash)
          }
          log.debug("get received payment details in ${t}ms")
          payment
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  suspend fun generatePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>): PaymentRequest {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          val hop = PaymentRequest.ExtraHop(Wallet.ACINQ.nodeId(), ShortChannelId.peerId(nodeParams().nodeId()), MilliSatoshi(1000), 100, CltvExpiryDelta(144))
          val routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>().`$colon$colon`(ScalaList.empty<PaymentRequest.ExtraHop>().`$colon$colon`(hop))
          doGeneratePaymentRequest(description, amount_opt, routes, paymentType = PaymentType.Standard())
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @WorkerThread
  private fun doGeneratePaymentRequest(description: String,
    amount_opt: Option<MilliSatoshi>,
    routes: ScalaList<ScalaList<PaymentRequest.ExtraHop>>,
    timeout: Timeout = shortTimeout,
    paymentType: String): PaymentRequest {
    return kit?.run {
      val f = Patterns.ask(paymentHandler(),
        MultiPartHandler.ReceivePayment(
          /* amount */ amount_opt,
          /* description */ description,
          /* expiry seconds */ Option.empty(),
          /* extra routing info */ routes,
          /* fallback onchain address */ Option.empty(),
          /* payment preimage */ Option.empty(),
          /* Standard, SwapIn,... */ paymentType), timeout)
      Await.result(f, Duration.Inf()) as PaymentRequest
    } ?: throw KitNotInitialized()
  }

  suspend fun requestSwapOut(amount: Satoshi, address: String, feeratePerKw: Long) {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          log.info("sending swap-out request to switchboard for address=$address with amount=$amount")
          switchboard().tell(Peer.SendSwapOutRequest(Wallet.ACINQ.nodeId(), amount, address, feeratePerKw), ActorRef.noSender())
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  suspend fun sendSwapIn() {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          switchboard().tell(Peer.SendSwapInRequest(Wallet.ACINQ.nodeId()), ActorRef.noSender())
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  /**
   * Extracts the worst case (fee, ctlv expiry delta) scenario from the routing hints in a payment request. If the payment request has no routing hints, return (0, 0).
   */
  private fun getPessimisticRouteSettingsFromHint(amount: MilliSatoshi, paymentRequest: PaymentRequest): Pair<MilliSatoshi, CltvExpiryDelta> {
    val aggregateByRoutes = JavaConverters.asJavaCollectionConverter(paymentRequest.routingInfo()).asJavaCollection().toList()
      .map {
        // get the aggregate (fee, expiry) for this route
        JavaConverters.asJavaCollectionConverter(it).asJavaCollection().toList()
          .map { h -> Pair(`package$`.`MODULE$`.nodeFee(h.feeBase(), h.feeProportionalMillionths(), amount), h.cltvExpiryDelta()) }
          .fold(Pair(MilliSatoshi(0), CltvExpiryDelta(0))) { a, b -> Pair(a.first.`$plus`(b.first), a.second.`$plus`(b.second)) }
      }
    // return (max of fee, max of cltv expiry delta)
    return Pair(MilliSatoshi(aggregateByRoutes.map { p -> p.first.toLong() }.max() ?: 0),
      CltvExpiryDelta(aggregateByRoutes.map { p -> p.second.toInt() }.max() ?: 0))
  }

  @UiThread
  suspend fun sendPaymentRequest(amount: MilliSatoshi, paymentRequest: PaymentRequest, subtractFee: Boolean) {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit?.run {
          val cltvExpiryDelta = if (paymentRequest.minFinalCltvExpiryDelta().isDefined) paymentRequest.minFinalCltvExpiryDelta().get() else Channel.MIN_CLTV_EXPIRY_DELTA()
          val isTrampoline = paymentRequest.nodeId() != Wallet.ACINQ.nodeId()

          val sendRequest: Any = if (isTrampoline) {
            // 1 - compute trampoline fee settings for this payment
            // note that if we have to subtract the fee from the amount, use ONLY the most expensive trampoline setting option, to make sure that the payment will go through
            val feeSettingsDefault = if (subtractFee) listOf(trampolineFeeSettings.value!!.last()) else trampolineFeeSettings.value!!
            val feeSettingsFromHints = getPessimisticRouteSettingsFromHint(amount, paymentRequest)
            log.info("most expensive fee/expiry from payment request hints=$feeSettingsFromHints")
            val finalTrampolineFeesList = JavaConverters.asScalaBufferConverter(feeSettingsDefault
              .map {
                // fee = trampoline_base + trampoline_percent * amount + fee_from_hint
                Tuple2(it.feeBase.`$plus`(amount.`$times`(it.feePercent)).`$plus`(feeSettingsFromHints.first), it.cltvExpiry.`$plus`(feeSettingsFromHints.second))
              })
              .asScala().toList()

            // 2 - compute amount to send, which changes if fees must be subtracted from it (empty wallet)
            val amountFinal = if (subtractFee) amount.`$minus`(finalTrampolineFeesList.head()._1) else amount

            // 3 - build trampoline payment object
            log.info("sending payment (trampoline) [ amount=$amountFinal, fees=$finalTrampolineFeesList, subtractFee=$subtractFee ] for pr=$paymentRequest")
            PaymentInitiator.SendTrampolinePaymentRequest(
              /* amount to send */ amountFinal,
              /* payment request */ paymentRequest,
              /* trampoline node public key */ Wallet.ACINQ.nodeId(),
              /* fees and expiry delta for the trampoline node */ finalTrampolineFeesList,
              /* final cltv expiry delta */ cltvExpiryDelta,
              /* route params */ Option.apply(null))
          } else {
            log.info("sending payment (direct) [ amount=$amount ] for pr=$paymentRequest")
            val customTlvs = `Seq$`.`MODULE$`.empty<Any>() as Seq<GenericTlv>
            PaymentInitiator.SendPaymentRequest(
              /* amount to send */ amount,
              /* paymentHash */ paymentRequest.paymentHash(),
              /* payment target */ paymentRequest.nodeId(),
              /* max attempts */ 5,
              /* final cltv expiry delta */ cltvExpiryDelta,
              /* payment request */ Option.apply(paymentRequest),
              /* external id */ Option.empty(),
              /* assisted routes */ paymentRequest.routingInfo(),
              /* route params */ Option.apply(null),
              /* custom cltvs */ customTlvs)
          }

          val res = Await.result(Patterns.ask(paymentInitiator(), sendRequest, shortTimeout), Duration.Inf())
          if (res is PaymentFailed) {
            val failure = res.failures().mkString(", ")
            log.error("payment has failed: [ $failure ])")
            throw RuntimeException("Payment failure: $failure")
          }

          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  fun acceptPayToOpen(paymentHash: ByteVector32) {
    kit?.system()?.eventStream()?.publish(AcceptPayToOpen(paymentHash))
  }

  @UiThread
  fun rejectPayToOpen(paymentHash: ByteVector32) {
    kit?.system()?.eventStream()?.publish(RejectPayToOpen(paymentHash))
  }

  suspend fun getChannels(): Iterable<RES_GETINFO> = getChannels(null)

  /**
   * Retrieves the list of channels from the router. Can filter by state.
   */
  @UiThread
  suspend fun getChannels(state: State?): Iterable<RES_GETINFO> {
    return coroutineScope {
      async(Dispatchers.Default) {
        api?.run {
          val res = Await.result(channelsInfo(Option.apply(null), shortTimeout), Duration.Inf()) as scala.collection.Iterable<RES_GETINFO>
          val channels = JavaConverters.asJavaIterableConverter(res).asJava()
          state?.let {
            channels.filter { c -> c.state() == state }
          } ?: channels
        } ?: ArrayList()
      }
    }.await()
  }

  /**
   * Retrieves a channel from the router, using its long channel Id.
   */
  @UiThread
  suspend fun getChannel(channelId: ByteVector32): RES_GETINFO? {
    return coroutineScope {
      async(Dispatchers.Default) {
        api?.run {
          Await.result(channelInfo(Left.apply(channelId), shortTimeout), Duration.Inf()) as RES_GETINFO
        }
      }
    }.await()
  }

  @UiThread
  suspend fun mutualCloseAllChannels(address: String) = withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
    if (api != null && kit != null) {
      delay(500)
      val closeScriptPubKey = Option.apply(Script.write(`package$`.`MODULE$`.addressToPublicKeyScript(address, Wallet.getChainHash())))
      val channelIds = prepareClosing(`NORMAL$`.`MODULE$`)
      log.info("requesting to mutually close channels=$channelIds")
      val closingResult = Await.result(api!!.close(channelIds, closeScriptPubKey, longTimeout), Duration.Inf())
      val successfullyClosed = handleClosingResult(closingResult)
      if (successfullyClosed == channelIds.size()) {
        Unit
      } else {
        throw ChannelsNotClosed(channelIds.size() - successfullyClosed)
      }
    } else throw KitNotInitialized()
  }

  @UiThread
  suspend fun forceCloseAllChannels() = withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
    if (api != null && kit != null) {
      delay(500)
      val channelIds = prepareClosing()
      log.info("requesting to *force* close channels=$channelIds")
      val closingResult = Await.result(api!!.forceClose(channelIds, longTimeout), Duration.Inf())
      val successfullyClosed = handleClosingResult(closingResult)
      if (successfullyClosed == channelIds.size()) {
        Unit
      } else {
        throw ChannelsNotClosed(channelIds.size() - successfullyClosed)
      }
    } else throw KitNotInitialized()
  }

  /** Create a (scala) list of ids of channels to be closed. */
  @WorkerThread
  private suspend fun prepareClosing(state: State? = null): ScalaList<Either<ByteVector32, ShortChannelId>> {
    return getChannels(state).map {
      val id: Either<ByteVector32, ShortChannelId> = Left.apply(it.channelId())
      id
    }.run {
      JavaConverters.asScalaIteratorConverter(iterator()).asScala().toList()
    }
  }

  @WorkerThread
  private fun handleClosingResult(result: scala.collection.immutable.Map<Either<ByteVector32, ShortChannelId>, Either<Throwable, ChannelCommandResponse>>): Int {
    // read the result
    val iterator = result.iterator()
    var successfullyClosed = 0
    while (iterator.hasNext()) {
      val res = iterator.next()
      val outcome = res._2
      if (outcome.isRight) {
        log.info("successfully forced closed channel=${res._1}")
        successfullyClosed++
      } else {
        log.info("failed to force close channel=${res._1}: ", outcome.left() as Throwable)
      }
    }
    return successfullyClosed
  }

  /**
   * Send a reconnect event to the ACINQ node.
   */
  @UiThread
  fun reconnectToPeer() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        kit?.run {
          log.info("sending connect to ACINQ actor")
          switchboard().tell(Peer.Connect(Wallet.ACINQ.nodeId(), Option.apply(Wallet.ACINQ.address())), ActorRef.noSender())
        } ?: log.debug("appkit not ready yet")
      }
    }
  }

  @WorkerThread
  fun reconnectTor() {
    torManager.value?.run { enableNetwork(true) }
  }

  @UiThread
  suspend fun getTorInfo(cmd: String): String {
    return coroutineScope {
      async(Dispatchers.IO) {
        torManager.value?.run { getInfo(cmd /*"status/bootstrap-phase"*/) } ?: throw RuntimeException("onion proxy manager not available")
      }
    }.await()
  }

  /**
   * This method launches the node startup process.
   */
  @UiThread
  fun startKit(context: Context, pin: String) {
    when (state.value) {
      is KitState.Error, is KitState.Bootstrap, is KitState.Started -> log.info("ignore startup attempt in state=${state.value?.javaClass?.simpleName}")
      else -> {
        state.value = KitState.Bootstrap.Init
        viewModelScope.launch {
          withContext(Dispatchers.Default) {
            try {
              val res = startNode(context, pin)
              state.postValue(res)
              res.kit.switchboard().tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ), ActorRef.noSender())
              ChannelsWatcher.schedule(context)
              Prefs.setLastVersionUsed(context, BuildConfig.VERSION_CODE)
            } catch (t: Throwable) {
              log.warn("aborted node startup!")
              closeConnections()
              when (t) {
                is GeneralSecurityException -> {
                  log.debug("user entered wrong PIN")
                  state.postValue(KitState.Error.WrongPassword)
                }
                is NetworkException, is UnknownHostException -> {
                  log.info("network error: ", t)
                  state.postValue(KitState.Error.NoConnectivity)
                }
                is IOException, is IllegalAccessException -> {
                  log.error("seed file not readable: ", t)
                  state.postValue(KitState.Error.UnreadableData)
                }
                is InvalidElectrumAddress -> {
                  log.error("cannot start with invalid electrum address: ", t)
                  state.postValue(KitState.Error.InvalidElectrumAddress(t.address))
                }
                is TorSetupException -> {
                  log.error("error when bootstrapping TOR: ", t)
                  state.postValue(KitState.Error.Tor(t.localizedMessage ?: t.javaClass.simpleName))
                }
                else -> {
                  log.error("error when starting node: ", t)
                  state.postValue(KitState.Error.Generic(t.localizedMessage ?: t.javaClass.simpleName))
                }
              }
            }
          }
        }
      }
    }
  }

  private fun closeConnections() {
    kit?.run {
      system().shutdown()
      nodeParams().db().audit().close()
      nodeParams().db().channels().close()
      nodeParams().db().network().close()
      nodeParams().db().peers().close()
      nodeParams().db().pendingRelay().close()
    } ?: log.warn("could not close kit connections because kit is not initialized!")
  }

  public fun shutdown() {
    closeConnections()
    balance.postValue(MilliSatoshi(0))
    networkInfo.postValue(Constants.DEFAULT_NETWORK_INFO)
    state.postValue(KitState.Off)
  }

  @WorkerThread
  private fun cancelBackgroundJobs(context: Context) {
    val workManager = WorkManager.getInstance(context)
    try {
      val jobs = workManager.getWorkInfosByTag(ChannelsWatcher.WATCHER_WORKER_TAG).get()
      if (jobs.isEmpty()) {
        log.info("no background jobs found")
      } else {
        for (job in jobs) {
          log.info("found a background job={}", job)
          workManager.cancelWorkById(job.id).result.get()
          log.info("successfully cancelled job={}", job)
        }
      }
    } catch (e: Exception) {
      log.error("failed to retrieve or cancel background jobs: ", e)
      throw RuntimeException("could not cancel background jobs")
    }
  }

  @WorkerThread
  private fun checkConnectivity(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (cm.activeNetworkInfo == null || !cm.activeNetworkInfo?.isConnected!!) {
      throw NetworkException()
    }
  }

  @WorkerThread
  private fun startNode(context: Context, pin: String): KitState.Started {
    log.info("starting up node...")

    // load config from libs + application.conf in resources
    val defaultConfig = ConfigFactory.load()
    val config = Wallet.getOverrideConfig(context).withFallback(defaultConfig)
    val system = ActorSystem.create("system", config)
    system.registerOnTermination {
      log.info("system has been shutdown, all actors are terminated")
    }

    checkConnectivity(context)
    cancelBackgroundJobs(context)

    if (Prefs.isTorEnabled(context)) {
      log.info("using TOR...")
      state.postValue(KitState.Bootstrap.Tor)
      torManager.postValue(TorHelper.bootstrap(context, object : TorEventHandler() {
        override fun onConnectionUpdate(name: String, status: TorConnectionStatus) {
          networkInfo.value?.apply {
            networkInfo.postValue(copy(
              networkConnected = (status == TorConnectionStatus.CONNECTED),
              torConnections = torConnections.apply { this[name] = status }
            ))
          }
        }
      }))
      log.info("TOR has been bootstrapped")
    } else {
      log.info("using clear connection...")
    }

    state.postValue(KitState.Bootstrap.Node)
    val mnemonics = String(Hex.decode(EncryptedSeed.readSeedFile(context, pin)), Charsets.UTF_8)
    log.info("seed successfully read")
    val seed = `ByteVector$`.`MODULE$`.apply(MnemonicCode.toSeed(mnemonics, "").toArray())

    val master = DeterministicWallet.generate(seed)
    // we compute various things based on master
    val address = Wallet.buildAddress(master)
    val xpub = Wallet.buildXpub(master)

    Class.forName("org.sqlite.JDBC")
    Wallet.getOverrideConfig(context)
    val setup = Setup(Wallet.getDatadir(context), Option.apply(seed), Option.empty(), Option.apply(address), system)
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get())
    log.info("node setup ready, running version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

    val nodeSupervisor = system!!.actorOf(Props.create(EclairSupervisor::class.java), "EclairSupervisor")
    system.eventStream().subscribe(nodeSupervisor, ChannelStateChanged::class.java)
    system.eventStream().subscribe(nodeSupervisor, ChannelSignatureSent::class.java)
    system.eventStream().subscribe(nodeSupervisor, Relayer.OutgoingChannels::class.java)
    system.eventStream().subscribe(nodeSupervisor, PeerConnected::class.java)
    system.eventStream().subscribe(nodeSupervisor, PeerDisconnected::class.java)
    system.eventStream().subscribe(nodeSupervisor, PaymentEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapOutResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInPending::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInConfirmed::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenRequestEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, ElectrumClient.ElectrumEvent::class.java)

    system.scheduler().schedule(Duration.Zero(), FiniteDuration(10, TimeUnit.MINUTES),
      Runnable {
        Wallet.httpClient.newCall(Request.Builder().url(Constants.PRICE_RATE_API).build()).enqueue(getExchangeRateHandler(context))
        Wallet.httpClient.newCall(Request.Builder().url(Constants.MXN_PRICE_RATE_API).build()).enqueue(getMXNRateHandler(context))
      }, system.dispatcher())

    val kit = Await.result(setup.bootstrap(), Duration.create(60, TimeUnit.SECONDS))
    log.info("bootstrap complete")
    return KitState.Started(kit, EclairImpl(kit), xpub)
  }

  private fun getExchangeRateHandler(context: Context): Callback {
    return object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve exchange rates: ${e.localizedMessage}")
      }

      override fun onResponse(call: Call, response: Response) {
        if (!response.isSuccessful) {
          log.warn("could not retrieve exchange rates, api responds with ${response.code()}")
        } else {
          response.body()?.let {
            try {
              val json = JSONObject(it.string())
              saveRate(context, "AUD") { json.getJSONObject("AUD").getDouble("last").toFloat() }
              saveRate(context, "BRL") { json.getJSONObject("BRL").getDouble("last").toFloat() }
              saveRate(context, "CAD") { json.getJSONObject("CAD").getDouble("last").toFloat() }
              saveRate(context, "CHF") { json.getJSONObject("CHF").getDouble("last").toFloat() }
              saveRate(context, "CLP") { json.getJSONObject("CLP").getDouble("last").toFloat() }
              saveRate(context, "CNY") { json.getJSONObject("CNY").getDouble("last").toFloat() }
              saveRate(context, "DKK") { json.getJSONObject("DKK").getDouble("last").toFloat() }
              saveRate(context, "EUR") { json.getJSONObject("EUR").getDouble("last").toFloat() }
              saveRate(context, "GBP") { json.getJSONObject("GBP").getDouble("last").toFloat() }
              saveRate(context, "HKD") { json.getJSONObject("HKD").getDouble("last").toFloat() }
              saveRate(context, "INR") { json.getJSONObject("INR").getDouble("last").toFloat() }
              saveRate(context, "ISK") { json.getJSONObject("ISK").getDouble("last").toFloat() }
              saveRate(context, "JPY") { json.getJSONObject("JPY").getDouble("last").toFloat() }
              saveRate(context, "KRW") { json.getJSONObject("KRW").getDouble("last").toFloat() }
              saveRate(context, "NZD") { json.getJSONObject("NZD").getDouble("last").toFloat() }
              saveRate(context, "PLN") { json.getJSONObject("PLN").getDouble("last").toFloat() }
              saveRate(context, "RUB") { json.getJSONObject("RUB").getDouble("last").toFloat() }
              saveRate(context, "SEK") { json.getJSONObject("SEK").getDouble("last").toFloat() }
              saveRate(context, "SGD") { json.getJSONObject("SGD").getDouble("last").toFloat() }
              saveRate(context, "THB") { json.getJSONObject("THB").getDouble("last").toFloat() }
              saveRate(context, "TWD") { json.getJSONObject("TWD").getDouble("last").toFloat() }
              saveRate(context, "USD") { json.getJSONObject("USD").getDouble("last").toFloat() }
              Prefs.setExchangeRateTimestamp(context, System.currentTimeMillis())
            } catch (e: Exception) {
              log.error("invalid body for price rate api")
            } finally {
              it.close()
            }
          } ?: log.warn("exchange rate body is null")
        }
      }
    }
  }

  private fun getMXNRateHandler(context: Context): Callback {
    return object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve MXN rates: ${e.localizedMessage}")
      }

      override fun onResponse(call: Call, response: Response) {
        if (!response.isSuccessful) {
          log.warn("could not retrieve MXN rates, api responds with ${response.code()}")
        } else {
          response.body()?.let { body ->
            saveRate(context, "MXN") { JSONObject(body.string()).getJSONObject("payload").getDouble("last").toFloat() }
            body.close()
          } ?: log.warn("MXN rate body is null")
        }
      }
    }
  }

  fun saveRate(context: Context, code: String, rateBlock: () -> Float) {
    val rate = try {
      rateBlock.invoke()
    } catch (e: Exception) {
      log.error("failed to read rate for $code: ", e)
      -1.0f
    }
    Prefs.setExchangeRate(context, code, rate)
  }

  private fun checkWalletContext() {
    Wallet.httpClient.newCall(Request.Builder().url(Constants.WALLET_CONTEXT_URL).build()).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve wallet context from remote: ", e)
      }

      override fun onResponse(call: Call, response: Response) {
        val body = response.body()
        if (response.isSuccessful && body != null) {
          try {
            val json = JSONObject(body.string()).getJSONObject(BuildConfig.CHAIN)
            log.debug("wallet context responded with {}", json.toString(2))
            // -- version context
            val installedVersion = BuildConfig.VERSION_CODE
            val latestVersion = json.getInt("version")
            val latestCriticalVersion = json.getInt("latest_critical_version")
            notifications.value?.run {
              if (installedVersion < latestCriticalVersion) {
                log.info("a critical update (v$latestCriticalVersion) is deemed available")
                add(InAppNotifications.UPGRADE_WALLET_CRITICAL)
              } else if (latestVersion - installedVersion >= 2) {
                add(InAppNotifications.UPGRADE_WALLET)
              } else {
                remove(InAppNotifications.UPGRADE_WALLET_CRITICAL)
                remove(InAppNotifications.UPGRADE_WALLET)
              }
              notifications.postValue(this)
            }

            // -- trampoline settings
            val trampolineArray = json.getJSONObject("trampoline").getJSONObject("v2").getJSONArray("attempts")
            val trampolineSettingsList = ArrayList<TrampolineFeeSetting>()
            for (i in 0 until trampolineArray.length()) {
              val setting: JSONObject = trampolineArray.get(i) as JSONObject
              trampolineSettingsList += TrampolineFeeSetting(
                feeBase = Converter.any2Msat(Satoshi(setting.getLong("fee_base_sat"))),
                feePercent = setting.getDouble("fee_percent"),
                cltvExpiry = CltvExpiryDelta(setting.getInt("cltv_expiry")))
            }

            trampolineSettingsList.sortedWith(compareBy({ it.feePercent }, { it.feeBase }))
            trampolineFeeSettings.postValue(trampolineSettingsList)
            log.info("trampoline settings set to $trampolineSettingsList")

            // -- swap-in settings
            val remoteSwapInSettings = SwapInSettings(feePercent = json.getJSONObject("swap_in").getJSONObject("v1").getDouble("fee_percent"))
            swapInSettings.postValue(remoteSwapInSettings)
            log.info("swap_in settings set to $remoteSwapInSettings")
          } catch (e: Exception) {
            log.error("error when reading wallet context body: ", e)
          }
        } else {
          log.warn("could not retrieve wallet context from remote, code=${response.code()}")
        }
      }
    })
  }
}
