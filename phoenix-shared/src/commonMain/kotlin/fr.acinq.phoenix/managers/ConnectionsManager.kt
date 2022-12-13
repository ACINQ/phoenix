package fr.acinq.phoenix.managers

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.TorHelper.connectionState
import fr.acinq.tor.Tor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import plus

data class Connections(
    val internet: Connection = Connection.CLOSED(reason = null),
    val tor: Connection = Connection.CLOSED(reason = null),
    val peer: Connection = Connection.CLOSED(reason = null),
    val electrum: Connection = Connection.CLOSED(reason = null)
) {
    val global : Connection
        get() = internet + tor + peer + electrum
}

class ConnectionsManager(
    peerManager: PeerManager,
    electrumClient: ElectrumClient,
    networkMonitor: NetworkMonitor,
    appConfigurationManager: AppConfigurationManager,
    tor: Tor
): CoroutineScope {

    constructor(business: PhoenixBusiness): this(
        peerManager = business.peerManager,
        electrumClient = business.electrumClient,
        networkMonitor = business.networkMonitor,
        appConfigurationManager = business.appConfigurationManager,
        tor = business.tor
    )

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val _connections = MutableStateFlow(Connections())
    public val connections: StateFlow<Connections> get() = _connections

    init {
        launch {
            combine(
                peerManager.getPeer().connectionState,
                electrumClient.connectionState,
                networkMonitor.networkState,
                appConfigurationManager.isTorEnabled.filterNotNull(),
                tor.state.connectionState(this)
            ) { peerState, electrumState, internetState, torEnabled, torState ->
                Connections(
                    peer = peerState,
                    electrum = electrumState,
                    internet = when (internetState) {
                        NetworkState.Available -> Connection.ESTABLISHED
                        NetworkState.NotAvailable -> Connection.CLOSED(reason = null)
                    },
                    tor = if (torEnabled) torState else Connection.CLOSED(reason = null)
                )
            }.collect {
                _connections.value = it
            }
        }
    }
}