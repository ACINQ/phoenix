package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.Normal
import fr.acinq.lightning.serialization.v1.Serialization.lightningSerializersModule
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.utils.extensions.localCommitmentSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        serializersModule = lightningSerializersModule
        allowStructuredMapKeys = true
    }

    init {
        launch(Dispatchers.Default) {
            val peer = peerManager.getPeer()
            val nodeId = peer.nodeParams.keyManager.nodeId.toString()
            peer.channelsFlow.collect { channels ->
                val channelsConfList = channels.map { (id, state) ->
                    ChannelsConfiguration.Model.Channel(
                        id = id.toHex(),
                        isOk = state is Normal,
                        stateName = state::class.simpleName ?: "Unknown",
                        localBalance = state.localCommitmentSpec?.toLocal,
                        remoteBalance = state.localCommitmentSpec?.toRemote,
                        json = json.encodeToString(
                            fr.acinq.lightning.serialization.v1.ChannelState.serializer(),
                            fr.acinq.lightning.serialization.v1.ChannelState.import(state)
                        ),
                        txId = if (state is ChannelStateWithCommitments) {
                            state.commitments.commitInput.outPoint.txid.toString()
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
