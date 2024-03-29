package fr.acinq.phoenix.android.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateUtils
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.io.PaymentReceived
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.LegacyMigrationHelper
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.CurrencyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.hours

class NodeService : Service() {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    lateinit var internalData: InternalDataRepository
    private val binder = NodeBinder()

    /** True if the service is running headless (that is without a GUI). In that case we should show a notification */
    @Volatile
    private var isHeadless = true

    // Notifications
    private lateinit var notificationManager: NotificationManagerCompat

    /** State of the wallet, provides access to the business when started. Private so that it's not mutated from the outside. */
    private val _state = MutableLiveData<NodeServiceState>(NodeServiceState.Off)
    val state: LiveData<NodeServiceState> get() = _state

    /** Lock for state updates */
    private val stateLock = ReentrantLock()

    /** List of payments received while the app is in the background */
    private val receivedInBackground = mutableStateListOf<MilliSatoshi>()

    // jobs monitoring events/payments after business start
    private var monitorPaymentsJob: Job? = null
    private var monitorNodeEventsJob: Job? = null
    private var monitorFcmTokenJob: Job? = null
    private var monitorInFlightPaymentsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        log.debug("creating node service...")
        internalData = (applicationContext as PhoenixApplication).internalDataRepository
        notificationManager = NotificationManagerCompat.from(this)
        refreshFcmToken()
        log.debug("service created")
    }

    internal fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                log.warn("fetching FCM registration token failed: ${task.exception?.localizedMessage}")
                return@OnCompleteListener
            }
            task.result?.let { serviceScope.launch { internalData.saveFcmToken(it) } }
        })
    }

    // =========================================================== //
    //                      SERVICE LIFECYCLE                      //
    // =========================================================== //

    override fun onBind(intent: Intent?): IBinder {
        log.debug("binding node service from intent=$intent")
        // UI is binding to the service. The service is not headless anymore and we can remove the notification.
        isHeadless = false
        receivedInBackground.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(SystemNotificationHelper.HEADLESS_NOTIF_ID)
        return binder
    }

    /** When unbound, the service is running headless. */
    override fun onUnbind(intent: Intent?): Boolean {
        isHeadless = true
        return false
    }

    private val shutdownHandler = Handler(Looper.getMainLooper())
    private val shutdownRunnable: Runnable = Runnable {
        if (isHeadless) {
            log.debug("reached scheduled shutdown...")
            if (receivedInBackground.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            shutdown()
        }
    }

    /** Shutdown the node, close connections and stop the service */
    fun shutdown() {
        log.info("shutting down service in state=${_state.value?.name}")
        monitorNodeEventsJob?.cancel()
        stopSelf()
        _state.postValue(NodeServiceState.Off)
    }

    // =========================================================== //
    //                    START COMMAND HANDLER                    //
    // =========================================================== //

    /** Called when an intent is called for this service. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log.info("start service from intent [ intent=$intent, flag=$flags, startId=$startId ]")
        val reason = intent?.getStringExtra(EXTRA_REASON)

        fun startForeground(notif: Notification) {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, SystemNotificationHelper.HEADLESS_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
            }
        }

        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
        when {
            _state.value is NodeServiceState.Running -> {
                // NOTE: the notification will NOT be shown if the app is already running
                val notif = SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                startForeground(notif)
            }
            encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                val seed = encryptedSeed.decrypt()
                log.debug("successfully decrypted seed in the background, starting wallet...")
                val notif = SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                startBusiness(seed, requestCheckLegacyChannels = false)
                startForeground(notif)
            }
            else -> {
                log.warn("unhandled incoming payment with seed=${encryptedSeed?.name()} reason=$reason")
                val notif = when (reason) {
                    "IncomingPayment" -> SystemNotificationHelper.notifyPaymentMissedAppUnavailable(applicationContext)
                    "PendingSettlement" -> SystemNotificationHelper.notifyPendingSettlement(applicationContext)
                    else -> SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                }
                startForeground(notif)
            }
        }
        shutdownHandler.removeCallbacksAndMessages(null)
        shutdownHandler.postDelayed(shutdownRunnable, 2 * 60 * 1000L) // service will shutdown in 2 minutes
        if (!isHeadless) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return START_NOT_STICKY
    }

    // =========================================================== //
    //                      START BUSINESS                         //
    // =========================================================== //

    /**
     * Start the node business logic.
     * @param decryptedMnemonics Must be the decrypted payload of an [EncryptedSeed] object.
     */
    fun startBusiness(decryptedMnemonics: ByteArray, requestCheckLegacyChannels: Boolean) {
        stateLock.lock()
        if (_state.value != NodeServiceState.Off) {
            log.warn("ignore attempt to start business in state=${_state.value}")
            return
        } else {
            _state.postValue(NodeServiceState.Init)
        }
        stateLock.unlock()

        serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when starting node: ", e)
            _state.postValue(NodeServiceState.Error(e))
            if (isHeadless) {
                shutdown()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }) {
            log.info("cancel competing workers")
            val wm = WorkManager.getInstance(applicationContext)
            withContext(Dispatchers.IO) {
                wm.getWorkInfosByTag(InflightPaymentsWatcher.TAG).get() + wm.getWorkInfosByTag(ChannelsWatcher.TAG).get()
            }.forEach {
                wm.cancelWorkById(it.id).result.get()
            }

            log.info("starting node from service state=${_state.value?.name} with checkLegacyChannels=$requestCheckLegacyChannels")
            doStartBusiness(decryptedMnemonics, requestCheckLegacyChannels)
            ChannelsWatcher.schedule(applicationContext)
            _state.postValue(NodeServiceState.Running)
        }
    }

    private suspend fun doStartBusiness(
        decryptedMnemonics: ByteArray,
        requestCheckLegacyChannels: Boolean,
    ) {
        // migrate legacy preferences if needed
        if (LegacyPrefsDatastore.getPrefsMigrationExpected(applicationContext).first() == true) {
            LegacyMigrationHelper.migrateLegacyPreferences(applicationContext)
            LegacyPrefsDatastore.savePrefsMigrationExpected(applicationContext, false)
        }

        // retrieve preferences before starting business
        val application = (applicationContext as? PhoenixApplication) ?: throw RuntimeException("invalid context type, should be PhoenixApplication")
        val userPrefs = application.userPrefs
        val business = application.business.filterNotNull().first()
        val electrumServer = userPrefs.getElectrumServer.first()
        val isTorEnabled = userPrefs.getIsTorEnabled.first()
        val liquidityPolicy = userPrefs.getLiquidityPolicy.first()
        val trustedSwapInTxs = LegacyPrefsDatastore.getMigrationTrustedSwapInTxs(applicationContext).first()
        val preferredFiatCurrency = userPrefs.getFiatCurrency.first()

        monitorPaymentsJob = serviceScope.launch { monitorPaymentsWhenHeadless(business.peerManager, business.currencyManager, userPrefs) }
        monitorNodeEventsJob = serviceScope.launch { monitorNodeEvents(business.peerManager, business.nodeParamsManager) }
        monitorFcmTokenJob = serviceScope.launch { monitorFcmToken(business) }
        monitorInFlightPaymentsJob = serviceScope.launch { monitorInFlightPayments(business.peerManager) }

        // preparing business
        val seed = business.walletManager.mnemonicsToSeed(EncryptedSeed.toMnemonics(decryptedMnemonics), wordList = MnemonicLanguage.English.wordlist())
        business.walletManager.loadWallet(seed)
        business.appConfigurationManager.updateElectrumConfig(electrumServer)
        business.appConfigurationManager.updatePreferredFiatCurrencies(
            AppConfigurationManager.PreferredFiatCurrencies(primary = preferredFiatCurrency, others = emptySet())
        )

        // start business
        business.start(
            StartupParams(
                requestCheckLegacyChannels = requestCheckLegacyChannels,
                isTorEnabled = isTorEnabled,
                liquidityPolicy = liquidityPolicy,
                trustedSwapInTxs = trustedSwapInTxs.map { TxId(it) }.toSet()
            )
        )

        // start the swap-in wallet watcher
        serviceScope.launch {
            business.peerManager.getPeer().startWatchSwapInWallet()
        }
    }

    private suspend fun monitorFcmToken(business: PhoenixBusiness) {
        val token = internalData.getFcmToken.filterNotNull().first()
        business.connectionsManager.connections.first { it.peer == Connection.ESTABLISHED }
        business.registerFcmToken(token)
    }

    private suspend fun monitorNodeEvents(peerManager: PeerManager, nodeParamsManager: NodeParamsManager) {
        val monitoringStartedAt = currentTimestampMillis()
        combine(peerManager.swapInNextTimeout, nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents) { nextTimeout, nodeEvent ->
            nextTimeout to nodeEvent
        }.collect { (nextTimeout, event) ->
            // TODO: click on notif must deeplink to the notification screen
            when (event) {
                is LiquidityEvents.Rejected -> {
                    log.debug("processing liquidity_event=$event")
                    if (event.source == LiquidityEvents.Source.OnChainWallet) {
                        // Check the last time a rejected on-chain swap notification has been shown. If recent, we do not want to trigger a notification every time.
                        val lastRejectedSwap = internalData.getLastRejectedOnchainSwap.first().takeIf {
                            // However, if the app started < 2 min ago, we always want to display a notification. So we'll ignore this check ^
                            currentTimestampMillis() - monitoringStartedAt >= 2 * DateUtils.MINUTE_IN_MILLIS
                        }
                        if (lastRejectedSwap != null
                            && lastRejectedSwap.first == event.amount
                            && currentTimestampMillis() - lastRejectedSwap.second <= 2 * DateUtils.HOUR_IN_MILLIS
                        ) {
                            log.debug("ignore this liquidity event as a similar notification was recently displayed")
                            return@collect
                        } else {
                            internalData.saveLastRejectedOnchainSwap(event)
                        }
                    }
                    when (val reason = event.reason) {
                        is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> {
                            SystemNotificationHelper.notifyPaymentRejectedPolicyDisabled(applicationContext, event.source, event.amount, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee -> {
                            SystemNotificationHelper.notifyPaymentRejectedOverAbsolute(applicationContext, event.source, event.amount, event.fee, reason.maxAbsoluteFee, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee -> {
                            SystemNotificationHelper.notifyPaymentRejectedOverRelative(applicationContext, event.source, event.amount, event.fee, reason.maxRelativeFeeBasisPoints, nextTimeout?.second)
                        }
                        LiquidityEvents.Rejected.Reason.ChannelInitializing -> {
                            SystemNotificationHelper.notifyPaymentRejectedChannelsInitializing(applicationContext, event.source, event.amount, nextTimeout?.second)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorPaymentsWhenHeadless(peerManager: PeerManager, currencyManager: CurrencyManager, userPrefs: UserPrefsRepository) {
        peerManager.getPeer().eventsFlow.collect { event ->
            when (event) {
                is PaymentReceived -> {
                    if (isHeadless) {
                        receivedInBackground.add(event.received.amount)
                        SystemNotificationHelper.notifyPaymentsReceived(
                            context = applicationContext,
                            userPrefs = userPrefs,
                            paymentHash = event.incomingPayment.paymentHash,
                            amount = event.received.amount,
                            rates = currencyManager.ratesFlow.value,
                            isHeadless = isHeadless && receivedInBackground.size == 1
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorInFlightPayments(peerManager: PeerManager) {
        peerManager.channelsFlow.filterNotNull().collect {
            val inFlightPaymentsCount = it.inFlightPaymentsCount()
            internalData.saveInFlightPaymentsCount(inFlightPaymentsCount)
            if (inFlightPaymentsCount == 0) {
                InflightPaymentsWatcher.cancel(applicationContext)
            } else {
                InflightPaymentsWatcher.scheduleOnce(applicationContext, delay = 2.hours)
            }
        }
    }

    inner class NodeBinder : Binder() {
        fun getService(): NodeService = this@NodeService
    }

    companion object {
        const val EXTRA_REASON = "${BuildConfig.APPLICATION_ID}.SERVICE_SPAWN_REASON"
    }
}