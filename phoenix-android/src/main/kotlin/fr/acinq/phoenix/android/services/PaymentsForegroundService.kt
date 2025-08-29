package fr.acinq.phoenix.android.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes


/**
 * This foreground service starts a [PhoenixBusiness] upon receiving FCM payment messages from the LSP. Being a foreground
 * service, it needs to display a foreground notification visible by the user.
 *
 * This service allows Phoenix to receive payments (or settle pending payments) even when it's closed or in the background.
 */
class PaymentsForegroundService : Service() {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var notificationManager: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        log.debug("creating node service...")
        notificationManager = NotificationManagerCompat.from(this)
        log.debug("service created")
    }

    override fun onBind(intent: Intent?) = null

    private val shutdownHandler = Handler(Looper.getMainLooper())
    private val shutdownRunnable: Runnable = Runnable {
        if (BusinessManager.isHeadless.value) {
            log.debug("reached scheduled shutdown while headless")
            if (BusinessManager.receivedInTheBackground.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            BusinessManager.stopAllBusinesses()
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log.info("start service from intent [ intent=$intent, flag=$flags, startId=$startId ]")

        val reason = intent?.getStringExtra(EXTRA_REASON)
        val walletId = intent?.getStringExtra(EXTRA_NODE_ID_HASH)?.let { WalletId(it) }

        val businessMap = BusinessManager.businessFlow.value

        // the notification for the foreground service depends on whether the business is started or not.
        val notif = when {

            !BusinessManager.isHeadless.value -> {
                log.info("app is not headless, ignoring background message (reason=$reason)")
                SystemNotificationHelper.notifyRunningHeadless(applicationContext)
            }

            walletId != null && businessMap[walletId] != null -> {
                log.info("active business found for wallet=$walletId, ignoring background message (reason=$reason)")
                businessMap[walletId]?.let {
                    if (it.connectionsManager.connections.value.peer !is Connection.ESTABLISHED) {
                        it.appConnectionsDaemon?.forceReconnect(AppConnectionsDaemon.ControlTarget.Peer)
                    }
                }
                SystemNotificationHelper.notifyRunningHeadless(applicationContext)
            }

            walletId == null -> {
                log.info("no wallet_id provided, ignoring background message (reason=$reason)")
                SystemNotificationHelper.notifyRunningHeadless(applicationContext)
            }

            else -> {
                when (val result = SeedManager.loadAndDecrypt(applicationContext)) {
                    is DecryptSeedResult.Failure.SeedFileNotFound -> {
                        log.info("seed not found, ignoring background message (reason=$reason)")
                        SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                    }
                    is DecryptSeedResult.Failure -> {
                        log.info("unable to read seed, ignoring background message (reason=$reason)")
                        when (reason) {
                            "IncomingPayment" -> SystemNotificationHelper.notifyPaymentMissedAppUnavailable(applicationContext)
                            "PendingSettlement" -> SystemNotificationHelper.notifyPendingSettlement(applicationContext)
                            else -> SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                        }
                    }
                    is DecryptSeedResult.Success -> {
                        val mnemonicsMap = result.mnemonicsMap
                        val match = mnemonicsMap[walletId]
                        if (match.isNullOrEmpty()) {
                            log.info("seed not found for node_id=$walletId, ignoring background message (reason=$reason)")
                            SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                        } else {
                            serviceScope.launch(Dispatchers.Default) {
                                when (val res = BusinessManager.startNewBusiness(match, isHeadless = true)) {
                                    is StartBusinessResult.Failure -> {
                                        log.error("error when starting wallet=$walletId... from foreground service: $res")
                                        stopForeground(STOP_FOREGROUND_REMOVE)
                                        stopSelf()
                                    }
                                    is StartBusinessResult.Success -> Unit
                                }
                            }
                            SystemNotificationHelper.notifyRunningHeadless(applicationContext)
                        }
                    }
                }
            }
        }

        // service will automatically shutdown after a while
        shutdownHandler.removeCallbacksAndMessages(null)
        shutdownHandler.postDelayed(shutdownRunnable, 2.minutes.inWholeMilliseconds)

        // show a notification -- this is mandatory!
        startForeground(notif)
        return START_NOT_STICKY
    }

    private fun startForeground(notif: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, SystemNotificationHelper.HEADLESS_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(SystemNotificationHelper.HEADLESS_NOTIF_ID, notif)
        }
    }

    companion object {
        const val EXTRA_REASON = "${BuildConfig.APPLICATION_ID}.FCM_MESSAGE.REASON"
        const val EXTRA_NODE_ID_HASH = "${BuildConfig.APPLICATION_ID}.FCM_MESSAGE.NODE_ID_HASH"
    }
}