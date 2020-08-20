package fr.acinq.phoenix.app

import fr.acinq.eklair.io.Peer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


@OptIn(ExperimentalCoroutinesApi::class)
class Daemon(override val di: DI) : DIAware {

    private val peer: Peer by instance()

    init {
        MainScope().launch { connectionDaemon() }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun connectionDaemon() {
        peer.openConnectedSubscription().consumeEach {
            if (it == Peer.Connection.CLOSED) {
                delay(2.seconds)
                peer.connect("localhost", 48001)
            }
        }
    }

}
