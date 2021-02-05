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
import akka.pattern.Patterns
import akka.util.Timeout
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.text.format.DateUtils
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.msopentech.thali.toronionproxy.OnionProxyManager
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.eclair.*
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.db.*
import fr.acinq.eclair.io.*
import fr.acinq.eclair.payment.*
import fr.acinq.eclair.payment.receive.MultiPartHandler
import fr.acinq.eclair.payment.relay.Relayer
import fr.acinq.eclair.payment.send.PaymentInitiator
import fr.acinq.eclair.wire.*
import fr.acinq.phoenix.*
import fr.acinq.phoenix.db.AppDb
import fr.acinq.phoenix.db.PayToOpenMetaRepository
import fr.acinq.phoenix.db.PaymentMetaRepository
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.crypto.SeedManager
import fr.acinq.phoenix.utils.tor.TorConnectionStatus
import fr.acinq.phoenix.utils.tor.TorEventHandler
import fr.acinq.phoenix.utils.tor.TorHelper
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import scala.Option
import scala.Tuple2
import scala.collection.JavaConverters
import scala.collection.immutable.Seq
import scala.collection.immutable.`Seq$`
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Either
import scala.util.Left
import scodec.bits.ByteVector
import java.io.IOException
import java.lang.Runnable
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
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

  companion object {
    const val EXTRA_REASON = "${BuildConfig.APPLICATION_ID}.SERVICE_SPAWN_REASON"
  }

  private val log = LoggerFactory.getLogger(this::class.java)
  private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private lateinit var notificationManager: NotificationManagerCompat
  private val notificationBuilder = NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID__HEADLESS)
  private val binder = NodeBinder()
  private lateinit var appContext: AppContext
  private var spawnReason: String? = null

  /** True if the service is running headless (that is without a GUI) and as such should show a notification. */
  @Volatile
  private var isHeadless = true
  private val receivedInBackground: MutableLiveData<List<MilliSatoshi>> = MutableLiveData(emptyList())

  // repositories for db access
  private lateinit var appDb: Database
  private lateinit var paymentMetaRepository: PaymentMetaRepository
  private lateinit var payToOpenMetaRepository: PayToOpenMetaRepository

  /** State of the service, provides access to the kit when it's started. Private so that it's not mutated from the outside. */
  private val _state = MutableLiveData<KitState>(KitState.Off)

  /** Public observable state that can be used by the UI */
  val state: LiveData<KitState> get() = _state

  /** Lock for state updates */
  private val stateLock = ReentrantLock()

  /** Shorthands methods to get the kit/api, if available */
  private val kit: Kit? get() = state.value?.kit()
  private val api: Eclair? get() = state.value?.api()

  /** State of network connections (Internet, Tor, Peer, Electrum). */
  val electrumConn = MutableLiveData(Constants.DEFAULT_NETWORK_INFO.electrumServer)
  val torConn = MutableLiveData(Constants.DEFAULT_NETWORK_INFO.torConnections)
  val peerConn = MutableLiveData(Constants.DEFAULT_NETWORK_INFO.lightningConnected)

  override fun onCreate() {
    super.onCreate()
    log.info("creating node service...")
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    Prefs.getFCMToken(applicationContext) ?: run {
      FirebaseInstanceId.getInstance().instanceId
        .addOnCompleteListener(OnCompleteListener { task ->
          if (!task.isSuccessful) {
            log.warn("failed to retrieve fcm token", task.exception)
            return@OnCompleteListener
          }
          log.debug("retrieved fcm token")
          task.result?.token?.let { token ->
            Prefs.saveFCMToken(applicationContext, token)
          }
        })
    }
    appContext = AppContext.getInstance(applicationContext)
    appDb = AppDb.getInstance(applicationContext)
    paymentMetaRepository = PaymentMetaRepository.getInstance(appDb.paymentMetaQueries)
    payToOpenMetaRepository = PayToOpenMetaRepository.getInstance(appDb.payToOpenMetaQueries)
    notificationManager = NotificationManagerCompat.from(this)
    val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
    notificationBuilder.setSmallIcon(R.drawable.ic_phoenix_outline)
      .setOnlyAlertOnce(true)
      .setContentTitle(getString(R.string.notif__headless_title__default))
      .setContentIntent(PendingIntent.getActivity(this, Constants.NOTIF_ID__HEADLESS, intent, PendingIntent.FLAG_ONE_SHOT))
    log.info("service created")
  }

  override fun onBind(intent: Intent?): IBinder? {
    log.info("binding node service from intent=$intent")
    // UI is binding to the service. The service is not headless anymore and we can remove the notification.
    isHeadless = false
    stopForeground(STOP_FOREGROUND_REMOVE)
    notificationManager.cancel(Constants.NOTIF_ID__HEADLESS)
    return binder
  }

  /** When unbound, the service is running headless. */
  override fun onUnbind(intent: Intent?): Boolean {
    isHeadless = true
    return false
  }

  private val shutdownHandler = Handler()
  private val shutdownRunnable: Runnable = Runnable {
    if (isHeadless) {
      log.info("reached scheduled shutdown...")
      if (receivedInBackground.value == null || receivedInBackground.value!!.isEmpty()) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        stopForeground(STOP_FOREGROUND_DETACH)
        notificationManager.notify(Constants.NOTIF_ID__HEADLESS, notificationBuilder.setAutoCancel(true).build())
      }
      shutdown()
    }
  }

  /** Called when an intent is called for this service. */
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    log.info("start service from intent [ intent=$intent, flag=$flags, startId=$startId ]")
    val reason = intent?.getStringExtra(EXTRA_REASON)?.also { spawnReason = it }
    val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(applicationContext))
    when {
      state.value is KitState.Started -> {
        notifyForegroundService(getString(R.string.notif__headless_title__default), null)
      }
      encryptedSeed is EncryptedSeed.V2.NoAuth -> {
        try {
          EncryptedSeed.byteArray2ByteVector(encryptedSeed.decrypt()).run {
            log.info("starting kit from intent")
            notifyForegroundService(getString(R.string.notif__headless_title__default), null)
            startKit(this)
          }
        } catch (e: Exception) {
          log.info("failed to read encrypted seed=${encryptedSeed.name()}: ", e)
          if (reason == "IncomingPayment") {
            notifyForegroundService(getString(R.string.notif__headless_title__missed_incoming), getString(R.string.notif__headless_message__app_locked))
          } else {
            notifyForegroundService(getString(R.string.notif__headless_title__missed_fulfill), getString(R.string.notif__headless_message__pending_fulfill))
          }
        }
      }
      else -> {
        log.info("unhandled incoming payment with seed=${encryptedSeed?.name()}")
        if (reason == "IncomingPayment") {
          notifyForegroundService(getString(R.string.notif__headless_title__missed_incoming), getString(R.string.notif__headless_message__app_locked))
        } else {
          notifyForegroundService(getString(R.string.notif__headless_title__missed_fulfill), getString(R.string.notif__headless_message__pending_fulfill))
        }
      }
    }
    shutdownHandler.removeCallbacksAndMessages(null)
    shutdownHandler.postDelayed(shutdownRunnable, 60 * 1000)
    if (!isHeadless) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    EventBus.getDefault().unregister(this)
  }

  // ============================================================= //
  //                  START/STOP NODE AND SERVICE                  //
  // ============================================================= //

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

  /** Shutdown the node, close connections and stop the service */
  fun shutdown() {
    closeConnections()
    log.info("shutting down service in state=${state.value?.getName()}")
    stopSelf()
    updateState(KitState.Off)
  }

  /**
   * This method launches the node startup process. The application state will be updated to reflect the
   * various stages of the node startup.
   *
   * If the kit is already starting, started, or failed to start, this method will return early and do nothing.
   */
  @UiThread
  fun startKit(seed: ByteVector) {
    // Check app state consistency. Use a lock because the [startKit] method can be called concurrently.
    // If the kit is already starting, started, or in error, the method returns.
    val canProceed = try {
      stateLock.lock()
      val state = _state.value
      if (state !is KitState.Off) {
        log.warn("ignore attempt to start kit with app state=${state?.getName()}")
        false
      } else {
        updateState(KitState.Bootstrap.Init, lazy = false)
        true
      }
    } catch (e: Exception) {
      log.error("error in state check when starting kit: ", e)
      false
    } finally {
      stateLock.unlock()
    }

    if (canProceed) {
      serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.info("aborted node startup with ${e.javaClass.simpleName}")
        when (e) {
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
            log.error("error when bootstrapping Tor: ", e)
            updateState(KitState.Error.Tor(e.localizedMessage ?: e.javaClass.simpleName))
          }
          else -> {
            log.error("error when starting node: ", e)
            updateState(KitState.Error.Generic(e.localizedMessage ?: e.javaClass.simpleName))
          }
        }
        if (isHeadless) {
          shutdown()
          stopForeground(STOP_FOREGROUND_REMOVE)
        }
      }) {
        log.debug("initiating node startup from state=${_state.value?.getName()}")
        Migration.doMigration(applicationContext)
        val (_kit, xpub) = doStartNode(applicationContext, seed)
        updateState(KitState.Started(_kit, xpub))
        ChannelsWatcher.schedule(applicationContext)
      }
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
  private fun doStartNode(context: Context, seed: ByteVector): Pair<Kit, Xpub> {
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
      startTor()
      log.info("TOR has been bootstrapped")
    } else {
      log.info("using clear connection...")
    }

    updateState(KitState.Bootstrap.Node)
    val master = DeterministicWallet.generate(seed)
    val address = Wallet.buildAddress(master)
    val xpub = Wallet.buildXpub(master)

    Class.forName("org.sqlite.JDBC")
    val setup = Setup(Wallet.getDatadir(context), Option.apply(seed), Option.empty(), Option.apply(address), system)
    log.info("node setup ready, running version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

    // we could do this only once, but we want to make sure that previous installs, that were using DNS resolution
    // (which caused issues related to IPv6) do overwrite the previous value
    val acinqNodeAddress = `NodeAddress$`.`MODULE$`.fromParts(Wallet.ACINQ.address().host, Wallet.ACINQ.address().port).get()
    setup.nodeParams().db().peers().addOrUpdatePeer(Wallet.ACINQ.nodeId(), acinqNodeAddress)
    log.info("added/updated ACINQ to peer database address=${acinqNodeAddress}")

    val nodeSupervisor = system!!.actorOf(Props.create { EclairSupervisor(applicationContext) }, "EclairSupervisor")
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
    system.eventStream().subscribe(nodeSupervisor, ChannelErrorOccurred::class.java)
    system.eventStream().subscribe(nodeSupervisor, MissedPayToOpenPayment::class.java)

    val kit = Await.result(setup.bootstrap(), Duration.create(60, TimeUnit.SECONDS))
    // this is only needed to create the peer when we don't yet have any channel, connection will be handled by the reconnection task
    kit.switchboard().tell(Peer.`Connect$`.`MODULE$`.apply(Wallet.ACINQ.nodeId(), Option.empty()), ActorRef.noSender())
    log.info("bootstrap complete")
    return Pair(kit, xpub)
  }

  // =============================================================== //
  //                    INTERACTION WITH THE NODE                    //
  // =============================================================== //

  /** Default timeout for awaiting akka futures' completion */
  private val shortTimeout = Timeout(Duration.create(10, TimeUnit.SECONDS))

  /** Longer timeout in some cases where the futures could take a long time to complete. */
  private val longTimeout = Timeout(Duration.create(30, TimeUnit.SECONDS))

  private suspend inline fun <T, R> T.askKit(crossinline block: T.() -> R): R = withContext(serviceScope.coroutineContext) {
    kit?.run {
      block()
    } ?: throw KitNotInitialized
  }

  /** Retrieve list of channels from router. Can filter by state. */
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
      log.info("requesting to *mutual* close channels=$channelIds")
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
    return getChannels().filterNot {
      it.state() is `CLOSING$` || it.state() is `CLOSED$`
    }.map {
      val id: Either<ByteVector32, ShortChannelId> = Left.apply(it.channelId())
      id
    }.run {
      JavaConverters.asScalaIteratorConverter(iterator()).asScala().toList()
    }
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
        log.info("successfully closed channel=${res._1}")
        successfullyClosed++
      } else {
        log.info("failed to close channel=${res._1}: ", outcome.left().get() as Throwable)
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
    return Pair(MilliSatoshi(aggregateByRoutes.map { p -> p.first.toLong() }.maxOrNull() ?: 0),
      CltvExpiryDelta(aggregateByRoutes.map { p -> p.second.toInt() }.maxOrNull() ?: 0))
  }

  @UiThread
  suspend fun sendPaymentRequest(amount: MilliSatoshi, paymentRequest: PaymentRequest, subtractFee: Boolean): UUID? = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    if (isHeadless) {
      throw CannotSendHeadless
    }
    kit?.run {
      val cltvExpiryDelta = if (paymentRequest.minFinalCltvExpiryDelta().isDefined) paymentRequest.minFinalCltvExpiryDelta().get() else Channel.MIN_CLTV_EXPIRY_DELTA()
      val isTrampoline = paymentRequest.nodeId() != Wallet.ACINQ.nodeId()

      val sendRequest: Any = if (isTrampoline) {
        // 1 - compute trampoline fee settings for this payment
        val trampolineFeeSettings = Prefs.getMaxTrampolineCustomFee(appContext.applicationContext)?.let { pref ->
          appContext.trampolineFeeSettings.value!!.filter {
            it.feeBase <= pref.feeBase && it.feeProportionalMillionths <= pref.feeProportionalMillionths
          } + pref
        } ?: run {
          appContext.trampolineFeeSettings.value!!
        }.run {
          if (subtractFee) {
            // if fee is subtracted from amount, use ONLY the most expensive trampoline setting option, to make sure that the payment will go through
            listOf(last())
          } else {
            this
          }
        }
        val feeSettingsFromHints = getPessimisticRouteSettingsFromHint(amount, paymentRequest)
        log.info("most expensive fee/expiry from payment request hints=$feeSettingsFromHints")
        val finalTrampolineFeesList = JavaConverters.asScalaBufferConverter(trampolineFeeSettings
          .map {
            // the fee from routing hints is ignored.
            Tuple2(`package$`.`MODULE$`.nodeFee(Converter.any2Msat(it.feeBase), it.feeProportionalMillionths, amount), it.cltvExpiry.`$plus`(feeSettingsFromHints.second))
          })
          .asScala().toList()

        // 2 - compute amount to send, which changes if fees must be subtracted from it (empty wallet)
        val amountFinal = if (subtractFee) amount.`$minus`(finalTrampolineFeesList.head()._1) else amount

        // 3 - build trampoline payment object
        log.info("sending payment (trampoline) [ amount=$amountFinal, fees=$finalTrampolineFeesList, subtractFee=$subtractFee ] for pr=${PaymentRequest.write(paymentRequest)}")
        PaymentInitiator.SendTrampolinePaymentRequest(
          /* amount to send */ amountFinal,
          /* payment request */ paymentRequest,
          /* trampoline node public key */ Wallet.ACINQ.nodeId(),
          /* fees and expiry delta for the trampoline node */ finalTrampolineFeesList,
          /* final cltv expiry delta */ cltvExpiryDelta,
          /* route params */ Option.apply(null))
      } else {
        log.info("sending payment (direct) [ amount=$amount ] for pr=${PaymentRequest.write(paymentRequest)}")
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
      when (res) {
        is PaymentFailed -> {
          val failure = res.failures().mkString(", ")
          log.error("payment has failed: [ $failure ]")
          throw RuntimeException("payment failure: $failure")
        }
        is UUID -> res
        else -> {
          log.warn("unhandled payment initiator result: $res")
          null
        }
      }
    } ?: throw KitNotInitialized
  }

  /** Request swap-out details. */
  suspend fun requestSwapOut(amount: Satoshi, address: String, feeratePerKw: Long) = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      log.info("requesting swap-out request to address=$address with amount=$amount and fee=$feeratePerKw")
      switchboard().tell(Peer.SendSwapOutRequest(Wallet.ACINQ.nodeId(), amount, address, feeratePerKw), ActorRef.noSender())
      Unit
    } ?: throw KitNotInitialized
  }

  /** Request swap-in details. */
  suspend fun sendSwapIn() = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.run {
      switchboard().tell(Peer.SendSwapInRequest(Wallet.ACINQ.nodeId()), ActorRef.noSender())
      Unit
    } ?: throw KitNotInitialized
  }

  /** Generate a BOLT 11 payment request. */
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
          /* expiry in seconds */ Option.apply(7 * DateUtils.DAY_IN_MILLIS / 1000),
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

  suspend fun getPayments(): List<PaymentWithMeta> = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    kit?.let {
      val t = System.currentTimeMillis()
      JavaConverters.seqAsJavaListConverter(it.nodeParams().db().payments().listPaymentsOverview(50)).asJava().map { p ->
        val id = when {
          p is PlainOutgoingPayment && p.parentId().isDefined -> p.parentId().get().toString()
          else -> p.paymentHash().toString()
        }
        PaymentWithMeta(p, paymentMetaRepository.get(id))
      }.also { log.debug("retrieved payment list in ${System.currentTimeMillis() - t}ms") }
    } ?: throw KitNotInitialized
  }


  // =================================================== //
  //                     TOR HANDLING                    //
  // =================================================== //

  var torManager: OnionProxyManager? = null

  fun startTor() {
    torManager = TorHelper.bootstrap(applicationContext, object : TorEventHandler() {
      override fun onConnectionUpdate(name: String, status: TorConnectionStatus) {
        torConn.value?.run {
          this[name] = status
          torConn.postValue(this)
        }
      }
    })
  }

  @WorkerThread
  fun reconnectTor() {
    torManager?.run { enableNetwork(true) }
  }

  @UiThread
  suspend fun getTorInfo(cmd: String): String = withContext(serviceScope.coroutineContext + Dispatchers.Default) {
    torManager?.run { getInfo(cmd) } ?: throw RuntimeException("onion proxy manager not available")
  }

  // ===================================================== //
  //                  CONNECTION HANDLING                  //
  // ===================================================== //

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.ElectrumReady) {
    log.debug("received electrum ready=$event")
    electrumConn.value = ElectrumServer(electrumAddress = event.serverAddress().toString(), blockHeight = event.height(), tipTime = event.tip().time())
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: ElectrumClient.`ElectrumDisconnected$`) {
    log.debug("received electrum disconnected=$event")
    electrumConn.value = null
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PeerConnectionChange) {
    refreshPeerConnectionState()
  }

  private fun isConnectedToPeer(): Boolean = api?.run {
    try {
      JavaConverters.asJavaIterableConverter(
        Await.result(peers(shortTimeout), Duration.Inf()) as scala.collection.Iterable<Peer.PeerInfo>
      ).asJava().any { it.state().equals("CONNECTED", true) }
    } catch (e: Exception) {
      log.error("failed to retrieve connection state from peer: ${e.localizedMessage}")
      false
    }
  } ?: false

  /** Prevents spamming the peer */
  private var hasRefreshedFCMToken = false

  /** Check state of connection with peer and refresh the peer connection live data. If needed, register fcm token with peer. */
  fun refreshPeerConnectionState() {
    serviceScope.launch(Dispatchers.Default) {
      isConnectedToPeer().also {
        log.debug("peer connection ? $it")
        peerConn.postValue(it)
        if (it && !hasRefreshedFCMToken) {
          Prefs.getFCMToken(applicationContext)?.let { token ->
            refreshFCMToken(token)
            hasRefreshedFCMToken = true
          }
        }
      }
    }
  }

  /** Force the [fr.acinq.eclair.io.ReconnectionTask] to attempt reconnection to peer, if needed. */
  fun sendTickReconnect() {
    kit?.system()?.eventStream()?.publish(ReconnectionTask.`TickReconnect$`.`MODULE$`)
  }

  // ================================================ //
  //                  EVENTS HANDLING                 //
  // ================================================ //

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: ChannelClosingEvent) {
    // store channel closing event as an outgoing payment in database.
    kit?.run {
      serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.error("failed to save closing event=$event: ", e)
      }) {
        if (event.balance == MilliSatoshi(0)) {
          log.info("ignore closing channel event=$event because our balance is empty")
        } else {
          log.info("save closing channel event=$event")
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
          paymentMetaRepository.insertClosing(id.toString(), event.closingType, event.channelId.toString(), event.spendingTxs, event.scriptDestMainOutput)
          val partialPayment = PaymentSent.PartialPayment(id, event.balance, MilliSatoshi(0), ByteVector32.Zeroes(), Option.empty(), date)
          val paymentCounterpartSent = PaymentSent(id, paymentHash, preimage, event.balance, fakeRecipientId,
            ScalaList.empty<PaymentSent.PartialPayment>().`$colon$colon`(partialPayment))
          nodeParams().db().payments().updateOutgoingPayment(paymentCounterpartSent)
          EventBus.getDefault().post(PaymentPending())
        }
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: SwapInConfirmed) {
    // a confirmed swap-in means that a channel was opened ; this event is stored as an incoming payment in payment database.
    kit?.run {
      serviceScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        log.error("failed to create and settle payment request placeholder for ${event.bitcoinAddress()}: ", e)
      }) {
        log.info("saving swap-in=$event as incoming payment")

        // 1 - generate fake invoice
        val description = applicationContext.getString(R.string.paymentholder_swap_in_desc, event.bitcoinAddress())
        val pr = doGeneratePaymentRequest(
          description = description,
          amount_opt = Option.apply(event.amount()),
          routes = ScalaList.empty<ScalaList<PaymentRequest.ExtraHop>>(),
          timeout = Timeout(Duration.create(10, TimeUnit.MINUTES)),
          paymentType = PaymentType.SwapIn())

        // 2 - save payment in eclair db, and save additional metadata such as the address
        paymentMetaRepository.insertSwapIn(pr.paymentHash().toString(), event.bitcoinAddress())
        nodeParams().db().payments().receiveIncomingPayment(pr.paymentHash(), event.amount(), System.currentTimeMillis())
        log.info("swap-in=$event saved with payment_hash=${pr.paymentHash()}, amount=${pr.amount()}")

        // 3 - notify UI
        EventBus.getDefault().post(RemovePendingSwapIn(event.bitcoinAddress()))
        EventBus.getDefault().post(PaymentReceived(pr.paymentHash(),
          ScalaList.empty<PaymentReceived.PartialPayment>().`$colon$colon`(PaymentReceived.PartialPayment(event.amount(), ByteVector32.Zeroes(), System.currentTimeMillis()))))
      }
    } ?: log.error("could not create and settle placeholder for on-chain payment because kit is not initialized")
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: PayToOpenRequestEvent) {
    val autoAcceptPayToOpen = Prefs.getAutoAcceptPayToOpen(applicationContext)
    if (autoAcceptPayToOpen) {
      log.info("automatically accepting pay-to-open=$event")
      acceptPayToOpen(event.payToOpenRequest().paymentHash())
    } else {
      if (isHeadless) {
        log.info("automatically rejecting pay-to-open=$event")
        updateNotification(getString(R.string.notif__headless_title__missed_incoming), getString(R.string.notif__headless_message__manual_pay_to_open))
        rejectPayToOpen(event.payToOpenRequest().paymentHash())
      } else {
        EventBus.getDefault().post(PayToOpenNavigationEvent(event.payToOpenRequest()))
        if (!appContext.isAppVisible) {
          notificationManager.notify(Constants.NOTIF_ID__PAY_TO_OPEN, NotificationCompat.Builder(applicationContext, Constants.NOTIF_CHANNEL_ID__PAY_TO_OPEN)
            .setSmallIcon(R.drawable.ic_phoenix_outline)
            .setContentTitle(getString(R.string.notif_pay_to_open_title))
            .setContentText(getString(R.string.notif_pay_to_open_message))
            .setContentIntent(PendingIntent.getActivity(applicationContext, Constants.NOTIF_ID__PAY_TO_OPEN,
              Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build())
        }
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: MissedPayToOpenPayment) {
    notificationManager.notify(Constants.NOTIF_ID__PAY_TO_OPEN, NotificationCompat.Builder(applicationContext, Constants.NOTIF_CHANNEL_ID__PAY_TO_OPEN)
      .setSmallIcon(R.drawable.ic_phoenix_outline)
      .setContentTitle(getString(R.string.notif__pay_to_open_missed_title, Converter.printAmountPretty(event.amount(), applicationContext, withUnit = true)))
      .setContentText(getString(R.string.notif__pay_to_open_missed_message))
      .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notif__pay_to_open_missed_message)))
      .setContentIntent(PendingIntent.getActivity(applicationContext, Constants.NOTIF_ID__PAY_TO_OPEN,
        Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_UPDATE_CURRENT))
      .setAutoCancel(true)
      .build())
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: FCMToken) {
    refreshFCMToken(event.token)
  }

  /** Set or unset the FCM token with the peer, depending on the encrypted seed type. */
  fun refreshFCMToken(token: String?) {
    kit?.run {
      when (SeedManager.getSeedFromDir(Wallet.getDatadir(applicationContext))) {
        is EncryptedSeed.V2.NoAuth -> {
          log.info("registering fcm token=$token with node=${Wallet.ACINQ.nodeId()}")
          token?.let { switchboard().tell(Peer.SendSetFCMToken(Wallet.ACINQ.nodeId(), it), ActorRef.noSender()) }
        }
        else -> {
          log.info("unregistering fcm token from node=${Wallet.ACINQ.nodeId()}")
          switchboard().tell(Peer.SendUnsetFCMToken(Wallet.ACINQ.nodeId()), ActorRef.noSender())
        }
      }
    } ?: log.info("could not refresh fcm token because kit is not ready yet")
  }

  // =========================================================== //
  //                 STATE UPDATE & NOTIFICATIONS                //
  // =========================================================== //

  /**
   * Update the app mutable [_state] and show a notification if the service is headless.
   * @param newState The new state of the app.
   * @param lazy `true` to update with postValue, `false` to commit the state directly. If not lazy, this method MUST be called from the main thread!
   */
  @Synchronized
  private fun updateState(newState: KitState, lazy: Boolean = true) {
    log.info("updating state from {} to {} with headless={}", _state.value?.getName(), newState.getName(), isHeadless)
    if (_state.value != newState) {
      if (lazy) {
        _state.postValue(newState)
      } else {
        _state.value = newState
      }
    } else {
      log.debug("ignored attempt to update state=${_state.value} to state=$newState")
    }
  }

  /** Display a blocking notification and set the service as being foregrounded. */
  private fun notifyForegroundService(title: String?, message: String?) {
    log.debug("notifying foreground service with msg=$message")
    updateNotification(title, message).also { startForeground(Constants.NOTIF_ID__HEADLESS, it) }
  }

  private fun updateNotification(title: String?, message: String?): Notification {
    title?.let { notificationBuilder.setContentTitle(it) }
    message?.let {
      notificationBuilder.setContentText(message)
      notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
    }
    return notificationBuilder.build().apply {
      notificationManager.notify(Constants.NOTIF_ID__HEADLESS, this)
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: PaymentReceived) {
    if (isHeadless) {
      handleReceivedPaymentHeadless(event.amount())
    }
  }

  private fun handleReceivedPaymentHeadless(amount: MilliSatoshi) {
    (receivedInBackground.value ?: emptyList()).run {
      this + amount
    }.let {
      val total = it.reduce { acc, amount -> acc.`$plus`(amount) }
      val message = getString(R.string.notif__headless_message__received_payment,
        Converter.printAmountPretty(total, applicationContext, withSign = false, withUnit = true),
        Converter.printFiatPretty(applicationContext, total, withSign = false, withUnit = true))
      updateNotification(getString(R.string.notif__headless_title__received), message)
      receivedInBackground.postValue(it)
      shutdownHandler.removeCallbacksAndMessages(null)
      shutdownHandler.postDelayed(shutdownRunnable, 60 * 1000)
    }
  }

  inner class NodeBinder : Binder() {
    fun getService(): EclairNodeService = this@EclairNodeService
  }
}

/**
 * 4 possible states:
 * - idle, waiting for the node to be started ;
 * - the node is starting ;
 * - the node is started ;
 * - the node failed to start.
 */
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
    object NoConnectivity : Error()
    object UnreadableData : Error()
  }

  /** Get a human readable state name */
  fun getName(): String = this.javaClass.simpleName

  /** Get the node's wallet master public key */
  fun getXpub(): Xpub? = if (this is Started) {
    xpub
  } else null

  /** Get node public key */
  fun getNodeId(): Crypto.PublicKey? = if (this is Started) {
    kit.nodeParams().nodeId()
  } else null

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

data class Xpub(val xpub: String, val path: String)
data class ElectrumServer(val electrumAddress: String, val blockHeight: Int, val tipTime: Long)
