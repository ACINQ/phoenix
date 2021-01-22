package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.io.WrappedChannelEvent
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val util: Utilities
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory,
    CloseChannelsConfiguration.Model.Loading
) {
    var isClosing = false

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                if (!isClosing) {
                    val filteredChannels = filteredChannels(channels)
                    val sats = totalSats(filteredChannels)
                    model(
                        CloseChannelsConfiguration.Model.Ready(
                            channelCount = filteredChannels.size,
                            sats = sats
                        )
                    )
                }
            }
        }
    }

    fun filteredChannels(channels: Map<ByteVector32, ChannelState>): Map<ByteVector32, ChannelState> {
        return channels.filter {
            when (it.value) {
                is Closing -> false
                is Closed -> false
                is Aborted -> false
                else -> true
            }
        }
    }

    fun totalSats(channels: Map<ByteVector32, ChannelState>): Long {
        return channels.values.sumOf {
            it.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
        }
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        when (intent) {
            is CloseChannelsConfiguration.Intent.CloseAllChannels -> {
                val scriptPubKey = util.addressToPublicKeyScript(address = intent.address)
                if (scriptPubKey == null) {
                    throw IllegalArgumentException(
                        "Address is invalid. Caller MUST validate user input via parseBitcoinAddress"
                    )
                }
                launch {
                    isClosing = true
                    val filteredChannels = filteredChannels(peerManager.getPeer().channels)
                    val sats = totalSats(filteredChannels)
                    filteredChannels.keys.forEach { channelId ->
                        val command = CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey))
                        val channelEvent = ChannelEvent.ExecuteCommand(command)
                        val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                        peerManager.getPeer().send(peerEvent)
                    }
                    model(CloseChannelsConfiguration.Model.ChannelsClosed(filteredChannels.size, sats))
                }
            }
        }
    }
}
