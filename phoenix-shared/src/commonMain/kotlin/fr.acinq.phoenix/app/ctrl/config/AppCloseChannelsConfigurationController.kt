package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.io.WrappedChannelEvent
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
    private val chain: Chain,
    private val util: Utilities,
    private val isForceClose: Boolean
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory,
    CloseChannelsConfiguration.Model.Loading
) {
    var closingChannelIds: Set<ByteVector32>? = null

    fun channelInfoStatus(channel: ChannelState): ChannelInfoStatus? = when (channel) {
        is Normal -> ChannelInfoStatus.Normal
        is Offline -> ChannelInfoStatus.Offline
        is Syncing -> ChannelInfoStatus.Syncing
        is Closing -> ChannelInfoStatus.Closing
        is Closed -> ChannelInfoStatus.Closed
        is Aborted -> ChannelInfoStatus.Aborted
        else -> null
    }

    fun isMutualClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        else -> false
    }

    fun isForceClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        ChannelInfoStatus.Offline -> true
        ChannelInfoStatus.Syncing -> true
        else -> false
    }

    fun isClosable(channelInfoStatus: ChannelInfoStatus): Boolean = if (isForceClose) {
        isForceClosable(channelInfoStatus)
    } else {
        isMutualClosable(channelInfoStatus)
    }

    fun isClosable(channel: ChannelState): Boolean = channelInfoStatus(channel)?.let {
        isClosable(it)
    } ?: false

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->

                val updatedChannelsList = channels.filter {
                    closingChannelIds?.let { set ->
                        set.contains(it.key)
                    } ?: true
                }.mapNotNull {
                    channelInfoStatus(it.value)?.let { mappedStatus ->
                        CloseChannelsConfiguration.Model.ChannelInfo(
                            id = it.key,
                            balance = sats(it.value),
                            status = mappedStatus
                        )
                    }
                }

                if (closingChannelIds != null) {
                    model(CloseChannelsConfiguration.Model.ChannelsClosed(updatedChannelsList))
                } else {
                    val closableChannelsList = updatedChannelsList.filter {
                        isClosable(it.status)
                    }

                    val path = when (chain) {
                        Chain.Mainnet -> "m/84'/0'/0'/0/0"
                        else -> "m/84'/1'/0'/0/0"
                    }
                    val wallet = walletManager.wallet.value!!
                    val address = wallet.onchainAddress(
                        path = path,
                        isMainnet = chain.isMainnet()
                    )

                    model(CloseChannelsConfiguration.Model.Ready(closableChannelsList, address))
                }
            }
        }
    }

    fun sats(channel: ChannelState): Long {
        return channel.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        var scriptPubKey : ByteArray? = null
        if (intent is CloseChannelsConfiguration.Intent.MutualCloseAllChannels) {
            scriptPubKey = util.addressToPublicKeyScript(address = intent.address)
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
                    CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey))
                } else {
                    CMD_FORCECLOSE
                }
                val channelEvent = ChannelEvent.ExecuteCommand(command)
                val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                peer.send(peerEvent)
            }
        }
    }
}
