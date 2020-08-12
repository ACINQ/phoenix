package fr.acinq.phoenix.app

import fr.acinq.eklair.io.Peer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance


@OptIn(ExperimentalCoroutinesApi::class)
class Daemon(override val di: DI) : DIAware {

    private val peer: Peer by instance()

    init {
        MainScope().launch { connectionDaemon() }
    }

    private suspend fun connectionDaemon() {
        peer.openConnectedSubscription().consumeEach {
            if (it == Peer.Connection.CLOSED) {
                peer.connect("localhost", 48001)
            }
        }
    }

}
