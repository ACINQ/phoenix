package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.Normal
import fr.acinq.lightning.serialization.v1.ByteVector32KSerializer
import fr.acinq.lightning.serialization.v1.Serialization.lightningSerializersModule
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val chain: Chain
) : AppController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = ChannelsConfiguration.emptyModel
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        chain = business.chain
    )

    private val json = Json {
        prettyPrint = true
        serializersModule = lightningSerializersModule
        allowStructuredMapKeys = true
    }

    init {
        launch {
            val peer = peerManager.getPeer()

            peer.channelsFlow.collect { channels ->
                model(
                    ChannelsConfiguration.Model(
                    nodeId = peer.nodeParams.keyManager.nodeId.toString(),
                    json = json.encodeToString(
                        serializer = MapSerializer(
                            ByteVector32KSerializer,
                            fr.acinq.lightning.serialization.v1.ChannelState.serializer()
                        ),
                        value = channels.mapValues {
                            fr.acinq.lightning.serialization.v1.ChannelState.import(it.value)
                        }
                    ),
                    channels = channels.map { (id, state) ->
                        ChannelsConfiguration.Model.Channel(
                            id = id.toHex(),
                            isOk = state is Normal,
                            stateName = state::class.simpleName ?: "Unknown",
                            localBalance = state.localCommitmentSpec?.let {
                                it.toLocal.truncateToSatoshi()
                            },
                            remoteBalance = state.localCommitmentSpec?.let {
                                it.toRemote.truncateToSatoshi()
                            },
                            json = json.encodeToString(
                                fr.acinq.lightning.serialization.v1.ChannelState.serializer(),
                                fr.acinq.lightning.serialization.v1.ChannelState.import(state)
                            ),
                            txId = if (state is ChannelStateWithCommitments) {
                                state.commitments.commitInput.outPoint.txid.toString()
                            } else null
                        )
                    }
                ))

            }
        }
    }

    override fun process(intent: ChannelsConfiguration.Intent) {}

}
