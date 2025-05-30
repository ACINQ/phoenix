package fr.acinq.phoenix.controllers.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.io.WrappedChannelCommand
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.localBalance
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.phoenixFinalWallet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val appConfigurationManager: AppConfigurationManager,
    private val chain: Chain,
    private val isForceClose: Boolean
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = CloseChannelsConfiguration.Model.Loading
) {
    constructor(business: PhoenixBusiness, isForceClose: Boolean): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        appConfigurationManager = business.appConfigurationManager,
        chain = business.chain,
        isForceClose = isForceClose
    )

    private var closingChannelIds: Set<ByteVector32>? = null

    private fun channelInfoStatus(channel: ChannelState): ChannelInfoStatus? = when (channel) {
        is Normal -> ChannelInfoStatus.Normal
        is Offline -> ChannelInfoStatus.Offline
        is Syncing -> ChannelInfoStatus.Syncing
        is Closing -> ChannelInfoStatus.Closing
        is Closed -> ChannelInfoStatus.Closed
        is Aborted -> ChannelInfoStatus.Aborted
        else -> null
    }

    private fun isMutualClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        else -> false
    }

    private fun isForceClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        ChannelInfoStatus.Offline -> true
        ChannelInfoStatus.Syncing -> true
        else -> false
    }

    private fun isClosable(channelInfoStatus: ChannelInfoStatus): Boolean = if (isForceClose) {
        isForceClosable(channelInfoStatus)
    } else {
        isMutualClosable(channelInfoStatus)
    }

    private fun isClosable(channel: ChannelState): Boolean = channelInfoStatus(channel)?.let {
        isClosable(it)
    } ?: false

    init {
        launch {
            val peer = peerManager.getPeer()
            peer.channelsFlow.collect { channels ->

                val closingChannelIdsCopy = closingChannelIds?.toSet()

                val updatedChannelsList = channels.filter {
                    closingChannelIdsCopy?.contains(it.key) ?: true
                }.mapNotNull {
                    channelInfoStatus(it.value)?.let { mappedStatus ->
                        CloseChannelsConfiguration.Model.ChannelInfo(
                            id = it.key,
                            balance = it.value.localBalance()?.truncateToSatoshi(),
                            status = mappedStatus
                        )
                    }
                }

                if (closingChannelIdsCopy != null) {
                    model(CloseChannelsConfiguration.Model.ChannelsClosed(
                        channels = updatedChannelsList,
                        closing = closingChannelIdsCopy
                    ))
                } else {
                    val closableChannelsList = updatedChannelsList.filter {
                        isClosable(it.status)
                    }
                    val address = peer.phoenixFinalWallet.finalAddress
                    model(CloseChannelsConfiguration.Model.Ready(
                        channels = closableChannelsList,
                        address = address
                    ))
                }
            }
        }
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        when (intent) {
            is CloseChannelsConfiguration.Intent.MutualCloseAllChannels -> process_mutualClose(intent)
            is CloseChannelsConfiguration.Intent.ForceCloseAllChannels -> process_forceClose(intent)
        }
    }

    fun process_mutualClose(intent: CloseChannelsConfiguration.Intent.MutualCloseAllChannels) {
        val scriptPubKey: ByteVector?
        try {
            scriptPubKey = Parser.parseBip21Uri(chain, intent.address).right!!.script
        } catch (e: Exception) {
            throw IllegalArgumentException("Address is invalid. Caller MUST validate user input via readBitcoinAddress")
        }

        launch {
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                isClosable(it.value)
            }

            closingChannelIds = closingChannelIds?.plus(filteredChannels.keys) ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                logger.info { "(mutual) closing channel=${channelId.toHex()} with feerate=${FeeratePerByte(intent.feerate)}" }
                val command = ChannelCommand.Close.MutualClose(
                    replyTo = CompletableDeferred(),
                    scriptPubKey = scriptPubKey,
                    feerate = intent.feerate
                )
                val peerEvent = WrappedChannelCommand(channelId, command)
                peer.send(peerEvent)
            }
        }
    }

    fun process_forceClose(intent: CloseChannelsConfiguration.Intent.ForceCloseAllChannels) {
        launch {
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                isClosable(it.value)
            }

            closingChannelIds = closingChannelIds?.plus(filteredChannels.keys) ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                logger.info { "(force) closing channel=${channelId.toHex()}" }
                val command = ChannelCommand.Close.ForceClose
                val peerEvent = WrappedChannelCommand(channelId, command)
                peer.send(peerEvent)
            }
        }
    }
}
