/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.background

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.Futures
import akka.pattern.Patterns
import akka.util.Timeout
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Binder
import android.os.IBinder
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
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
import fr.acinq.phoenix.*
import fr.acinq.phoenix.events.*
import fr.acinq.phoenix.events.PayToOpenResponse
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.seed.EncryptedSeed
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
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
import scala.util.Either
import scala.util.Left
import scodec.bits.`ByteVector$`
import java.io.IOException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import scala.collection.immutable.List as ScalaList

/**
 * This service starts, runs, and stops the node. It maintains a [state] in an observable LiveData object, that
 * the GUI can watch and adapt to.
 *
 * This service is both `started` and `bound`. It can run headless, without a GUI but with a foreground
 * notification ; if there's a GUI binding to the service, then the notification is removed.
 *
 * This service also listens to some EventBus events.
 *
 * Note that this service can survive the GUI being killed. Still, it can be killed using the [shutdown] method
 * whenever the GUI stops.
 */
class EclairNodeService : Service() {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private lateinit var notificationManager: NotificationManagerCompat
  private val notificationBuilder = NotificationCompat.Builder(this, Constants.FCM_NOTIFICATION_CHANNEL_ID)
  private val binder = NodeBinder()

  /** True if the service is running headless (that is without a GUI) and as such should show a notification. */
  @Volatile
  private var isHeadless = false

  private lateinit var appContext: AppContext
  private val stateLock = ReentrantLock()
  /** State of the service, provides access to the kit when it's started. Private so that it's not mutated from the outside. */
  private val _state = MutableLiveData<KitState>(KitState.Off)
  /** Public observable state that can be used by the UI */
  val state: LiveData<KitState> get() = _state
  /** Shorthands methods to get the kit/api, if available */
  private val kit: Kit? get() = state.value?.kit()
  private val api: Eclair? get() = state.value?.api()

  // ============================================================== //
  //                  SERVICE BASE METHODS OVERRIDE                 //
  // ============================================================== //

  override fun onCreate() {
    super.onCreate()
    log.info("creating node service")
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    appContext = AppContext.getInstance(applicationContext)
    notificationManager = NotificationManagerCompat.from(this)
    val intent = Intent(this, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    notificationBuilder.setSmallIcon(R.drawable.ic_phoenix)
      .setAutoCancel(true)
      .setContentTitle(getString(R.string.notif_fcm_title))
      .setContentIntent(PendingIntent.getActivity(this, Constants.FCM_NOTIFICATION_ID, intent, PendingIntent.FLAG_ONE_SHOT))
    log.info("end of service creation")
  }

  override fun onBind(intent: Intent?): IBinder? {
    log.info("binding node service from intent=$intent")
    if (appContext.isAppVisible) {
      isHeadless = false
    }
    stopForeground(true)
    return binder
  }

  /** When unbound, the service is running headless */
  override fun onUnbind(intent: Intent?): Boolean {
    if (!appContext.isAppVisible) {
      isHeadless = true
    }
    return false
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    log.info("on start command! [ intent=$intent, flag=$flags, startId=$startId ]")
    if (!appContext.isAppVisible) {
      isHeadless = true
    }
    // Start the kit with an empty password. If the seed is actually encrypted, this attempt will fail.
    startKit(null)
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    EventBus.getDefault().unregister(this)
  }

  // ======================================================================= //
  //                  METHODS TO START/STOP NODE AND SERVICE                 //
  // ======================================================================= //

  /** Close database connections opened by the node */
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

  /** Reset the app state to Off - only if it was in error. Does nothing otherwise. */
  fun resetToOff() {
    if (_state.value is KitState.Error) {
      updateState(KitState.Off)
    }
  }

  /** Shutdown the node, close connections and stop the service */
  fun shutdown() {
    closeConnections()
    if (isHeadless) {
      log.info("shutting down service in state=${state.value}")
      stopForeground(true)
      isHeadless = false
    }
    stopSelf()
    updateState(KitState.Off)
  }

  /**
   * This method launches the node startup process. A PIN code can be provided to decrypt the seed. The application
   * state will be updated to reflect the various stages of the node startup.
   *
   * If the service is headless this method will check if the seed is expected to be encrypted to prevent a hard
   * error, and fail gracefully with a notification.
   *
   * If the kit is already starting, started, or failed to start, this method will return early and not do anything.
   *
   * @param pin The PIN code encrypting the seed. Null if the seed is not encrypted.
   */
  @UiThread
  fun startKit(pin: String?) {
    // Check app state consistency. Use a lock because the [startKit] method can be called concurrently.
    // If the kit is already starting, started, or in error, the method returns.
    try {
      stateLock.lock()
      val state = _state.value
      if (state !is KitState.Off) {
        log.warn("ignore attempt to start kit with app state=${state}")
        return
      } else {
        updateState(KitState.Bootstrap.Init, lazy = false)
      }
    } catch (e: Exception) {
      log.error("error in state check when starting kit: ", e)
      return
    } finally {
      stateLock.unlock()
    }

    // Seed is encrypted but no pin is provided. Alert user if service is headless.
    if (pin == null && Prefs.isSeedEncrypted(applicationContext)) {
      notifyIncomingPaymentWhenLocked()
      return
    }

    // Startup can safely start
    log.info("initiating node startup from state=${_state.value}")
    serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.warn("aborted node startup with error=${e.javaClass.simpleName}!")
      closeConnections()
      when (e) {
        is GeneralSecurityException -> {
          log.debug("user entered wrong PIN")
          updateState(KitState.Error.WrongPassword)
        }
        is NetworkException, is UnknownHostException -> {
          log.info("network error: ", e)
          updateState(KitState.Error.NoConnectivity)
        }
        is IOException, is IllegalAccessException -> {
          log.error("seed file not readable: ", e)
          updateState(KitState.Error.UnreadableData)
        }
        is InvalidElectrumAddress -> {
          log.error("cannot start with invalid electrum address: ", e)
          updateState(KitState.Error.InvalidElectrumAddress(e.address))
        }
        is TorSetupException -> {
          log.error("error when bootstrapping TOR: ", e)
          updateState(KitState.Error.Tor(e.localizedMessage ?: e.javaClass.simpleName))
        }
        else -> {
          log.error("error when starting node: ", e)
          updateState(KitState.Error.Generic(e.localizedMessage ?: e.javaClass.simpleName))
        }
      }
    }) {
      Migration.doMigration(applicationContext)
      val (_kit, xpub) = doStartNode(applicationContext, pin)
      updateState(KitState.Started(_kit, xpub))
      kit?.switchboard()?.tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ), ActorRef.noSender())
      ChannelsWatcher.schedule(applicationContext)
    }
  }

  /** Stop all background jobs that would be locking the eclair database. */
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
  private fun doStartNode(context: Context, pin: String?): Pair<Kit, Xpub> {
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
      updateState(KitState.Bootstrap.Tor)
      appContext.startTor()
      log.info("TOR has been bootstrapped")
    } else {
      log.info("using clear connection...")
    }

    updateState(KitState.Bootstrap.Node)
    val mnemonics = String(Hex.decode(EncryptedSeed.readSeedFromDir(Wallet.getDatadir(context), pin)), Charsets.UTF_8)
    log.info("seed successfully read")
    val seed = `ByteVector$`.`MODULE$`.apply(MnemonicCode.toSeed(mnemonics, "").toArray())
    val master = DeterministicWallet.generate(seed)

    val address = Wallet.buildAddress(master)
    val xpub = Wallet.buildXpub(master)

    Class.forName("org.sqlite.JDBC")
    val acinqNodeAddress = `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get()
    val setup = Setup(Wallet.getDatadir(context), Option.apply(seed), Option.empty(), Option.apply(address), system)
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), acinqNodeAddress)
    log.info("node setup ready, running version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

    val nodeSupervisor = system!!.actorOf(Props.create { EclairSupervisor() }, "EclairSupervisor")
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

    val kit = Await.result(setup.bootstrap(), Duration.create(60, TimeUnit.SECONDS))
    log.info("bootstrap complete")
    return Pair(kit, xpub)
  }

  // ================================================================================= //
  //                    BELOW ARE METHODS TO INTERACT WITH THE NODE                    //
  //                            SUSPENDED WHEN SYNCHRONOUS.                            //
  //                          ALWAYS CALLABLE FROM UI THREAD.                          //
  // ================================================================================= //

  /** Default timeout for awaiting akka futures' completion */
  private val shortTimeout = Timeout(Duration.create(10, TimeUnit.SECONDS))

  /** Longer timeout in some cases where the futures could take a long time to complete. */
  private val longTimeout = Timeout(Duration.create(30, TimeUnit.SECONDS))

  private suspend inline fun <T, R> T.askKit(crossinline block: T.() -> R): R = withContext(serviceScope.coroutineContext) {
    kit?.run {
      block()
    } ?: throw KitNotInitialized
  }

  /** Retrieves the list of channels from the router. Can filter by state. */
  @UiThread
  suspend fun getChannels(channelState: State? = null): Iterable<RES_GETINFO> {
    return withContext(serviceScope.coroutineContext + Dispatchers.IO) {
      api?.run {
        val res = Await.result(channelsInfo(Option.apply(null), shortTimeout), Duration.Inf()) as scala.collection.Iterable<RES_GETINFO>
        val channels = JavaConverters.asJavaIterableConverter(res).asJava()
        channelState?.let {
          channels.filter { c -> c.state() == channelState }
        } ?: channels
      } ?: emptyList()
    }
  }

  /** Retrieves a channel from the router, using its long channel Id. */
  @UiThread
  suspend fun getChannel(channelId: ByteVector32): RES_GETINFO? {
    return withContext(serviceScope.coroutineContext + Dispatchers.IO) {
      api?.run { Await.result(channelInfo(Left.apply(channelId), shortTimeout), Duration.Inf()) as RES_GETINFO }
    }
  }

  /** Mutual close all channels. Will throw if one channel closing does not work correctly. */
  @UiThread
  suspend fun mutualCloseAllChannels(address: String) = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    if (api != null && kit != null) {
      delay(500)
      val closeScriptPubKey = Option.apply(Script.write(`package$`.`MODULE$`.addressToPublicKeyScript(address, Wallet.getChainHash())))
      val channelIds = prepareClosing()
      log.info("requesting to *force* close channels=$channelIds")
      val closingResult = Await.result(api!!.close(channelIds, closeScriptPubKey, longTimeout), Duration.Inf())
      val successfullyClosed = handleClosingResult(closingResult)
      if (successfullyClosed == channelIds.size()) {
        Unit
      } else {
        throw ChannelsNotClosed(channelIds.size() - successfullyClosed)
      }
    } else throw KitNotInitialized
  }

  /** Unilaterally close all channels. Will throw if one channel closing does not work correctly. */
  @UiThread
  suspend fun forceCloseAllChannels() = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
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
    } else throw KitNotInitialized
  }

  /** Create a list of ids of channels to be closed. */
  @WorkerThread
  private suspend fun prepareClosing(): ScalaList<Either<ByteVector32, ShortChannelId>> {
    val channelIds = ScalaList.empty<Either<ByteVector32, ShortChannelId>>()
    getChannels().forEach {
      val id: Either<ByteVector32, ShortChannelId> = Left.apply(it.channelId())
      channelIds.`$colon$colon`(id)
    }
    return channelIds
  }

  /** Handle the response of a closing request from the API. */
  @WorkerThread
  private fun handleClosingResult(result: scala.collection.immutable.Map<Either<ByteVector32, ShortChannelId>, Either<Throwable, ChannelCommandResponse>>): Int {
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

  /** Accept a pay-to-open request for a given payment hash. */
  @UiThread
  fun acceptPayToOpen(paymentHash: ByteVector32) {
    kit?.system()?.eventStream()?.publish(AcceptPayToOpen(paymentHash))
  }

  /** Reject a pay-to-open request for a given payment hash. */
  @UiThread
  fun rejectPayToOpen(paymentHash: ByteVector32) {
    kit?.system()?.eventStream()?.publish(RejectPayToOpen(paymentHash))
  }

  /** Extracts the worst case (fee, ctlv expiry delta) scenario from the routing hints in a payment request. If the payment request has no routing hints, return (0, 0). */
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
  suspend fun sendPaymentRequest(amount: MilliSatoshi, paymentRequest: PaymentRequest, subtractFee: Boolean) = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    if (isHeadless) {
      throw CannotSendHeadless
    }
    kit?.run {
      val cltvExpiryDelta = if (paymentRequest.minFinalCltvExpiryDelta().isDefined) paymentRequest.minFinalCltvExpiryDelta().get() else Channel.MIN_CLTV_EXPIRY_DELTA()
      val isTrampoline = paymentRequest.nodeId() != Wallet.ACINQ.nodeId()

      val sendRequest: Any = if (isTrampoline) {
        // 1 - compute trampoline fee settings for this payment
        // note that if we have to subtract the fee from the amount, use ONLY the most expensive trampoline setting option, to make sure that the payment will go through
        val trampolineFeeSettings = appContext.trampolineFeeSettings.value ?: throw RuntimeException("missing trampoline fee settings")
        val feeSettingsDefault = if (subtractFee) listOf(trampolineFeeSettings.last()) else trampolineFeeSettings
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
      log.info("payment initiator has accepted request and returned $res")
      if (res is PaymentFailed) {
        val failure = res.failures().mkString(", ")
        log.error("payment has failed: [ $failure ])")
        throw RuntimeException("Payment failure: $failure")
      }

      Unit
    } ?: throw KitNotInitialized
  }

  /** Request swap-out details. If no feerate is provided, default to a 6 blocks target using the node fee estimator. */
  suspend fun requestSwapOut(amount: Satoshi, address: String, feeratePerKw: Long? = null) = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      log.info("sending swap-out request to switchboard for address=$address with amount=$amount")
      switchboard().tell(Peer.SendSwapOutRequest(Wallet.ACINQ.nodeId(), amount, address,
        feeratePerKw ?: nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(6)
      ), ActorRef.noSender())
      Unit
    } ?: throw KitNotInitialized
  }

  /** Request swap-out details */
  suspend fun sendSwapIn() = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      switchboard().tell(Peer.SendSwapInRequest(Wallet.ACINQ.nodeId()), ActorRef.noSender())
      Unit
    } ?: throw KitNotInitialized
  }

  /** Generate a BOLT 11 payment request */
  @UiThread
  suspend fun generatePaymentRequest(description: String, amount_opt: Option<MilliSatoshi>): PaymentRequest = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      val hop = PaymentRequest.ExtraHop(Wallet.ACINQ.nodeId(), ShortChannelId.peerId(nodeParams().nodeId()), MilliSatoshi(1000), 100, CltvExpiryDelta(144))
      val routes = ScalaList.empty<List<PaymentRequest.ExtraHop>>().`$colon$colon`(ScalaList.empty<PaymentRequest.ExtraHop>().`$colon$colon`(hop))
      doGeneratePaymentRequest(description, amount_opt, routes, paymentType = PaymentType.Standard())
    } ?: throw KitNotInitialized
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
    } ?: throw KitNotInitialized
  }

  suspend fun getSentPaymentsFromParentId(parentId: UUID): List<OutgoingPayment> = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      JavaConverters.seqAsJavaListConverter(nodeParams().db().payments().listOutgoingPayments(parentId)).asJava()
    } ?: throw KitNotInitialized
  }

  suspend fun getReceivedPayment(paymentHash: ByteVector32): Option<IncomingPayment> = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      nodeParams().db().payments().getIncomingPayment(paymentHash)
    } ?: throw KitNotInitialized
  }

  suspend fun getPayments(): List<PlainPayment> = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.let {
      JavaConverters.seqAsJavaListConverter(it.nodeParams().db().payments().listPaymentsOverview(50)).asJava()
    } ?: throw KitNotInitialized
  }

  // ================================================================== //
  //                  METHODS TO DEAL WITH CONNECTIONS                  //
  // ================================================================== //

  /** Send a reconnect event to the ACINQ node. */
  @UiThread
  fun reconnectToPeer() {
    serviceScope.launch(Dispatchers.Default) {
      kit?.run {
        log.info("forcing reconnection to peer")
        switchboard().tell(Peer.Connect(Wallet.ACINQ.nodeId(), Option.apply(Wallet.ACINQ.address())), ActorRef.noSender())
      }
    }
  }

  // =========================================================== //
  //                  METHODS HANDLING UI EVENTS                 //
  // =========================================================== //

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
        EventBus.getDefault().post(RemovePendingSwapIn(event.bitcoinAddress()))
        EventBus.getDefault().post(PaymentReceived(pr.paymentHash(),
          ScalaList.empty<PaymentReceived.PartialPayment>().`$colon$colon`(PaymentReceived.PartialPayment(event.amount(), ByteVector32.Zeroes(), System.currentTimeMillis()))))
      } catch (e: Exception) {
        log.error("failed to create and settle payment request placeholder for ${event.bitcoinAddress()}: ", e)
      }
    } ?: log.error("could not create and settle placeholder for on-chain payment because kit is not initialized")
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: Peer.SendFCMToken) {
    kit?.run {
      log.info("registering token=${event.token()} with node=${event.nodeId()}")
      switchboard().tell(event, ActorRef.noSender())
    } ?: log.warn("could not register fcm token because kit is not ready yet")
  }

  // =========================================================== //
  //                 UPDATE STATE AND NOTIFY USER                //
  // =========================================================== //

  /**
   * Update the app mutable [_state] and show a notification if the service is headless.
   * @param s The new state of the app.
   * @param lazy `true` to update with postValue, `false` to commit the state directly. If not lazy, this method MUST be called from the main thread!
   */
  @Synchronized
  private fun updateState(s: KitState, lazy: Boolean = true) {
    if (_state.value != s) {
      if (lazy) {
        _state.postValue(s)
      } else {
        _state.value = s
      }
      if (isHeadless) {
        when (s) {
          is KitState.Bootstrap -> notifyForegroundService(getString(R.string.notif_fcm_message_starting_up))
          is KitState.Started -> notifyForegroundService(getString(R.string.notif_fcm_message_receive_in_progress))
          is KitState.Error -> {
            log.warn("failure ${s.getName()} in startup while service is headless! Shutting down.")
            shutdown()
          }
        }
      }
    }
  }

  /** Display a blocking notification and set the service as being foregrounded. */
  private fun notifyForegroundService(message: String) {
    notificationBuilder.setContentText(message)
    val notification = notificationBuilder.build()
    notificationManager.notify(Constants.FCM_NOTIFICATION_ID, notification)
    startForeground(Constants.FCM_NOTIFICATION_ID, notification)
  }

  /** Notify the user that a payment is incoming, but the node cannot start because the seed is encrypted. Manual action is required first. */
  private fun notifyIncomingPaymentWhenLocked() {
    if (isHeadless) {
      notificationBuilder.setContentText(getString(R.string.notif_fcm_message_manual_start_app))
      notificationManager.notify(Constants.FCM_NOTIFICATION_ID, notificationBuilder.build())
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: PaymentReceived) {
    if (isHeadless) {
      notificationBuilder.setContentText(getString(R.string.notif_fcm_message_received))
      notificationManager.notify(Constants.FCM_NOTIFICATION_ID, notificationBuilder.build())
    }
  }

  inner class NodeBinder : Binder() {
    fun getService(): EclairNodeService = this@EclairNodeService
  }
}

/**
 * 4 possible states:
 * - idle, waiting for the node to be started
 * - the node is starting
 * - the node is started
 * - the node failed to start.
 * */
sealed class KitState {

  /** Default state, the node is not started. */
  object Off : KitState()

  /** This is an utility state for clients who cannot reach the service but keep watch of the state. Should not be used internally. */
  object Disconnected : KitState()

  /** This is a transition state. The node is starting up and will soon either go to Started, or to Error. */
  sealed class Bootstrap : KitState() {
    object Init : Bootstrap()
    object Tor : Bootstrap()
    object Node : Bootstrap()
  }

  /** The node is started and we should be able to access the kit/api. */
  data class Started(internal val kit: Kit, internal val xpub: Xpub) : KitState() {
    internal val _api: Eclair by lazy {
      EclairImpl(kit)
    }
  }

  /** Startup has failed, the state contains the error details. */
  sealed class Error : KitState() {
    data class Generic(val message: String) : Error()
    data class Tor(val message: String) : Error()
    data class InvalidElectrumAddress(val address: String) : Error()
    object InvalidBiometric : Error()
    object WrongPassword : Error()
    object NoConnectivity : Error()
    object UnreadableData : Error()
  }

  /** Get a human readable state name */
  fun getName(): String = this.javaClass.simpleName

  /** Get the node's wallet master public key */
  fun getXpub(): Xpub? = if (this is Started) { xpub } else null

  /** Get node public key */
  fun getNodeId(): Crypto.PublicKey? = if (this is Started) { kit.nodeParams().nodeId() } else null

  /** Get node final address */
  fun getFinalAddress(): String? = if (this is Started) {
    try {
      kit.wallet().receiveAddress.value().get().get()
    } catch (e: Exception) {
      null
    }
  } else null

  /** Get node's current feerate per Kw */
  fun getFeeratePerKw(target: Int): Long? = kit()?.run {
    nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(target)
  }

  fun kit(): Kit? = if (this is Started) kit else null
  fun api(): Eclair? = if (this is Started) _api else null
}
