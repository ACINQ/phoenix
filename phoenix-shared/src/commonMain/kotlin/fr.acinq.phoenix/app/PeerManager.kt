package fr.acinq.phoenix.app

import fr.acinq.eclair.NodeParams
import fr.acinq.eclair.WalletParams
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.db.Databases
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val configurationManager: AppConfigurationManager,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    private val _peer = MutableStateFlow<Peer?>(null)
    public val peerState: StateFlow<Peer?> = _peer

    init {
        launch {
            _peer.value = buildPeer(
                nodeParams = nodeParamsManager.nodeParams.filterNotNull().first(),
                walletParams = configurationManager.walletParams.filterNotNull().first(),
                databases = nodeParamsManager.databases.filterNotNull().first(),
            )
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()

    private fun buildPeer(nodeParams: NodeParams, walletParams: WalletParams, databases: Databases): Peer {
        return Peer(nodeParams, walletParams, electrumWatcher, databases, tcpSocketBuilder, MainScope())
    }
}