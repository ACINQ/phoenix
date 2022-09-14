package fr.acinq.phoenix.managers

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.wire.InitTlv
import fr.acinq.lightning.wire.TlvStream
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.calculateBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val databaseManager: DatabaseManager,
    private val configurationManager: AppConfigurationManager,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        nodeParamsManager = business.nodeParamsManager,
        databaseManager = business.databaseManager,
        configurationManager = business.appConfigurationManager,
        tcpSocketBuilder = business.tcpSocketBuilder,
        electrumWatcher = business.electrumWatcher
    )

    private val logger = newLogger(loggerFactory)

    private val _peer = MutableStateFlow<Peer?>(null)
    val peerState: StateFlow<Peer?> = _peer

    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    init {
        launch {
            val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
            val walletParams = configurationManager.chainContext.filterNotNull().first().walletParams()
            val startupParams = configurationManager.startupParams.filterNotNull().first()

            var initTlvs = TlvStream.empty<InitTlv>()
            if (startupParams.requestCheckLegacyChannels) {
                val legacyKey = nodeParams.keyManager.legacyNodeKey
                val signature = Crypto.sign(
                    data = Crypto.sha256(legacyKey.publicKey.toUncompressedBin()),
                    privateKey = legacyKey.privateKey
                )
                initTlvs = initTlvs.addOrUpdate(InitTlv.PhoenixAndroidLegacyNodeId(legacyNodeId = legacyKey.publicKey, signature = signature))
            }

            logger.debug { "instantiating peer with nodeParams=$nodeParams walletParams=$walletParams initTlvs=$initTlvs" }

            val peer = Peer(
                initTlvStream = initTlvs,
                nodeParams = nodeParamsManager.nodeParams.filterNotNull().first(),
                walletParams = configurationManager.chainContext.filterNotNull().first().walletParams(),
                watcher = electrumWatcher,
                db = databaseManager.databases.filterNotNull().first(),
                socketBuilder = tcpSocketBuilder,
                scope = MainScope()
            )
            _peer.value = peer
            peer.channelsFlow.collect { channels ->
                _balance.value = calculateBalance(channels)
            }
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()
}