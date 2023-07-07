package fr.acinq.phoenix.android.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalData
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

class FCMService : FirebaseMessagingService() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.debug(
            "received fcm message" +
                    "\n    data=${remoteMessage.data}" +
                    "\n    prio=${remoteMessage.priority}" +
                    "\n    original_prio=${remoteMessage.originalPriority} was_degraded ? ${remoteMessage.priority < remoteMessage.originalPriority}" +
                    "\n    type=${remoteMessage.messageType}" +
                    "\n    notif_title=${remoteMessage.notification?.title}" +
                    "\n    notif_body=${remoteMessage.notification?.body}" +
                    "\n    notif_prio=${remoteMessage.notification?.notificationPriority}" +
                    "\n    notif_channel=${remoteMessage.notification?.channelId}" +
                    "\n    from=${remoteMessage.from}"
        )

        val reason = remoteMessage.data["reason"]
        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)

        when {
            encryptedSeed !is EncryptedSeed.V2.NoAuth -> {
                log.warn("ignored fcm message with unhandled seed=${encryptedSeed?.name()}")
            }
            remoteMessage.priority != RemoteMessage.PRIORITY_HIGH -> {
                // cannot start foreground service from low/normal priority message
                log.warn("ignoring FCM message with low/normal priority, show notification with reason=$reason")
                when (reason) {
                    "IncomingPayment" -> SystemNotificationHelper.notifyPaymentMissedAppUnavailable(applicationContext)
                    "PendingSettlement" -> SystemNotificationHelper.notifyPendingSettlement(applicationContext)
                    else -> {}
                }
            }
            else -> {
                log.info("starting phoenix foreground service with reason=$reason")
                startPhoenixForegroundService(reason)
            }
        }
    }

    private fun startPhoenixForegroundService(reason: String?) {
        ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, NodeService::class.java)
            .apply { reason?.let { putExtra(NodeService.EXTRA_REASON, it) } })
    }

    override fun onNewToken(token: String) {
        serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.warn("failed to save fcm token after onNewToken event: {}", e.localizedMessage)
        }) {
            InternalData.saveFcmToken(applicationContext, token)
        }
    }
}