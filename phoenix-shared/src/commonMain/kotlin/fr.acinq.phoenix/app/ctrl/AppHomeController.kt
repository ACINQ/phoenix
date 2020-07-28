package fr.acinq.phoenix.app.ctrl

import fr.acinq.eklair.Peer
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(override val di: DI) : AppController<Home.Model, Home.Intent>(Home.emptyModel) {
    private val peer: Peer by instance()

    init {
        launch {
            peer.openStateSubscription().consumeEach { state ->
                model(Home.Model(
                    state.connected,
                    state.channels.map {
                        Home.Model.Channel(
                            cid = Hex.encode(it.key.toByteArray()),
                            local = it.value.commitments.localCommit.spec.toLocal.truncateToSatoshi().toLong(),
                            remote = it.value.commitments.localCommit.spec.toRemote.truncateToSatoshi().toLong()
                        )
                    }
                ))
            }
        }
    }

    override fun process(intent: Home.Intent) {
        when (intent) {
            is Home.Intent.Connect -> {
                launch {
                    peer.connect("localhost", 48001) // TODO: Only for demo
                }
            }
        }
    }

}
