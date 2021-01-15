package fr.acinq.phoenix.utils

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import plus

data class Connections(
    val internet: Connection = Connection.CLOSED,
    val peer: Connection = Connection.CLOSED,
    val electrum: Connection = Connection.CLOSED
) {
    val global : Connection
        get() = internet + peer + electrum
}

@ExperimentalCoroutinesApi
class ConnectionsMonitor(peer: Peer, electrumClient: ElectrumClient, networkMonitor: NetworkMonitor): CoroutineScope {

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val _connections = MutableStateFlow<Connections>(Connections())
    public val connections: StateFlow<Connections> = _connections

    init {
        launch {
            combine(
                peer.connectionState,
                electrumClient.connectionState,
                networkMonitor.networkState
            ) { peerState, electrumState, internetState ->
                Connections(
                    peer = peerState,
                    electrum = electrumState,
                    internet = when (internetState) {
                        NetworkState.Available -> Connection.ESTABLISHED
                        NetworkState.NotAvailable -> Connection.CLOSED
                    }
                )
            }.collect {
                _connections.value = it
            }
        }
    }
}