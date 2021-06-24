package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.app.PaymentsManager
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(
    loggerFactory,
    firstModel = Home.emptyModel
) {
    private var isBoot = true

    init {
        launch {
            val peer = peerManager.getPeer()
            val bootFlow = peer.bootChannelsFlow.filterNotNull()
            val channelsFlow = peer.channelsFlow
            combine(bootFlow, channelsFlow) { bootChannels, channels ->
                // The bootFlow will fire once, after the channels have been read from the database.
                // The channelsFlow is initialized with an empty HashMap.
                if (isBoot) {
                    isBoot = false
                    bootChannels
                } else {
                    channels
                }
            }.collect { channels ->
                updateBalance(channels)
            }
        }

        launch {
            paymentsManager.incomingSwaps.collect {
                model {
                    copy(incomingBalance = it.values.sum().takeIf { it.msat > 0 })
                }
            }
        }

        launch {
            paymentsManager.paymentsCount.collect {
                model {
                    copy(
                        paymentsCount = it
                    )
                }
            }
        }
    }

    private suspend fun updateBalance(channels: Map<ByteVector32, ChannelState>): Unit {
        val newBalance = channels.values.map {
            // If the channel is offline, we want to display the underlying balance.
            // The alternative is to display a zero balance whenever the user is offline.
            // And if there's one sure way to make user's freak out,
            // it's showing them a zero balance...
            val channel = when (it) {
                is Offline -> it.state
                else -> it
            }
            when (channel) {
                is Closing -> MilliSatoshi(0)
                is Closed -> MilliSatoshi(0)
                is Aborted -> MilliSatoshi(0)
                is ErrorInformationLeak -> MilliSatoshi(0)
                else -> it.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
            }
        }.sum()

        model { copy(balance = newBalance) }
    }

    override fun process(intent: Home.Intent) {}
}
