package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.channel.NewBlock
import fr.acinq.eklair.io.*
import fr.acinq.eklair.payment.PaymentRequest
import fr.acinq.eklair.utils.UUID
import fr.acinq.phoenix.ctrl.Scan
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class AppScanController(di: DI) : AppController<Scan.Model, Scan.Intent>(di, Scan.Model.NeedHeight) {

    private val peer: Peer by instance()

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Height -> {
                launch {
                    peer.send(WrappedChannelEvent(ByteVector32.Zeroes, NewBlock(intent.height, null)))
                    model(Scan.Model.Ready)
                }
            }
            is Scan.Intent.Parse -> {
                launch {
                    val paymentRequest = PaymentRequest.read(intent.request)
                    model(Scan.Model.Validate(intent.request, paymentRequest.amount?.toLong(), paymentRequest.description))
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    val paymentRequest = PaymentRequest.read(intent.request)
                    val uuid = UUID.randomUUID()

                    launch {
                        val sent = peer.openListenerEventSubscription().consumeAsFlow()
                            .filterIsInstance<PaymentSent>()
                            .first { it.id == uuid }
                        model(Scan.Model.Fulfilled(sent.recipientAmount.toLong(), paymentRequest.description))
                    }

                    model(Scan.Model.Sending(paymentRequest.amount?.toLong() ?: intent.amountMsat, paymentRequest.description))
                    peer.send(SendPayment(uuid, paymentRequest))
                }
            }
        }
    }
}