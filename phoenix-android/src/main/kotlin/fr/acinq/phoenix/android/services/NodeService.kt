package fr.acinq.phoenix.android.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.PaymentEvents
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.hours

abstract class NodeService : Service() {

    internal val log = LoggerFactory.getLogger(this::class.java)

    internal val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    lateinit var internalData: InternalDataRepository
    private val binder = NodeBinder()

    var isHeadless by mutableStateOf(false)

    lateinit var notificationManager: NotificationManagerCompat

    /** State of the wallet, provides access to the business when started. Private so that it's not mutated from the outside. */
    private val _state = MutableLiveData<NodeServiceState>(NodeServiceState.Off)
    val state: LiveData<NodeServiceState> get() = _state

    /** Lock for state updates */
    private val stateLock = ReentrantLock()

    /** List of payments received while the app is in the background */
    val receivedInBackground = mutableStateListOf<MilliSatoshi>()

    // jobs monitoring events/payments after business start
    private var monitorPaymentsJob: Job? = null
    private var monitorNodeEventsJob: Job? = null
    private var monitorFcmTokenJob: Job? = null
    private var monitorInFlightPaymentsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        log.info("creating node service...")
        internalData = (applicationContext as PhoenixApplication).internalDataRepository
        notificationManager = NotificationManagerCompat.from(this)
        refreshFcmToken()
        log.debug("service created")
    }

    abstract fun refreshFcmToken()
    abstract fun deleteFcmToken()

    abstract fun isFcmAvailable(context: Context): Boolean

    abstract suspend fun monitorFcmToken(business: PhoenixBusiness)

    // =========================================================== //
    //                      SERVICE LIFECYCLE                      //
    // =========================================================== //

    override fun onBind(intent: Intent?): IBinder {
        log.debug("binding node service from intent={}", intent)
        receivedInBackground.clear()
        return binder
    }

    /** When unbound, the service is running headless. */
    override fun onUnbind(intent: Intent?): Boolean {
        return false
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(SystemNotificationHelper.HEADLESS_NOTIF_ID)
        shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        log.info("service destroyed")
    }

    // =========================================================== //
    //                          UTILITY                            //
    // =========================================================== //

    /** Shutdown the node, close connections and stop the service */
    fun shutdown() {
        log.info("shutting down service in state=${_state.value?.name}")
        serviceScope.launch {
            (application as? PhoenixApplication)?.business?.first()?.stop()
            monitorNodeEventsJob?.cancel()
            stopSelf()
            _state.postValue(NodeServiceState.Off)
            log.info("shutdown complete")
        }
    }

    internal fun startForeground(notif: Notification, foregroundServiceType: Int) {
        log.info("--- starting service in foreground with type=$foregroundServiceType ---")
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, SystemNotificationHelper.HEADLESS_NOTIF_ID, notif, foregroundServiceType)
        } else {
            startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
        }
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

        monitorPaymentsJob = serviceScope.launch { monitorPaymentsWhenHeadless(business.nodeParamsManager, business.currencyManager, userPrefs) }
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

    private suspend fun monitorNodeEvents(peerManager: PeerManager, nodeParamsManager: NodeParamsManager) {
        val monitoringStartedAt = currentTimestampMillis()
        combine(peerManager.swapInNextTimeout, nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents) { nextTimeout, nodeEvent ->
            nextTimeout to nodeEvent
        }.collect { (nextTimeout, event) ->
            when (event) {
                is LiquidityEvents.Rejected -> {
                    log.debug("processing liquidity_event={}", event)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun monitorPaymentsWhenHeadless(nodeParamsManager: NodeParamsManager, currencyManager: CurrencyManager, userPrefs: UserPrefsRepository) {
        nodeParamsManager.nodeParams.filterNotNull().flatMapLatest { it.nodeEvents }.collect { event ->
            when (event) {
                is PaymentEvents.PaymentReceived -> {
                    if (isHeadless) {
                        receivedInBackground.add(event.amount)
                        SystemNotificationHelper.notifyPaymentsReceived(
                            context = applicationContext,
                            userPrefs = userPrefs,
                            paymentHash = event.paymentHash,
                            amount = event.amount,
                            rates = currencyManager.ratesFlow.value,
                            isFromBackground = isHeadless
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
        const val EXTRA_ORIGIN = "${BuildConfig.APPLICATION_ID}.SERVICE_SPAWN_ORIGIN"
        const val ORIGIN_FCM = "fcm"
        const val ORIGIN_HEADLESS = "fcm"
    }
}
