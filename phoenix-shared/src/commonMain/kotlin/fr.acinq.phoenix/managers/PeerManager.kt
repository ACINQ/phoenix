package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.wire.InitTlv
import fr.acinq.lightning.wire.TlvStream
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
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
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        nodeParamsManager = business.nodeParamsManager,
        databaseManager = business.databaseManager,
        configurationManager = business.appConfigurationManager,
        electrumWatcher = business.electrumWatcher
    )

    private val logger = newLogger(loggerFactory)

    private val _peer = MutableStateFlow<Peer?>(null)
    val peerState: StateFlow<Peer?> = _peer

    /**
     * Our local view of our channels. It is initialized with data from the local db, then with the actual
     * channels once they have been reestablished.
     */
    private val _channelsFlow = MutableStateFlow<Map<ByteVector32, LocalChannelInfo>?>(null)
    val channelsFlow: StateFlow<Map<ByteVector32, LocalChannelInfo>?> = _channelsFlow

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
                nodeParams = nodeParams,
                walletParams = walletParams,
                watcher = electrumWatcher,
                db = databaseManager.databases.filterNotNull().first(),
                socketBuilder = null,
                scope = MainScope()
            )
            _peer.value = peer

            // The local channels flow must use `bootFlow` first, as `channelsFlow` is empty when the wallet starts.
            // `bootFlow` data come from the local database and will be overridden by fresh data once the connection
            // with the peer has been established.
            val bootFlow = peer.bootChannelsFlow.filterNotNull()
            val channelsFlow = peer.channelsFlow
            var isBoot = true
            combine(bootFlow, channelsFlow) { bootChannels, channels ->
                // bootFlow will fire once, after the channels have been read from the database.
                if (isBoot) {
                    isBoot = false
                    bootChannels.entries.associate { it.key to LocalChannelInfo(it.key.toHex(), it.value, isBooting = true) }
                } else {
                    channels.entries.associate { it.key to LocalChannelInfo(it.key.toHex(),it.value, isBooting = false) }
                }
            }.collect {
                _channelsFlow.value = it
            }
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()

    /**
     * Returns the underlying channel, if it's of type ChannelStateWithCommitments.
     * Note that Offline channels are automatically unwrapped.
     */
    fun getChannelWithCommitments(channelId: ByteVector32): ChannelStateWithCommitments? {
        val peer = peerState.value ?: return null
        var channel = peer.channels[channelId] ?: return null
        channel = when (channel) {
            is Offline -> channel.state
            else -> channel
        }
        return when (channel) {
            is ChannelStateWithCommitments -> channel
            else -> null
        }
    }
}
