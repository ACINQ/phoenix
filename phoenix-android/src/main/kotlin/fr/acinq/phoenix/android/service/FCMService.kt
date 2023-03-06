package fr.acinq.phoenix.android.service

import android.content.Intent
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

class FCMService : FirebaseMessagingService() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.debug("received fcm message" +
                "\n    data=${remoteMessage.data}" +
                "\n    prio=${remoteMessage.priority}" +
                "\n    original_prio=${remoteMessage.originalPriority} was_degraded ? ${remoteMessage.priority < remoteMessage.originalPriority}" +
                "\n    type=${remoteMessage.messageType}" +
                "\n    notif_title=${remoteMessage.notification?.title}" +
                "\n    notif_body=${remoteMessage.notification?.body}" +
                "\n    notif_prio=${remoteMessage.notification?.notificationPriority}" +
                "\n    notif_channel=${remoteMessage.notification?.channelId}" +
                "\n    from=${remoteMessage.from}")

        // message type is contained in the data payload
        val dataType = remoteMessage.data["t"]?.trim()
        when {
            remoteMessage.priority != RemoteMessage.PRIORITY_HIGH -> {
                log.debug("received FCM message with low/normal priority, abort processing and display notif")
            }
            // data is an invoice request from SNS
            dataType?.equals("invoice", true) == true -> {
                handleInvoiceRequestNotif(
                    priority = remoteMessage.priority,
                    amount = remoteMessage.data["amt"]!!.toLong().msat,
                    descriptionHash = Hex.decode(remoteMessage.data["h"]!!).byteVector32(),
                    nodeId = PublicKey.fromHex(remoteMessage.data["n"]!!)
                )
            }
            else -> {
                handleIncomingPaymentNotif(
                    priority = remoteMessage.priority,
                    reason = remoteMessage.data["reason"]
                )
            }
        }
    }

    private fun handleInvoiceRequestNotif(
        priority: Int,
        amount: MilliSatoshi,
        nodeId: PublicKey,
        descriptionHash: ByteVector32,
    ) {
        val business = (applicationContext as PhoenixApplication).business
        serviceScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to get invoice: ", e)
        }) {
            handleIncomingPaymentNotif(priority, "InvoiceRequest")
            val invoice = business.paymentsManager.generateInvoice(
                amount, descriptionHash, expirySeconds = 7 * DateUtils.DAY_IN_MILLIS / 1000
            )
            business.connectionsManager.connections.collect {
                if (it.peer == Connection.ESTABLISHED) {
                    log.debug("peer connected, forwarding invoice to service")
                    business.lnurlManager.sendInvoiceToLnid(
                        nodeId, amount, PaymentRequest.read(invoice)
                    )
                }
            }
        }
    }

    private fun handleIncomingPaymentNotif(priority: Int, reason: String?) {
        when (val seed = SeedManager.loadSeedFromDisk(applicationContext)) {
            is EncryptedSeed.V2.NoAuth -> {
                log.info("start foreground service")
                ContextCompat.startForegroundService(applicationContext,
                    Intent(applicationContext, NodeService::class.java).apply { reason?.let { putExtra(NodeService.EXTRA_REASON, it) } })
            }
            null -> {
                log.info("ignored incoming payment notif message: no seed found")
            }
            else -> {
                log.info("ignored incoming payment notif: unhandled seed type=${seed.name()}")
            }
        }
    }

    override fun onNewToken(token: String) {
        serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.warn("failed to save fcm token after onNewToken event: {}", e.localizedMessage)
        }) {
            InternalData.saveFcmToken(applicationContext, token)
        }
    }
}