package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.Normal
import fr.acinq.lightning.serialization.ByteVector32KSerializer
import fr.acinq.lightning.serialization.Serialization.lightningSerializersModule
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ChannelsConfiguration
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
    loggerFactory,
    firstModel = ChannelsConfiguration.emptyModel
) {

    private val json = Json {
        prettyPrint = true
        serializersModule = lightningSerializersModule
        allowStructuredMapKeys = true
    }

    init {
        launch {
            val peer = peerManager.getPeer()

            peer.channelsFlow.collect {
                model(ChannelsConfiguration.Model(
                    peer.nodeParams.keyManager.nodeId.toString(),
                    json.encodeToString(MapSerializer(
                        ByteVector32KSerializer,
                        fr.acinq.lightning.serialization.v1.ChannelState.serializer()
                    ),
                    it.mapValues { m ->  fr.acinq.lightning.serialization.v1.ChannelState.import(m.value) } ),
                    it.map { (id, state) ->
                        ChannelsConfiguration.Model.Channel(
                            id = id.toHex(),
                            isOk = state is Normal,
                            stateName = state::class.simpleName ?: "Unknown",
                            commitments = state.localCommitmentSpec?.let {
                                it.toLocal.truncateToSatoshi()
                                    .toLong() to (it.toLocal + it.toRemote).truncateToSatoshi().toLong()
                            },
                            json = json.encodeToString(
                                fr.acinq.lightning.serialization.v1.ChannelState.serializer(),
                                fr.acinq.lightning.serialization.v1.ChannelState.import(state)
                            ),
                            txUrl = if (state is ChannelStateWithCommitments) {
                                val txId = state.commitments.commitInput.outPoint.txid
                                val base = "https://mempool.space"
                                when (chain) {
                                    Chain.Mainnet -> "$base/tx/$txId"
                                    Chain.Testnet -> "$base/testnet/tx/$txId"
                                    Chain.Regtest -> "$base/_REGTEST_/tx/$txId"
                                }
                            } else null
                        )
                    }
                ))

            }
        }
    }

    override fun process(intent: ChannelsConfiguration.Intent) {}

}
