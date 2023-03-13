package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.json.JsonSerializers
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

            peerManager.channelsFlow.filterNotNull().collect { channels ->
                val models = channels.values.map { mapChannelInfoToModel(it) }
                val allChannelsJson = models.associate { it.id to it.json }.toString()
                launch(Dispatchers.Main) {
                    model(
                        ChannelsConfiguration.Model(
                            nodeId = nodeId,
                            json = allChannelsJson,
                            channels = models
                        )
                    )
                }
            }
        }
    }

    override fun process(intent: ChannelsConfiguration.Intent) {}

    private fun mapChannelInfoToModel(channelInfo: LocalChannelInfo) = ChannelsConfiguration.Model.Channel(
        id = channelInfo.channelId,
        isOk = channelInfo.isUsable,
        stateName = if (channelInfo.isBooting) "Booting" else channelInfo.state::class.simpleName ?: "Unknown",
        localBalance = channelInfo.localBalance,
        capacity = channelInfo.capacity,
        json = JsonSerializers.json.encodeToString(channelInfo.state),
        txId = channelInfo.fundingTx
    )
}
