package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.io.PaymentRequestGenerated
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.ReceivePayment
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.ctrl.Receive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(di: DI) : AppController<Receive.Model, Receive.Intent>(di, Receive.Model.Awaiting) {

    val preimage = ByteVector32(Random.secure().nextBytes(32))

    val peer: Peer by instance()

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentRequestGenerated -> {
                        if (it.receivePayment.paymentPreimage == preimage) {
                            model { Receive.Model.Generated(it.request) }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model {
                        require(this == Receive.Model.Awaiting)
                        Receive.Model.Generating
                    }
                    peer.send(ReceivePayment(preimage, intent.amountMsat.msat, CltvExpiry(100), "Phoenix payment"))
                }
            }
        }
    }

}