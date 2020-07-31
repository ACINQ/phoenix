package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.*
import fr.acinq.eklair.utils.msat
import fr.acinq.eklair.utils.secure
import fr.acinq.phoenix.ctrl.Receive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(override val di: DI) : AppController<Receive.Model, Receive.Intent>(Receive.Model.Awaiting) {

    val preimage = ByteVector32(Random.secure().nextBytes(32))

    val peer: Peer by instance()

    init {
        launch {
            peer.openConnectedSubscription().consumeEach {
                if (!it) model(Receive.Model.Disconnected)
            }
        }

        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentRequestGenerated -> {
                        if (it.receivePayment.paymentPreimage == preimage) {
                            model(Receive.Model.Generated(it.request))
                        }
                    }
                    is PaymentReceived -> {
                        if (it.receivePayment.paymentPreimage == preimage) {
                            model(Receive.Model.Received(it.receivePayment.amount.toLong()))
                        }
                    }
                }
            }
        }
    }

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                require(lastModel == Receive.Model.Awaiting)
                launch {
                    peer.send(ReceivePayment(preimage, intent.amountMsat.msat, CltvExpiry(100)))
                }
            }
        }
    }

}