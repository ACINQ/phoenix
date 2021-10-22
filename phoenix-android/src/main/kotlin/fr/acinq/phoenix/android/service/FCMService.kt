package fr.acinq.phoenix.android.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import org.slf4j.LoggerFactory

class FCMService : FirebaseMessagingService() {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.info("received fcm message data=${remoteMessage.data} type=${remoteMessage.messageType} notif=${remoteMessage.notification} from=${remoteMessage.from}")

        val reason = remoteMessage.data["reason"]
        when (val seed = SeedManager.loadSeedFromDisk(applicationContext)) {
            is EncryptedSeed.V2.NoAuth -> {
                log.info("start foreground service")
                ContextCompat.startForegroundService(applicationContext,
                    Intent(applicationContext, NodeService::class.java).apply { reason?.let { putExtra(NodeService.EXTRA_REASON, it) } })
            }
            null -> {
                log.info("ignored fcm message with reason=$reason: no seed found")
            }
            else -> {
                log.info("ignored fcm message with reason=$reason: unhandled seed type=${seed.name()}")
            }
        }
    }

    override fun onNewToken(token: String) {

    }
}