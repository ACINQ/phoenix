package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.eclair.channel.ChannelState
import fr.acinq.eclair.channel.ChannelStateWithCommitments
import fr.acinq.eclair.channel.Normal
import fr.acinq.eclair.io.ByteVector32KSerializer
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.eclairSerializersModule
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ChannelsConfiguration
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppChannelsConfigurationController(
    loggerFactory: LoggerFactory, 
    private val peer: Peer, 
    private val chain: Chain
) : AppController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>(loggerFactory, ChannelsConfiguration.emptyModel) {

    private val json = Json {
        prettyPrint = true
        serializersModule = eclairSerializersModule
    }

    init {
        launch {
            peer.channelsFlow.collect {
                model(ChannelsConfiguration.Model(
                    peer.nodeParams.keyManager.nodeId.toString(),
                    json.encodeToString(MapSerializer(ByteVector32KSerializer, ChannelState.serializer()), it),
                    it.map { (id, state) ->
                        ChannelsConfiguration.Model.Channel(
                            id.toHex(),
                            state is Normal,
                            state::class.simpleName ?: "Unknown",
                            state.localCommitmentSpec?.let {
                                it.toLocal.truncateToSatoshi()
                                    .toLong() to (it.toLocal + it.toRemote).truncateToSatoshi().toLong()
                            },
                            json.encodeToString(ChannelState.serializer(), state),
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
