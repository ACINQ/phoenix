package fr.acinq.phoenix.app.ctrl

import fr.acinq.eklair.channel.HasCommitments
import fr.acinq.eklair.io.Peer
import fr.acinq.phoenix.ctrl.Home
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
            peer.openConnectedSubscription().consumeEach {
                model(lastModel.copy(connected = it))
            }
        }

        launch {
            peer.openChannelsSubscription().consumeEach { channels ->
                model(
                    lastModel.copy(
                        channels = channels
                            .mapNotNull { (id, state) ->
                            (state as? HasCommitments)?.let {
                                Home.Model.Channel(
                                    cid = id.toHex(),
                                    local = it.commitments.localCommit.spec.toLocal.truncateToSatoshi().toLong(),
                                    remote = it.commitments.localCommit.spec.toRemote.truncateToSatoshi().toLong(),
                                    state = it::class.simpleName ?: "[Unknown]"
                                )
                            }
                        }
                    )
                )
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
