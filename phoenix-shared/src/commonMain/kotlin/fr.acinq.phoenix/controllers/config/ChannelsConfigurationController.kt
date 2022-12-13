package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.Normal
import fr.acinq.lightning.json.JsonSerializers
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.utils.extensions.localCommitmentSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.kodein.log.LoggerFactory


class AppChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
) : AppController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = ChannelsConfiguration.emptyModel
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
    )

    init {
        launch(Dispatchers.Default) {
            val peer = peerManager.getPeer()
            val nodeId = peer.nodeParams.keyManager.nodeId.toString()
            peer.channelsFlow.collect { channels ->
                val channelsConfList = channels.map { (channelId, channelState) ->
                    ChannelsConfiguration.Model.Channel(
                        id = channelId.toHex(),
                        isOk = channelState is Normal,
                        stateName = channelState::class.simpleName ?: "Unknown",
                        localBalance = channelState.localCommitmentSpec?.toLocal,
                        remoteBalance = channelState.localCommitmentSpec?.toRemote,
                        json = JsonSerializers.json.encodeToString(channelState),
                        txId = if (channelState is ChannelStateWithCommitments) {
                            channelState.commitments.commitInput.outPoint.txid.toString()
                        } else null
                    )
                }
                val allChannelsJson = channelsConfList.associate { it.id to it.json }.toString()
                launch(Dispatchers.Main) {
                    model(
                        ChannelsConfiguration.Model(
                            nodeId = nodeId,
                            json = allChannelsJson,
                            channels = channelsConfList
                        )
                    )
                }
            }
        }
    }

    override fun process(intent: ChannelsConfiguration.Intent) {}

}
