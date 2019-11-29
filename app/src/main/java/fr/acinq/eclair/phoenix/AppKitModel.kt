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

package fr.acinq.eclair.phoenix

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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.*
import fr.acinq.eclair.*
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.singleaddress.SingleAddressEclairWallet
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.*
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.payment.*
import fr.acinq.eclair.payment.receive.MultiPartHandler
import fr.acinq.eclair.payment.relay.Relayer
import fr.acinq.eclair.payment.send.PaymentInitiator
import fr.acinq.eclair.phoenix.background.ChannelsWatcher
import fr.acinq.eclair.phoenix.events.*
import fr.acinq.eclair.phoenix.events.PayToOpenResponse
import fr.acinq.eclair.phoenix.utils.*
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import fr.acinq.eclair.wire.*
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import scala.Option
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
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

data class NodeData(var balance: MilliSatoshi, var electrumAddress: String? = "")
class AppKit(val kit: Kit, val api: Eclair)
enum class StartupState {
  OFF, IN_PROGRESS, DONE, ERROR
}

class AppKitModel : ViewModel() {
  private val log = LoggerFactory.getLogger(AppKitModel::class.java)

  private val timeout = Timeout(Duration.create(5, TimeUnit.SECONDS))
  private val longTimeout = Timeout(Duration.create(30, TimeUnit.SECONDS))
  private val awaitDuration = Duration.create(10, TimeUnit.SECONDS)
  private val longAwaitDuration = Duration.create(60, TimeUnit.SECONDS)

  val pendingSwapIns = MutableLiveData(HashMap<String, SwapInPending>())
  val payments = MutableLiveData<List<PlainPayment>>()
  val notifications = MutableLiveData(HashSet<InAppNotifications.NotificationTypes>())
  val navigationEvent = SingleLiveEvent<Any>()
  val startupState = MutableLiveData<StartupState>()
  val startupErrorMessage = MutableLiveData<String>()
  val nodeData = MutableLiveData<NodeData>()
  private val _kit = MutableLiveData<AppKit>()
  val kit: LiveData<AppKit> get() = _kit

  init {
    _kit.value = null
    startupState.value = StartupState.OFF
    nodeData.value = NodeData(MilliSatoshi(0), "")
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    checkWalletContext()
  }

  override fun onCleared() {
    EventBus.getDefault().unregister(this)
    shutdown()

    super.onCleared()
    log.info("appkit has been cleared")
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
    kit.value?.run {
      try {
        val pr = doGeneratePaymentRequest("On-chain payment to ${event.bitcoinAddress()}", Option.apply(event.amount()), ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>())
        kit.nodeParams().db().payments().receiveIncomingPayment(pr.paymentHash(), event.amount(), System.currentTimeMillis())
        pendingSwapIns.value?.remove(event.bitcoinAddress())
        navigationEvent.postValue(PaymentReceived(pr.paymentHash(), ScalaList.empty<PaymentReceived.PartialPayment>()))
      } catch (e: Exception) {
        log.error("failed to create and settle payment request placeholder for ${event.bitcoinAddress()}: ", e)
      }
    } ?: log.error("could not create and settle placeholder for on-chain payment because kit is not initialized")
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: BalanceEvent) {
    kit.value?.run {
      nodeData.postValue(nodeData.value?.copy(balance = event.balance))
    } ?: log.info("unhandled balance event with kit not initialized")
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.ElectrumReady) {
    nodeData.value = nodeData.value?.copy(electrumAddress = event.serverAddress().toString())
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.`ElectrumDisconnected$`) {
    nodeData.value = nodeData.value?.copy(electrumAddress = "")
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: ChannelClosingEvent) {
    // store channel closing event as an outgoing payment in database.
    kit.value?.run {
      val id = UUID.randomUUID()
      val preimage = `package$`.`MODULE$`.randomBytes32()
      val paymentHash = Crypto.hash256(preimage.bytes())
      val date = System.currentTimeMillis()
      val paymentCounterpart = OutgoingPayment(
        /* id and parent id */ id, id,
        /* use arbitrary external id to designate payment as channel closing counterpart */ Option.apply("closing-${event.channelId}"),
        /* placeholder payment hash */ paymentHash,
        /* balance */ event.balance,
        /* target node id */ `package$`.`MODULE$`.randomKey().publicKey(), /* creation date */ date,
        /* payment request */ Option.empty(),
        /* payment is always successful */ OutgoingPaymentStatus.`Pending$`.`MODULE$`)
      kit.nodeParams().db().payments().addOutgoingPayment(paymentCounterpart)

      val partialPayment = PaymentSent.PartialPayment(id, event.balance, MilliSatoshi(0), ByteVector32.Zeroes(), Option.empty(), date)
      val paymentCounterpartSent = PaymentSent(id, paymentHash, preimage, ScalaList.empty<PaymentSent.PartialPayment>().`$colon$colon`(partialPayment))
      kit.nodeParams().db().payments().updateOutgoingPayment(paymentCounterpartSent)
    }
  }

  @UiThread
  fun isKitReady(): Boolean = kit.value != null


  fun refreshPayments() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        _kit.value?.let {
          var p: List<PlainPayment>? = ArrayList()
          val t = measureTimeMillis {
            p = JavaConverters.seqAsJavaListConverter(it.kit.nodeParams().db().payments().listPaymentsOverview(50)).asJava()
          }
          log.info("list payments in ${t}ms")
          payments.postValue(p)
        } ?: log.info("kit non initialized, cannot list payments")
      }
    }
  }

  suspend fun getSentPaymentsFromParentId(parentId: UUID): List<OutgoingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.run {
          var payments: List<OutgoingPayment> = ArrayList()
          val t = measureTimeMillis {
            payments = JavaConverters.seqAsJavaListConverter(kit.nodeParams().db().payments().listOutgoingPayments(parentId)).asJava()
          }
          log.info("get sent payment details in ${t}ms")
          payments
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  suspend fun getReceivedPayment(paymentHash: ByteVector32): Option<IncomingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.run {
          var payment: Option<IncomingPayment> = Option.empty()
          val t = measureTimeMillis {
            payment = this.kit.nodeParams().db().payments().getIncomingPayment(paymentHash)
          }
          log.info("get received payment details in ${t}ms")
          payment
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  suspend fun generatePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>): PaymentRequest {
    return coroutineScope {
      async(Dispatchers.Default) {
        val hop = PaymentRequest.ExtraHop(Wallet.ACINQ.nodeId(), ShortChannelId.peerId(_kit.value?.kit?.nodeParams()?.nodeId()), MilliSatoshi(1000), 100, CltvExpiryDelta(144))
        val routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>().`$colon$colon`(ScalaList.empty<PaymentRequest.ExtraHop>().`$colon$colon`(hop))
        doGeneratePaymentRequest(description, amount_opt, routes)
      }
    }.await()
  }

  @WorkerThread
  private fun doGeneratePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>, routes: ScalaList<ScalaList<PaymentRequest.ExtraHop>>): PaymentRequest {
    return _kit.value?.let {
      val f = Patterns.ask(it.kit.paymentHandler(),
        MultiPartHandler.ReceivePayment(
          /* amount */ amount_opt,
          /* description */ description,
          /* expiry seconds */ Option.empty(),
          /* extra routing info */ routes,
          /* fallback onchain address */ Option.empty(),
          /* payment preimage */ Option.empty(),
          /* allow multi part payment */ true), timeout)
      Await.result(f, awaitDuration) as PaymentRequest
    } ?: throw KitNotInitialized()
  }

  suspend fun sendSwapOut(amount: Satoshi, address: String, feeratePerKw: Long) {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.run {
          log.info("sending swap-out request to switchboard for address=$address with amount=$amount")
          this.kit.switchboard().tell(Peer.SendSwapOutRequest(Wallet.ACINQ.nodeId(), amount, address, feeratePerKw), ActorRef.noSender())
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  suspend fun sendSwapIn() {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.run {
          this.kit.switchboard().tell(Peer.SendSwapInRequest(Wallet.ACINQ.nodeId()), ActorRef.noSender())
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  suspend fun sendPaymentRequest(amount: MilliSatoshi, paymentRequest: PaymentRequest, deductFeeFromAmount: Boolean, checkFees: Boolean = false) {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.run {
          val cltvExpiryDelta = if (paymentRequest.minFinalCltvExpiryDelta().isDefined) paymentRequest.minFinalCltvExpiryDelta().get() else Channel.MIN_CLTV_EXPIRY_DELTA()
          val trampolineData = Wallet.getTrampoline(amount, paymentRequest)
          val amountFinal = if (deductFeeFromAmount) amount.`$minus`(trampolineData.second) else amount

          val sendRequest: Any = if (trampolineData.first.isDefined) {
            PaymentInitiator.SendTrampolinePaymentRequest(
              /* amount to send */ amountFinal,
              /* trampoline fees */ trampolineData.second,
              /* payment request */ paymentRequest,
              /* trampoline node public key */ trampolineData.first.get(),
              /* final cltv expiry delta */ cltvExpiryDelta,
              /* trampoline expiry delta, should be very large! */ CltvExpiryDelta(144 * 5),
              /* route params */ Option.apply(null))
          } else {
            PaymentInitiator.SendPaymentRequest(
              /* amount to send */ amountFinal,
              /* paymentHash */ paymentRequest.paymentHash(),
              /* payment target */ paymentRequest.nodeId(),
              /* max attempts */ 3,
              /* final cltv expiry delta */ cltvExpiryDelta,
              /* payment request */ Option.apply(paymentRequest),
              /* external id */ Option.empty(),
              /* predefined route */ ScalaList.empty<Crypto.PublicKey>(),
              /* assisted routes */ paymentRequest.routingInfo(),
              /* route params */ Option.apply(null))
          }

          log.info("sending $amountFinal with trampoline=${trampolineData.first} fee=${trampolineData.second} (deducted: $deductFeeFromAmount) for pr $paymentRequest")
          this.kit.paymentInitiator().tell(sendRequest, ActorRef.noSender())
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  fun acceptPayToOpen(paymentHash: ByteVector32) {
    _kit.value?.kit?.system()?.eventStream()?.publish(AcceptPayToOpen(paymentHash))
  }

  @UiThread
  fun rejectPayToOpen(paymentHash: ByteVector32) {
    _kit.value?.kit?.system()?.eventStream()?.publish(RejectPayToOpen(paymentHash))
  }

  suspend fun getChannels(): Iterable<RES_GETINFO> = getChannels(null)

  /**
   * Retrieves the list of channels from the router. Can filter by state.
   */
  @UiThread
  suspend fun getChannels(state: State?): Iterable<RES_GETINFO> {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit.value?.api?.let {
          val res = Await.result(it.channelsInfo(Option.apply(null), timeout), awaitDuration) as scala.collection.Iterable<RES_GETINFO>
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
        kit.value?.api?.let {
          val res = Await.result(it.channelInfo(Left.apply(channelId), timeout), awaitDuration) as RES_GETINFO
          res
        }
      }
    }.await()
  }

  @UiThread
  suspend fun mutualCloseAllChannels(address: String) {
    return coroutineScope {
      async(Dispatchers.Default) {
        delay(500)
        kit.value?.let {
          val closeScriptPubKey = Option.apply(Script.write(fr.acinq.eclair.`package$`.`MODULE$`.addressToPublicKeyScript(address, Wallet.getChainHash())))
          val closingFutures = ArrayList<Future<String>>()
          getChannels(`NORMAL$`.`MODULE$`).map { res ->
            val channelId = res.channelId()
            log.info("attempting to mutual close channel=$channelId to $closeScriptPubKey")
            closingFutures.add(it.api.close(Left.apply(channelId), closeScriptPubKey, longTimeout))
          }
          Await.result(Futures.sequence(closingFutures, it.kit.system().dispatcher()), longAwaitDuration)
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  @UiThread
  suspend fun forceCloseAllChannels() {
    return coroutineScope {
      async(Dispatchers.Default) {
        kit.value?.let {
          val closingFutures = ArrayList<Future<String>>()
          getChannels().map { res ->
            val channelId = res.channelId()
            log.info("attempting to force close channel=$channelId")
            closingFutures.add(it.api.forceClose(Left.apply(channelId), longTimeout))
          }
          Await.result(Futures.sequence(closingFutures, it.kit.system().dispatcher()), longAwaitDuration)
          Unit
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  /**
   * Send a reconnect event to the ACINQ node.
   */
  @UiThread
  fun reconnect() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        kit.value?.run {
          log.debug("sending reconnect to ACINQ actor")
          kit.system().actorSelection("/system/user/*/switchboard/peer-${Wallet.ACINQ.nodeId()}").tell(Peer.`Reconnect$`.`MODULE$`, ActorRef.noSender())
        } ?: log.info("appkit not ready yet")
      }
    }
  }

  /**
   * This method launches the node startup process.
   */
  @UiThread
  fun startAppKit(context: Context, pin: String) {
    when {
      isKitReady() -> log.info("silently ignoring attempt to start node because kit is already started")
      startupState.value == StartupState.ERROR -> log.info("silently ignoring attempt to start node because startup is in error")
      startupState.value == StartupState.IN_PROGRESS -> log.info("silently ignoring attempt to start node because startup is already in progress")
      startupState.value == StartupState.DONE -> log.info("silently ignoring attempt to start node because startup is done")
      else -> {
        startupState.value = StartupState.IN_PROGRESS
        viewModelScope.launch {
          withContext(Dispatchers.Default) {
            try {
              val res = startNode(context, pin)
              res.kit.switchboard().tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ), ActorRef.noSender())
              _kit.postValue(res)
              ChannelsWatcher.schedule()
              startupState.postValue(StartupState.DONE)
            } catch (t: Throwable) {
              log.info("aborted node startup")
              startupState.postValue(StartupState.ERROR)
              closeConnections()
              _kit.postValue(null)
              when (t) {
                is GeneralSecurityException -> {
                  log.info("user entered wrong PIN")
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_wrong_pwd))
                }
                is NetworkException, is UnknownHostException -> {
                  log.info("network error: ", t)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_network))
                }
                is IOException, is IllegalAccessException -> {
                  log.error("seed file not readable: ", t)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_unreadable))
                }
                else -> {
                  log.error("error when starting node: ", t)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_generic))
                }
              }
            }
          }
        }
      }
    }
  }

  private fun closeConnections() {
    _kit.value?.let {
      it.kit.system().shutdown()
      it.kit.nodeParams().db().audit().close()
      it.kit.nodeParams().db().channels().close()
      it.kit.nodeParams().db().network().close()
      it.kit.nodeParams().db().peers().close()
      it.kit.nodeParams().db().pendingRelay().close()
    } ?: log.warn("could not shutdown system because kit is not initialized!")
  }

  public fun shutdown() {
    closeConnections()
    nodeData.postValue(NodeData(MilliSatoshi(0), ""))
    _kit.postValue(null)
    startupState.postValue(StartupState.OFF)
  }

  @WorkerThread
  private fun cancelBackgroundJobs() {
    // cancel all the jobs scheduled by the work manager that would lock up the eclair DB
  }

  @WorkerThread
  private fun setupApp() {
    // initialize app, clean data, init notification channels...
  }

  @WorkerThread
  private fun checkConnectivity(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (cm.activeNetworkInfo == null || !cm.activeNetworkInfo?.isConnected!!) {
      throw NetworkException()
    }
  }

  @WorkerThread
  private fun startNode(context: Context, pin: String): AppKit {
    log.info("starting up node...")
    // TODO before startup: migration scripts + check datadir + restore backups

    val system = ActorSystem.create("system")
    system.registerOnTermination {
      log.info("system has been shutdown, all actors are terminated")
    }

    checkConnectivity(context)
    setupApp()
    cancelBackgroundJobs()

    val mnemonics = String(Hex.decode(EncryptedSeed.readSeedFile(context, pin)), Charsets.UTF_8)
    log.info("seed successfully read")
    val seed = `ByteVector$`.`MODULE$`.apply(MnemonicCode.toSeed(mnemonics, "").toArray())
    val pk = DeterministicWallet.derivePrivateKey(DeterministicWallet.generate(seed), Wallet.getNodeKeyPath())
    val bech32Address = fr.acinq.bitcoin.`package$`.`MODULE$`.computeBIP84Address(pk.publicKey(), Wallet.getChainHash())

    Class.forName("org.sqlite.JDBC")
    val setup = Setup(Wallet.getDatadir(context), Wallet.getOverrideConfig(context), Option.apply(seed), Option.empty(), Option.apply(SingleAddressEclairWallet(bech32Address)), system)
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get())
    log.info("node setup ready")

    val nodeSupervisor = system!!.actorOf(Props.create(EclairSupervisor::class.java), "EclairSupervisor")
    system.eventStream().subscribe(nodeSupervisor, BackupEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, ChannelEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, Relayer.OutgoingChannels::class.java)
    system.eventStream().subscribe(nodeSupervisor, PaymentEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapOutResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInPending::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInConfirmed::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenRequestEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, ByteVector32::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, ElectrumClient.ElectrumEvent::class.java)

    system.scheduler().schedule(Duration.Zero(), FiniteDuration(10, TimeUnit.MINUTES),
      Runnable { Api.httpClient.newCall(Request.Builder().url(Wallet.PRICE_RATE_API).build()).enqueue(getExchangeRateHandler(context)) }, system.dispatcher())

    val kit = Await.result(setup.bootstrap(), Duration.create(60, TimeUnit.SECONDS))
    log.info("bootstrap complete")
    val eclair = EclairImpl(kit)
    return AppKit(kit, eclair)
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
          val body = response.body()
          if (body != null) {
            try {
              val json = JSONObject(body.string())
              getRateFromJson(context, json, "AUD")
              getRateFromJson(context, json, "BRL")
              getRateFromJson(context, json, "CAD")
              getRateFromJson(context, json, "CHF")
              getRateFromJson(context, json, "CLP")
              getRateFromJson(context, json, "CNY")
              getRateFromJson(context, json, "DKK")
              getRateFromJson(context, json, "EUR")
              getRateFromJson(context, json, "GBP")
              getRateFromJson(context, json, "HKD")
              getRateFromJson(context, json, "INR")
              getRateFromJson(context, json, "ISK")
              getRateFromJson(context, json, "JPY")
              getRateFromJson(context, json, "KRW")
              getRateFromJson(context, json, "NZD")
              getRateFromJson(context, json, "PLN")
              getRateFromJson(context, json, "RUB")
              getRateFromJson(context, json, "SEK")
              getRateFromJson(context, json, "SGD")
              getRateFromJson(context, json, "THB")
              getRateFromJson(context, json, "TWD")
              getRateFromJson(context, json, "USD")
              Prefs.setExchangeRateTimestamp(context, System.currentTimeMillis())
            } catch (t: Throwable) {
              log.error("could not read exchange rates response: ", t)
            } finally {
              body.close()
            }
          } else {
            log.warn("exchange rate body is null")
          }
        }
      }
    }
  }

  fun getRateFromJson(context: Context, json: JSONObject, code: String) {
    var rate = -1.0f
    try {
      rate = json.getJSONObject(code).getDouble("last").toFloat()
    } catch (e: Exception) {
      log.debug("could not read {} from price api response", code)
    }
    Prefs.setExchangeRate(context, code, rate)
  }

  private fun checkWalletContext() {
    Api.httpClient.newCall(Request.Builder().url(Api.WALLET_CONTEXT_URL).build()).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve wallet context from remote: ", e)
      }

      override fun onResponse(call: Call, response: Response) {
        val body = response.body()
        if (response.isSuccessful && body != null) {
          try {
            val json = JSONObject(body.string())
            log.debug("wallet context responded with {}", json.toString(2))
            val installedVersion = BuildConfig.VERSION_CODE
            val latestVersion = json.getJSONObject(BuildConfig.CHAIN).getInt("version")
            val latestCriticalVersion = json.getJSONObject(BuildConfig.CHAIN).getInt("latest_critical_version")
            notifications.value?.run {
              if (installedVersion < latestCriticalVersion) {
                log.info("a critical update (v$latestCriticalVersion) is deemed available")
                add(InAppNotifications.NotificationTypes.UPGRADE_WALLET_CRITICAL)
              } else if (latestVersion - installedVersion >= 2) {
                add(InAppNotifications.NotificationTypes.UPGRADE_WALLET)
              } else {
                remove(InAppNotifications.NotificationTypes.UPGRADE_WALLET_CRITICAL)
                remove(InAppNotifications.NotificationTypes.UPGRADE_WALLET)
              }
              notifications.postValue(this)
            }
          } catch (e: JSONException) {
            log.error("could not read wallet context body: ", e)
          }
        } else {
          log.warn("could not retrieve wallet context from remote, code=${response.code()}")
        }
      }
    })
  }
}
