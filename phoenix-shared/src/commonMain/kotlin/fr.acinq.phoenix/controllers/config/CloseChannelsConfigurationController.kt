package fr.acinq.phoenix.controllers.config

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.io.WrappedChannelCommand
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.localBalance
import fr.acinq.phoenix.utils.loggerExtensions.*
import kotlinx.coroutines.launch

class AppCloseChannelsConfigurationController(
    loggerFactory: Logger,
    private val peerManager: PeerManager,
    private val chain: NodeParams.Chain,
    private val isForceClose: Boolean
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = CloseChannelsConfiguration.Model.Loading
) {
    constructor(business: PhoenixBusiness, isForceClose: Boolean): this(
        loggerFactory = business.newLoggerFactory,
        peerManager = business.peerManager,
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
                    val address = peer.finalAddress
                    model(CloseChannelsConfiguration.Model.Ready(
                        channels = closableChannelsList,
                        address = address
                    ))
                }
            }
        }
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        var scriptPubKey : ByteArray? = null
        if (intent is CloseChannelsConfiguration.Intent.MutualCloseAllChannels) {
            scriptPubKey = Parser.addressToPublicKeyScript(chain, intent.address)
            if (scriptPubKey == null) {
                throw IllegalArgumentException(
                    "Address is invalid. Caller MUST validate user input via parseBitcoinAddress"
                )
            }
        }

        launch {
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                isClosable(it.value)
            }

            closingChannelIds = closingChannelIds?.plus(filteredChannels.keys) ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                val command: ChannelCommand = if (scriptPubKey != null) {
                    logger.info { "(mutual) closing channel=${channelId.toHex()}" }
                    ChannelCommand.Close.MutualClose(scriptPubKey = ByteVector(scriptPubKey), feerates = null)
                } else {
                    logger.info { "(force) closing channel=${channelId.toHex()}" }
                    ChannelCommand.Close.ForceClose
                }
                val peerEvent = WrappedChannelCommand(channelId, command)
                peer.send(peerEvent)
            }
        }
    }
}
