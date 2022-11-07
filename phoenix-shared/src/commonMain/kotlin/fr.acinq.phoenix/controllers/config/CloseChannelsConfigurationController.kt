package fr.acinq.phoenix.controllers.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.io.WrappedChannelCommand
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.localCommitmentSpec
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
    private val chain: Chain,
    private val isForceClose: Boolean
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = CloseChannelsConfiguration.Model.Loading
) {
    constructor(business: PhoenixBusiness, isForceClose: Boolean): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        walletManager = business.walletManager,
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
                            balance = sats(it.value),
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

    private fun sats(channel: ChannelState): Long {
        return channel.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
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

            closingChannelIds = closingChannelIds?.let {
                it.plus(filteredChannels.keys)
            } ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                val command: CloseCommand = if (scriptPubKey != null) {
                    CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey), feerates = null)
                } else {
                    CMD_FORCECLOSE
                }
                val channelEvent = ChannelCommand.ExecuteCommand(command)
                val peerEvent = WrappedChannelCommand(channelId, channelEvent)
                peer.send(peerEvent)
            }
        }
    }
}
