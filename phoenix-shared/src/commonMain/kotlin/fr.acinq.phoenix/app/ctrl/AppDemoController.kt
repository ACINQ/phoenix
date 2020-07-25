package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.CltvExpiry
import fr.acinq.eklair.Peer
import fr.acinq.eklair.ReceivePayment
import fr.acinq.eklair.utils.secure
import fr.acinq.phoenix.ctrl.Demo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AppDemoController(override val di: DI) : AppController<Demo.Model, Demo.Intent>() {
    private val peer: Peer by instance()

    override fun process(intent: Demo.Intent) {
        when (intent) {
            is Demo.Intent.Connect -> {
                launch {
                    peer.connect("localhost", 48001) // TODO: Only for demo
                }
            }
            is Demo.Intent.Receive -> {
                val preimage = ByteVector32(Random.secure().nextBytes(32))
                launch {
                    peer.input.send(ReceivePayment(preimage, intent.amount, CltvExpiry(100)))
                }
            }
        }
    }

}
