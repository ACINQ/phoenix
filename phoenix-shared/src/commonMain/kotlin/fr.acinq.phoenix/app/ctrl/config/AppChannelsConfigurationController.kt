package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.eclair.channel.ChannelStateWithCommitments
import fr.acinq.eclair.channel.Normal
import fr.acinq.eclair.serialization.ByteVector32KSerializer
import fr.acinq.eclair.serialization.Serialization.eclairSerializersModule
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
) : AppController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>(loggerFactory, ChannelsConfiguration.emptyModel) {

    private val json = Json {
        prettyPrint = true
        serializersModule = eclairSerializersModule
    }

    init {
        launch {
            val peer = peerManager.getPeer()

            peer.channelsFlow.collect {
                model(ChannelsConfiguration.Model(
                    peer.nodeParams.keyManager.nodeId.toString(),
                    json.encodeToString(MapSerializer(
                        ByteVector32KSerializer,
                        fr.acinq.eclair.serialization.v1.ChannelState.serializer()
                    ),
                    it.mapValues { m ->  fr.acinq.eclair.serialization.v1.ChannelState.import(m.value) } ),
                    it.map { (id, state) ->
                        ChannelsConfiguration.Model.Channel(
                            id.toHex(),
                            state is Normal,
                            state::class.simpleName ?: "Unknown",
                            state.localCommitmentSpec?.let {
                                it.toLocal.truncateToSatoshi()
                                    .toLong() to (it.toLocal + it.toRemote).truncateToSatoshi().toLong()
                            },
                            json.encodeToString(
                                fr.acinq.eclair.serialization.v1.ChannelState.serializer(),
                                fr.acinq.eclair.serialization.v1.ChannelState.import(state)
                            ),
                            if (state is ChannelStateWithCommitments) {
                                val prefix = when (chain) {
                                    Chain.MAINNET -> ""
                                    Chain.TESTNET -> "testnet/"
                                    Chain.REGTEST -> "_REGTEST_/"
                                }
                                val txId = state.commitments.localCommit.publishableTxs.commitTx.tx.txid
                                "https://blockstream.info/$prefix/tx/$txId"
                            } else null
                        )
                    }
                ))

            }
        }
    }

    override fun process(intent: ChannelsConfiguration.Intent) {}

}
