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
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.`package$`
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.singleaddress.SingleAddressEclairWallet
import fr.acinq.eclair.channel.Channel
import fr.acinq.eclair.channel.ChannelEvent
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.eclair.channel.State
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.BackupEvent
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.payment.PaymentEvent
import fr.acinq.eclair.payment.PaymentLifecycle
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.events.*
import fr.acinq.eclair.phoenix.utils.NetworkException
import fr.acinq.eclair.phoenix.utils.SingleLiveEvent
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import fr.acinq.eclair.router.RouteParams
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.wire.`NodeAddress$`
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import scala.Option
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.math.BigDecimal
import scala.util.Left
import scodec.bits.`ByteVector$`
import java.io.IOException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import scala.collection.immutable.List as ScalaList

data class NodeData(var balance: MilliSatoshi, var activeChannelsCount: Int)
class AppKit(val kit: Kit, val api: Eclair)
enum class StartupState {
  OFF, IN_PROGRESS, DONE, ERROR
}

class AppKitModel : ViewModel() {
  private val log = LoggerFactory.getLogger(AppKitModel::class.java)

  private val timeout = Timeout(Duration.create(5, TimeUnit.SECONDS))
  private val awaitDuration = Duration.create(10, TimeUnit.SECONDS)

  val navigationEvent = SingleLiveEvent<Any>()

  val startupState = MutableLiveData<StartupState>()
  val startupErrorMessage = MutableLiveData<String>()

  val nodeData = MutableLiveData<NodeData>()
  val payments = MutableLiveData<List<Payment>>()

  private val _kit = MutableLiveData<AppKit>()
  val kit: LiveData<AppKit> get() = _kit

  init {
    _kit.value = null
    startupState.value = StartupState.OFF
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

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PayToOpenRequestEvent) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: BalanceEvent) {
    nodeData.value = nodeData.value?.copy(balance = event.amount)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: fr.acinq.eclair.phoenix.events.PaymentEvent) {
    refreshPaymentList()
  }

  @UiThread
  fun isKitReady(): Boolean = kit.value != null

  fun refreshPaymentList() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        _kit.value?.let {
          payments.postValue(JavaConverters.seqAsJavaListConverter(it.kit.nodeParams().db().payments().listPayments(50)).asJava())
        } ?: log.warn("tried to retrieve payment list but appkit is not initialized!!")
      }
    }
  }

  @UiThread
  suspend fun generatePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>): PaymentRequest {
    return coroutineScope {
      async(Dispatchers.Default) {
        _kit.value?.let {
          val hop = PaymentRequest.ExtraHop(Wallet.ACINQ.nodeId(), ShortChannelId.peerId(_kit.value?.kit?.nodeParams()?.nodeId()), 1000, 100, 144)
          val routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>().`$colon$colon`(ScalaList.empty<PaymentRequest.ExtraHop>().`$colon$colon`(hop))

          val preimage = fr.acinq.eclair.`package$`.`MODULE$`.randomBytes32()

          // push preimage to node supervisor actor
          it.kit.system().eventStream().publish(preimage)

          val f = Patterns.ask(_kit.value?.kit?.paymentHandler(), PaymentLifecycle.ReceivePayment(amount_opt, description, Option.apply(null), routes, Option.empty(), Option.apply(preimage)), timeout)
          Await.result(f, awaitDuration) as PaymentRequest
        } ?: throw RuntimeException("kit not initialized")
      }
    }.await()
  }

  @UiThread
  fun sendPaymentRequest(amount: MilliSatoshi, paymentRequest: PaymentRequest, checkFees: Boolean = false) {
    log.info("sending payment request $paymentRequest")
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        _kit.value?.let {
          val finalCltvExpiry = if (paymentRequest.minFinalCltvExpiry().isDefined && paymentRequest.minFinalCltvExpiry().get() is Long) paymentRequest.minFinalCltvExpiry().get() as Long
          else Channel.MIN_CLTV_EXPIRY()

          val routeParams: Option<RouteParams> = if (checkFees) Option.apply(null) // when fee protection is enabled, use the default RouteParams with reasonable values
          else Option.apply(RouteParams.apply( // otherwise, let's build a "no limit" RouteParams
            false, // never randomize on mobile
            `package$`.`MODULE$`.millibtc2millisatoshi(MilliBtc(BigDecimal.exact(1))).amount(), // at most 1mBTC base fee
            1.0, // at most 100%
            4, Router.DEFAULT_ROUTE_MAX_CLTV(), Option.empty()))

          it.kit.paymentInitiator().tell(PaymentLifecycle.SendPayment(amount.amount(),
            paymentRequest.paymentHash(),
            paymentRequest.nodeId(),
            paymentRequest.routingInfo(),
            finalCltvExpiry + 1,
            10,
            routeParams,
            Option.apply(paymentRequest)), ActorRef.noSender())
        } ?: log.warn("tried to send a payment but app kit is not initialized!!")
      }
    }
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
        delay(500)
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

  @UiThread
  suspend fun closeAllChannels(address: String) {
    return coroutineScope {
      async(Dispatchers.Default) {
        delay(500)
        kit.value?.let {
          val closeScriptPubKey = Option.apply(Script.write(fr.acinq.eclair.`package$`.`MODULE$`.addressToPublicKeyScript(address, Wallet.getChainHash())))
          val channels = Await.result(kit.value?.api?.channelsInfo(Option.apply(null), timeout), awaitDuration) as scala.collection.Iterable<RES_GETINFO>
          val closingFutures = ArrayList<Future<String>>()
          JavaConverters.asJavaIterableConverter(channels).asJava().map { res ->
            val channelId = res.channelId()
            log.info("init closing of channel=$channelId")
            it.api.close(Left.apply(channelId), closeScriptPubKey, timeout)?.let { it1 -> closingFutures.add(it1) }
          }

          val r = Await.result(Futures.sequence(closingFutures, it.kit.system().dispatcher()), awaitDuration)
          log.info("closing channels returns: $r")
        } ?: throw RuntimeException("app kit not initialized")
      }
    }.await()
  }

  /**
   * This method launches the node startup process.
   */
  @UiThread
  fun startAppKit(context: Context, pin: String) {
    if (isKitReady()) {
      log.warn("tried to startup node but kit is already setup")
    } else {
      viewModelScope.launch {
        startupState.value = StartupState.IN_PROGRESS
        withContext(Dispatchers.Default) {
          try {
            _kit.postValue(startNode(context, pin))
            startupState.postValue(StartupState.DONE)
            _kit.value?.kit?.switchboard()?.tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ), ActorRef.noSender())
          } catch (t: Throwable) {
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

  fun shutdown() {
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

    log.info("seed successfully read")

    Class.forName("org.sqlite.JDBC")
    // todo init address
    val setup = Setup(Wallet.getDatadir(context), ConfigFactory.empty(), Option.apply(seed), Option.empty(), Option.apply(SingleAddressEclairWallet("2NDSyZrKHJNYtJtMrKtVQvsUwAU8rtAcVmc")), system)
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get())
    log.info("node setup ready")

    val nodeSupervisor = system!!.actorOf(Props.create(EclairSupervisor::class.java), "EclairSupervisor")
    system.eventStream().subscribe(nodeSupervisor, BackupEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, ChannelEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, PaymentEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenRequestEvent::class.java)
    system.eventStream().subscribe(nodeSupervisor, PaymentLifecycle.PaymentResult::class.java)
    system.eventStream().subscribe(nodeSupervisor, ByteVector32::class.java)
    system.eventStream().subscribe(nodeSupervisor, PayToOpenResponse::class.java)

    val kit = Await.result(setup.bootstrap(), Duration.create(60, TimeUnit.SECONDS))
    log.info("bootstrap complete")
    val eclair = EclairImpl(kit)
    return AppKit(kit, eclair)
  }
}
