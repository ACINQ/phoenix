package fr.acinq.phoenix.android.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.io.PaymentReceived
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.CurrencyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.lang.Runnable
import java.util.concurrent.locks.ReentrantLock

class NodeService : Service() {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val binder = NodeBinder()

    /** True if the service is running headless (that is without a GUI). In that case we should show a notification */
    @Volatile
    private var isHeadless = true

    // Notifications
    private lateinit var notificationManager: NotificationManagerCompat

    /** State of the wallet, provides access to the business when started. Private so that it's not mutated from the outside. */
    private val _state = MutableLiveData<WalletState>(WalletState.Off)
    val state: LiveData<WalletState> get() = _state

    /** Lock for state updates */
    private val stateLock = ReentrantLock()

    /** List of payments received while the app is in the background */
    private val receivedInBackground = mutableStateListOf<MilliSatoshi>()

    override fun onCreate() {
        super.onCreate()
        log.info("creating node service...")
        notificationManager = NotificationManagerCompat.from(this)
        refreshFcmToken()
        log.info("service created")
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                log.warn("fetching FCM registration token failed: ", task.exception)
                return@OnCompleteListener
            }
            task.result?.let { serviceScope.launch { InternalData.saveFcmToken(applicationContext, it) } }
        })
    }

    // =========================================================== //
    //                      SERVICE LIFECYCLE                      //
    // =========================================================== //

    override fun onBind(intent: Intent?): IBinder {
        log.info("binding node service from intent=$intent")
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
        // closeConnections()
        log.info("shutting down service in state=${state.value?.name}")
        stopSelf()
        updateState(WalletState.Off)
    }

    // =========================================================== //
    //                    START COMMAND HANDLER                    //
    // =========================================================== //

    /** Called when an intent is called for this service. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log.info("start service from intent [ intent=$intent, flag=$flags, startId=$startId ]")
        val reason = intent?.getStringExtra(EXTRA_REASON)

        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
        when {
            state.value is WalletState.Started -> {
                // NOTE: the notification will NOT be shown if the app is already running
                val notif = SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
            }
            encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                encryptedSeed.decrypt().let {
                    log.info("successfully decrypted seed in the background, starting wallet...")
                    val notif = SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                    startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
                    startBusiness(it, requestCheckLegacyChannels = false)
                }
            }
            else -> {
                log.info("unhandled incoming payment with seed=${encryptedSeed?.name()} reason=$reason")
                val notif = when (reason) {
                    "IncomingPayment" -> SystemNotificationHelper.notifyPaymentMissedAppUnavailable(applicationContext)
                    "PendingSettlement" -> SystemNotificationHelper.notifyPendingSettlement(applicationContext)
                    else -> SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                }
                startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
            }
        }
        shutdownHandler.removeCallbacksAndMessages(null)
        shutdownHandler.postDelayed(shutdownRunnable, 60 * 1000L) // push back shutdown by 60s
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
     * @param decryptedPayload Must be the decrypted payload of an [EncryptedSeed] object.
     */
    fun startBusiness(decryptedPayload: ByteArray, requestCheckLegacyChannels: Boolean) {
        serviceScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            log.error("error when checking node state consistency before startup: ", e)
        }) {
            // Check node state consistency. Use a lock because the [doStartNode] method can be called concurrently.
            // If the node is already starting, started, or in error, the method returns.
            val canProceed = try {
                stateLock.lock()
                val state = _state.value
                if (state !is WalletState.Off) {
                    log.warn("ignore attempt to start kit with app state=${state?.name}")
                    false
                } else {
                    updateState(WalletState.Bootstrap.Init, lazy = false)
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
                    log.error("error when starting node: ", e)
                    updateState(WalletState.Error.Generic(e.localizedMessage ?: e.javaClass.simpleName))
                    if (isHeadless) {
                        shutdown()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }) {
                    log.debug("initiating node startup from state=${_state.value?.name} with requestCLC=$requestCheckLegacyChannels")
                    val state = doStartNode(decryptedPayload, requestCheckLegacyChannels)
                    updateState(state)
                }
            }
        }
    }

    @WorkerThread
    private suspend fun doStartNode(
        decryptedPayload: ByteArray,
        requestCheckLegacyChannels: Boolean,
    ): WalletState.Started {
        log.info("starting up node...")
        val business = (applicationContext as? PhoenixApplication)?.business ?: throw RuntimeException("invalid context type, should be PhoenixApplication")
        val electrumServer = UserPrefs.getElectrumServer(applicationContext).first()
        val isTorEnabled = UserPrefs.getIsTorEnabled(applicationContext).first()
        val liquidityPolicy = UserPrefs.getLiquidityPolicy(applicationContext).first()
        val trustedSwapInTxs = LegacyPrefsDatastore.getMigrationTrustedSwapInTxs(applicationContext).first()
        val preferredFiatCurrency = UserPrefs.getFiatCurrency(applicationContext).first()
        val seed = business.walletManager.mnemonicsToSeed(EncryptedSeed.toMnemonics(decryptedPayload))

        monitorPaymentsWhenHeadless(business.peerManager, business.nodeParamsManager, business.currencyManager)
        business.walletManager.loadWallet(seed)
        business.appConfigurationManager.updatePreferredFiatCurrencies(
            AppConfigurationManager.PreferredFiatCurrencies(primary = preferredFiatCurrency, others = emptySet())
        )
        business.start(
            StartupParams(
                requestCheckLegacyChannels = requestCheckLegacyChannels,
                isTorEnabled = isTorEnabled,
                liquidityPolicy = liquidityPolicy,
                trustedSwapInTxs = trustedSwapInTxs.map { ByteVector32.fromValidHex(it) }.toSet()
            )
        )
        business.appConfigurationManager.updateElectrumConfig(electrumServer)

        serviceScope.launch {
            val token = InternalData.getFcmToken(applicationContext).first()
            log.debug("retrieved from prefs fcm token=$token")
            var hasRegisteredToken = false
            business.connectionsManager.connections.collect {
                if (it.peer == Connection.ESTABLISHED && !hasRegisteredToken) {
                    business.registerFcmToken(token)
                    hasRegisteredToken = true
                }
            }
        }
        ChannelsWatcher.schedule(applicationContext)
        return WalletState.Started.Kmm(business)
    }

    private fun monitorPaymentsWhenHeadless(peerManager: PeerManager, nodeParamsManager: NodeParamsManager, currencyManager: CurrencyManager) {

        serviceScope.launch {
            nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents.collect { event ->
                // TODO: click on notif must deeplink to the notification screen
                when (event) {
                    is LiquidityEvents.Rejected -> {
                        log.debug("processing liquidity_event=$event")
                        when (val reason = event.reason) {
                            is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> {
                                SystemNotificationHelper.notifyPaymentRejectedPolicyDisabled(applicationContext, event.source, event.amount)
                            }
                            is LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee -> {
                                SystemNotificationHelper.notifyPaymentRejectedOverAbsolute(applicationContext, event.source, event.amount, event.fee, reason.maxAbsoluteFee)
                            }
                            is LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee -> {
                                SystemNotificationHelper.notifyPaymentRejectedOverRelative(applicationContext, event.source, event.amount, event.fee, reason.maxRelativeFeeBasisPoints)
                            }
                            LiquidityEvents.Rejected.Reason.ChannelInitializing -> {
                                SystemNotificationHelper.notifyPaymentRejectedChannelsInitializing(applicationContext, event.source, event.amount)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        serviceScope.launch {
            peerManager.getPeer().eventsFlow.collect { event ->
                when (event) {
                    is PaymentReceived -> {
                        if (isHeadless) {
                            receivedInBackground.add(event.received.amount)
                            SystemNotificationHelper.notifyPaymentsReceived(
                                context = applicationContext,
                                paymentHash = event.incomingPayment.paymentHash,
                                amount = event.received.amount,
                                rates = currencyManager.ratesFlow.value,
                                isHeadless = isHeadless && receivedInBackground.size == 1
                            )

                            // push back service shutdown by 60s - maybe we'll receive more payments?
                            shutdownHandler.removeCallbacksAndMessages(null)
                            shutdownHandler.postDelayed(shutdownRunnable, 60 * 1000)
                        }
                    }
                    else -> Unit
                }
            }
        }
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
    private fun updateState(newState: WalletState, lazy: Boolean = true) {
        log.info("updating state from {} to {} with headless={}", _state.value?.name, newState.name, isHeadless)
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

    inner class NodeBinder : Binder() {
        fun getService(): NodeService = this@NodeService
    }

    companion object {
        const val EXTRA_REASON = "${BuildConfig.APPLICATION_ID}.SERVICE_SPAWN_REASON"
    }
}