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
import androidx.navigation.fragment.findNavController
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.singleaddress.SingleAddressEclairWallet
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.BackupEvent
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.payment.PaymentEvent
import fr.acinq.eclair.payment.PaymentInitiator
import fr.acinq.eclair.payment.PaymentLifecycle
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.events.*
import fr.acinq.eclair.phoenix.utils.*
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import fr.acinq.eclair.wire.SwapInResponse
import fr.acinq.eclair.wire.`NodeAddress$`
import kotlinx.coroutines.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
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

data class NodeData(var balance: MilliSatoshi, var activeChannelsCount: Int)
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

  val navigationEvent = SingleLiveEvent<Any>()

  val startupState = MutableLiveData(StartupState.OFF)
  val startupErrorMessage = MutableLiveData<String>()

  val nodeData = MutableLiveData<NodeData>()


  private val _kit = MutableLiveData<AppKit>()
  val kit: LiveData<AppKit> get() = _kit

  val httpClient = OkHttpClient()

  init {
    _kit.value = null
    nodeData.value = NodeData(MilliSatoshi(0), 0)
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
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
  fun handleEvent(event: PaymentComplete) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PayToOpenRequestEvent) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: BalanceEvent) {
    nodeData.value = nodeData.value?.copy(balance = event.available)
  }

  @UiThread
  fun isKitReady(): Boolean = kit.value != null

  suspend fun listPayments(): List<Payment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.let {
          val list = mutableListOf<Payment>()
          val t = measureTimeMillis {
//            val closed = JavaConverters.seqAsJavaListConverter(it.kit.nodeParams().db().channels().listClosedLocalChannels()).asJava()
//            closed.forEach { hc ->
//              if (hc is DATA_CLOSING) {
//                val txs = JavaConverters.seqAsJavaListConverter(hc.mutualClosePublished()).asJava()
//                txs.forEach { tx ->
//                  val txsOut = JavaConverters.seqAsJavaListConverter(tx.txOut()).asJava()
//                  txsOut.forEach { txOut ->
//                    list.add(ClosingPayment(tx.txid().toString(), txOut.amount(), System.currentTimeMillis()))
//                  }
//                }
//              } else {
//                log.info("closed channel is not DATA_CLOSING type")
//              }
//            }
            list.addAll(JavaConverters.seqAsJavaListConverter(it.kit.nodeParams().db().payments().listPayments(50)).asJava())
          }
          log.info("payment list query complete in ${t}ms")
          list
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  suspend fun getSentPayment(id: UUID): Option<OutgoingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        var payment: Option<OutgoingPayment> = Option.empty()
        val t = measureTimeMillis {
         payment = _kit.value?.kit?.nodeParams()?.db()?.payments()?.getOutgoingPayment(id) ?: throw KitNotInitialized()
        }
        log.info("get sent payment complete in ${t}ms")
        payment
      }
    }.await()
  }

  suspend fun getReceivedPayment(paymentHash: ByteVector32): Option<IncomingPayment> {
    return coroutineScope {
      async(Dispatchers.Default) {
        var payment: Option<IncomingPayment> = Option.empty()
        val t = measureTimeMillis {
          payment = _kit.value?.kit?.nodeParams()?.db()?.payments()?.getIncomingPayment(paymentHash) ?: throw KitNotInitialized()
        }
        log.info("get received payment complete in ${t}ms")
        payment
      }
    }.await()
  }

  @UiThread
  suspend fun generatePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>): PaymentRequest {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.let {
          val hop = PaymentRequest.ExtraHop(Wallet.ACINQ.nodeId(), ShortChannelId.peerId(_kit.value?.kit?.nodeParams()?.nodeId()), MilliSatoshi(1000), 100, CltvExpiryDelta(144))
          val routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>().`$colon$colon`(ScalaList.empty<PaymentRequest.ExtraHop>().`$colon$colon`(hop))
          val f = Patterns.ask(it.kit.paymentHandler(),
            PaymentLifecycle.ReceivePayment(
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
    }.await()
  }

  @UiThread
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


          val routingHeadShortChannelId = if (paymentRequest.routingInfo().headOption().isDefined && paymentRequest.routingInfo().head().headOption().isDefined)
            Option.apply(paymentRequest.routingInfo().head().head().shortChannelId()) else Option.empty()

          val trampolineNode: Option<Crypto.PublicKey> = when {
            // payment target is ACINQ: no trampoline
            paymentRequest.nodeId() == Wallet.ACINQ.nodeId() -> Option.empty()
            // routing info head is a peer id: target is a phoenix app
            routingHeadShortChannelId.isDefined && ShortChannelId.isPeerId(routingHeadShortChannelId.get()) -> Option.empty()
            // otherwise, we use ACINQ node for trampoline
            else -> Option.apply(Wallet.ACINQ.nodeId())
          }

          val trampolineFee: MilliSatoshi = if (trampolineNode.isDefined) {
            Converter.any2Msat(Satoshi(5)).`$times`(5) // base fee covering 5 hops with a base fee of 5 sat
              .`$plus`(amount.`$times`(0.001))  // + proportional fee = 0.1 % of payment amount
          } else {
            MilliSatoshi(0) // no fee when we are not using trampoline
          }

          val amountFinal = if (deductFeeFromAmount) amount.`$minus`(trampolineFee) else amount

          val sendRequest = PaymentInitiator.SendPaymentRequest(
            /* amount to send */ if (deductFeeFromAmount) amount.`$minus`(trampolineFee) else amount,
            /* paymentHash */ paymentRequest.paymentHash(),
            /* payment target */ paymentRequest.nodeId(),
            /* max attempts */ 1,
            /* final cltv expiry delta */ cltvExpiryDelta,
            /* payment request */ Option.apply(paymentRequest),
            /* external id */ Option.empty(),
            /* predefined route */ ScalaList.empty<Crypto.PublicKey>(),
            /* assisted routes */ paymentRequest.routingInfo(),
            /* route params */ Option.apply(null),
            /* trampoline node public key */ trampolineNode,
            /* trampoline fees */ trampolineFee,
            /* trampoline expiry delta, should be very large! */ CltvExpiryDelta(144 * 5))

          log.info("sending $amountFinal with fee=$trampolineFee (deducted: $deductFeeFromAmount) for pr $paymentRequest")
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
  suspend fun closeAllChannels(address: String) {
    return coroutineScope {
      async(Dispatchers.Default) {
        delay(500)
        kit.value?.let {
          val closeScriptPubKey = Option.apply(Script.write(fr.acinq.eclair.`package$`.`MODULE$`.addressToPublicKeyScript(address, Wallet.getChainHash())))
          val closingFutures = ArrayList<Future<String>>()

          getChannels(`NORMAL$`.`MODULE$`).map { res ->
            val channelId = res.channelId()
            log.info("init closing of channel=$channelId")
            closingFutures.add(it.api.close(Left.apply(channelId), closeScriptPubKey, longTimeout))
          }

          val r = Await.result(Futures.sequence(closingFutures, it.kit.system().dispatcher()), longAwaitDuration)
          log.info("closing channels returns: $r")
        } ?: throw KitNotInitialized()
      }
    }.await()
  }

  /**
   * This method launches the node startup process.
   */
  @UiThread
  fun startAppKit(context: Context, pin: String) {
    when {
      isKitReady() -> {
        log.warn("ignoring attempt to start node because kit is already setup")
      }
      startupState.value == StartupState.IN_PROGRESS -> {
        log.info("ignoring attempt to start node because startup is already in progress")
      }
      else -> {
        startupState.value = StartupState.IN_PROGRESS
        viewModelScope.launch {
          withContext(Dispatchers.Default) {
            try {
              val res = startNode(context, pin)
              res.kit.switchboard().tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ), ActorRef.noSender())
              _kit.postValue(res)
              startupState.postValue(StartupState.DONE)
            } catch (t: Throwable) {
              shutdown()
              log.info("aborted node startup")
              startupState.postValue(StartupState.ERROR)
              _kit.postValue(null)
              when (t) {
                is GeneralSecurityException -> {
                  log.info("user entered wrong PIN")
                  startupState.postValue(StartupState.ERROR)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_wrong_pwd))
                }
                is NetworkException, is UnknownHostException -> {
                  log.info("network error: ", t)
                  startupState.postValue(StartupState.ERROR)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_network))
                }
                is IOException, is IllegalAccessException -> {
                  log.error("seed file not readable: ", t)
                  startupState.postValue(StartupState.ERROR)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_unreadable))
                }
                else -> {
                  log.error("error when starting node: ", t)
                  startupState.postValue(StartupState.ERROR)
                  startupErrorMessage.postValue(context.getString(R.string.startup_error_generic))
                }
              }
            }
          }
        }
      }
    }
  }


  private fun shutdown() {
    _kit.value?.let {
      it.kit.system().shutdown()
      it.kit.nodeParams().db().audit().close()
      it.kit.nodeParams().db().channels().close()
      it.kit.nodeParams().db().network().close()
      it.kit.nodeParams().db().peers().close()
      it.kit.nodeParams().db().pendingRelay().close()
    } ?: log.warn("could not close db connection and shutdown system because kit is not initialized!")
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
    if (!cm.activeNetworkInfo?.isConnected!!) {
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
      _kit.postValue(null)
    }

    checkConnectivity(context)
    setupApp()
    cancelBackgroundJobs()

    val mnemonics = String(Hex.decode(EncryptedSeed.readSeedFile(context, pin)), Charsets.UTF_8).split(" ")
    val seed = `ByteVector$`.`MODULE$`.apply(MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mnemonics).asScala().toSeq(), "").toArray())
    val pk = DeterministicWallet.derivePrivateKey(DeterministicWallet.generate(seed), LocalKeyManager.nodeKeyBasePath(Wallet.getChainHash()))
    val hashOfSeed = pk.privateKey().publicKey().hash160().toHex()

    // val walletBech32 = SingleAddressEclairWallet(fr.acinq.bitcoin.`package$`.`MODULE$`.computeBIP84Address(pk.publicKey(), Wallet.getChainHash()))
    // TODO: use bech32 wallet (not working for now
    val walletBip49 = SingleAddressEclairWallet(fr.acinq.bitcoin.`package$`.`MODULE$`.computeBIP49Address(pk.publicKey(), Wallet.getChainHash()))

    log.info("seed successfully read")

    Class.forName("org.sqlite.JDBC")
    // todo init address
    val setup = Setup(Wallet.getDatadir(context), ConfigFactory.empty(), Option.apply(seed), Option.empty(), Option.apply(walletBip49), system)
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get())
    log.info("node setup ready")

    val nodeSupervisor = system!!.actorOf(Props.create(EclairSupervisor::class.java), "EclairSupervisor")
    system.eventStream().subscribe(nodeSupervisor, BackupEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, ChannelEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, PaymentEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, SwapInResponse::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenRequestEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, ByteVector32::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenResponse::class.java)

    system.scheduler().schedule(Duration.Zero(), FiniteDuration(10, TimeUnit.MINUTES),
      Runnable { httpClient.newCall(Request.Builder().url(Wallet.PRICE_RATE_API).build()).enqueue(getExchangeRateHandler(context)) }, system.dispatcher())

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
}
