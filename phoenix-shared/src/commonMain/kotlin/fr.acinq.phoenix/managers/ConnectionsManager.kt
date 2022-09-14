package fr.acinq.phoenix.managers

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import plus

data class Connections(
    val internet: Connection = Connection.CLOSED(reason = null),
    val peer: Connection = Connection.CLOSED(reason = null),
    val electrum: Connection = Connection.CLOSED(reason = null)
) {
    val global : Connection
        get() = internet + peer + electrum
}

class ConnectionsManager(
    peerManager: PeerManager,
    electrumClient: ElectrumClient,
    networkMonitor: NetworkMonitor
): CoroutineScope {

    constructor(business: PhoenixBusiness): this(
        peerManager = business.peerManager,
        electrumClient = business.electrumClient,
        networkMonitor = business.networkMonitor
    )

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val _connections = MutableStateFlow<Connections>(Connections())
    public val connections: StateFlow<Connections> = _connections

    init {
        launch {
            combine(
                peerManager.getPeer().connectionState,
                electrumClient.connectionState,
                networkMonitor.networkState
            ) { peerState, electrumState, internetState ->
                Connections(
                    peer = peerState,
                    electrum = electrumState,
                    internet = when (internetState) {
                        NetworkState.Available -> Connection.ESTABLISHED
                        NetworkState.NotAvailable -> Connection.CLOSED(reason = null)
                    }
                )
            }.collect {
                _connections.value = it
            }
        }
    }
}