/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android

import android.content.Context
import android.text.format.DateUtils
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.PaymentEvents
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.components.wallet.WalletAvatars
import fr.acinq.phoenix.android.services.InflightPaymentsWatcher
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.datastore.InternalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.managers.CurrencyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.utils.MnemonicLanguage
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours


sealed class StartBusinessResult {
    data class Success(val walletInfo: WalletManager.WalletInfo, val business: PhoenixBusiness): StartBusinessResult()
    sealed class Failure : StartBusinessResult() {
        data class Generic(val cause: Throwable): Failure()
        data object LoadWalletError: Failure()
    }
}

object BusinessManager {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisor)
    private val startupMutex = Mutex()

    // No memory leaks because this can only contain the application context.
    private lateinit var appContext: Context
    private val application by lazy { appContext as PhoenixApplication }

    /** A map of (walletId -> active businesses) */
    private val _businessFlow = MutableStateFlow<Map<WalletId, PhoenixBusiness>>(emptyMap())
    val businessFlow = _businessFlow.asStateFlow()

    private var _isHeadless = MutableStateFlow(true)
    val isHeadless = _isHeadless.asStateFlow()

    /** Mpa of jobs monitoring events/payments after business starts */
    private val eventsMonitoringJobs = mutableMapOf<WalletId, List<Job>>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * This method creates and starts a new business from a given [decryptedMnemonics], and adds it to the flow of started businesses.
     *
     * If a business already exists for that seed, the method does nothing.
     *
     * @param words bip39 mnemonics
     * @param isHeadless true if started from a service (e.g. after a FCM notification), false if started from the UI.
     */
    suspend fun startNewBusiness(words: List<String>, isHeadless: Boolean): StartBusinessResult = startupMutex.withLock {

        _isHeadless.value = isHeadless

        val business = PhoenixBusiness(PlatformContext(appContext))

        val walletInfo = try {
            log.debug("loading wallet before starting a new business")
            val seed = business.walletManager.mnemonicsToSeed(words, wordList = MnemonicLanguage.English.wordlist())
            business.walletManager.loadWallet(seed)
        } catch (e: Exception) {
            log.error("unable to load wallet, likely because of an invalid seed, aborting...")
            return StartBusinessResult.Failure.LoadWalletError
        }

        val walletId = WalletId(walletInfo.nodeIdHash)
        val nodeId = walletInfo.nodeId.toHex()
        val globalPrefs = application.globalPrefs
        val walletMetadata = globalPrefs.getAvailableWalletsMeta.first()[walletId] ?: run {
            val metadata = UserWalletMetadata(walletId = walletId, name = null, avatar = WalletAvatars.list.random(), createdAt = currentTimestampMillis())
            globalPrefs.saveAvailableWalletMeta(metadata)
            metadata
        }
        val userPrefs = DataStoreManager.loadUserPrefsForWallet(appContext, walletId)
        val internalPrefs = DataStoreManager.loadInternalPrefsForWallet(appContext, walletId)

        val businessInFlow = businessFlow.value[walletId]
        if (businessInFlow != null) {
            log.info("business already exists in flow, ignoring...")
            return StartBusinessResult.Success(walletInfo, businessInFlow)
        }

        return try {
            log.info("preparing new business with node_id=$nodeId wallet_id=$walletId...")

            // check last used version to display a patch note
            val lastVersionUsed = globalPrefs.getLastUsedAppCode.first()
            if (lastVersionUsed == null) {
                // lastUsedAppCode was added in version 99, and is set up during the wallet creation. So if it's null, this Phoenix was installed prior v99 and we can show a patch note
                globalPrefs.saveShowReleaseNoteSinceCode(98)
            } else if (lastVersionUsed < BuildConfig.VERSION_CODE) {
                globalPrefs.saveShowReleaseNoteSinceCode(lastVersionUsed)
            }

            // update app configuration with user preferences
            business.appConfigurationManager.updateElectrumConfig(userPrefs.getElectrumServer.first())
            business.appConfigurationManager.updatePreferredFiatCurrencies(userPrefs.getFiatCurrencies.first())

            // setup jobs monitoring the business events
            eventsMonitoringJobs[walletId] = listOf(
                scope.launch { monitorPaymentsWhenHeadless(walletId, walletMetadata, business.nodeParamsManager, business.currencyManager, userPrefs) },
                scope.launch { monitorNodeEvents(business.peerManager, business.nodeParamsManager, internalPrefs) },
                scope.launch { monitorFcmToken(business) },
                scope.launch { monitorInFlightPayments(business.peerManager, internalPrefs) },
            )

            // startup params depend user's settings: Tor and liquidity policy
            val startupParams = StartupParams(isTorEnabled = userPrefs.getIsTorEnabled.first(), liquidityPolicy = userPrefs.getLiquidityPolicy.first())
            delay(1_000)

            // actually start the business
            log.info("starting new business with node_id=$nodeId...")
            _businessFlow.value += walletId to business
            business.start(startupParams)

            // the node has been started, so we can now increment the last-used build code
            globalPrefs.saveLastUsedAppCode(BuildConfig.VERSION_CODE)

            // start watching the swap-in wallet
            scope.launch {
                business.peerManager.getPeer().startWatchSwapInWallet()
            }

            log.info("business initialisation has successfully completed")
            StartBusinessResult.Success(walletInfo, business)
        } catch (e: Exception) {
            log.error("there was an error when initialising new business: ", e)
            stopBusiness(walletId)
            StartBusinessResult.Failure.Generic(e)
        }
    }

    fun stopAllBusinesses() {
        log.info("stopping all businesses...")
        businessFlow.value.forEach { (id, business) ->
            business.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
            business.stop()
            eventsMonitoringJobs.remove(id)?.forEach { it.cancel() }
        }
        _businessFlow.value = emptyMap()
    }

    fun stopBusiness(walletId: WalletId) {
        val businessMap = _businessFlow.value.toMutableMap()
        businessMap[walletId]?.let { business ->
            business.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
            business.stop()
        }
        businessMap.remove(walletId)
        _businessFlow.value = businessMap

        eventsMonitoringJobs.remove(walletId)?.forEach { it.cancel() }
    }

    fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                log.warn("fetching FCM registration token failed: ${task.exception?.localizedMessage}")
                return@OnCompleteListener
            }
            task.result?.let { scope.launch { application.globalPrefs.saveFcmToken(it) } }
        })
    }

    private suspend fun monitorFcmToken(business: PhoenixBusiness) {
        val token = application.globalPrefs.getFcmToken.filterNotNull().first()
        business.connectionsManager.connections.first { it.peer == Connection.ESTABLISHED }
        log.info("registering fcm token=$token")
        business.registerFcmToken(token)
    }

    private suspend fun monitorNodeEvents(peerManager: PeerManager, nodeParamsManager: NodeParamsManager, internalPrefs: InternalPrefs) {
        val monitoringStartedAt = currentTimestampMillis()
        combine(peerManager.swapInNextTimeout, nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents) { nextTimeout, nodeEvent ->
            nextTimeout to nodeEvent
        }.collect { (nextTimeout, event) ->
            // TODO: click on notif must deeplink to the notification screen
            when (event) {
                is LiquidityEvents.Rejected -> {
                    log.debug("processing liquidity_event={}", event)
                    if (event.source == LiquidityEvents.Source.OnChainWallet) {
                        // Check the last time a rejected on-chain swap notification has been shown. If recent, we do not want to trigger a notification every time.
                        val lastRejectedSwap = internalPrefs.getLastRejectedOnchainSwap.first().takeIf {
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
                            internalPrefs.saveLastRejectedOnchainSwap(event)
                        }
                    }
                    when (val reason = event.reason) {
                        is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> {
                            SystemNotificationHelper.notifyPaymentRejectedPolicyDisabled(appContext, event.source, event.amount, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee -> {
                            SystemNotificationHelper.notifyPaymentRejectedOverAbsolute(appContext, event.source, event.amount, event.fee, reason.maxAbsoluteFee, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee -> {
                            SystemNotificationHelper.notifyPaymentRejectedOverRelative(appContext, event.source, event.amount, event.fee, reason.maxRelativeFeeBasisPoints, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.MissingOffChainAmountTooLow -> {
                            SystemNotificationHelper.notifyPaymentRejectedAmountTooLow(appContext, event.source, event.amount)
                        }
                        // Temporary errors
                        is LiquidityEvents.Rejected.Reason.ChannelFundingInProgress,
                        is LiquidityEvents.Rejected.Reason.NoMatchingFundingRate,
                        is LiquidityEvents.Rejected.Reason.TooManyParts -> {
                            SystemNotificationHelper.notifyPaymentRejectedFundingError(appContext, event.source, event.amount)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorPaymentsWhenHeadless(walletId: WalletId, walletMetadata: UserWalletMetadata, nodeParamsManager: NodeParamsManager, currencyManager: CurrencyManager, userPrefs: UserPrefs) {
        nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents.collect { event ->
            when (event) {
                is PaymentEvents.PaymentReceived -> {
                    // FIXME handle headless/background behaviour
                    if (isHeadless.value) {
                        SystemNotificationHelper.notifyPaymentsReceived(
                            context = appContext,
                            userPrefs = userPrefs,
                            walletId = walletId,
                            userWalletMetadata = walletMetadata,
                            paymentId = event.payment.id,
                            paymentAmount = event.payment.amountReceived,
                            rates = currencyManager.ratesFlow.value,
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorInFlightPayments(peerManager: PeerManager, internalPrefs: InternalPrefs) {
        peerManager.channelsFlow.filterNotNull().collect {
            val inFlightPaymentsCount = it.inFlightPaymentsCount()
            internalPrefs.saveInFlightPaymentsCount(inFlightPaymentsCount)
            if (inFlightPaymentsCount == 0) {
                InflightPaymentsWatcher.cancel(appContext)
            } else {
                InflightPaymentsWatcher.scheduleOnce(appContext, delay = 2.hours)
            }
        }
    }

    fun clear() {
        supervisor.cancel()
    }
}
