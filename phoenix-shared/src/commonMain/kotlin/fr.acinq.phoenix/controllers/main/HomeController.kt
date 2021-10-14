package fr.acinq.phoenix.controllers.main

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.calculateBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Home.emptyModel
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        paymentsManager = business.paymentsManager
    )

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

    private suspend fun updateBalance(channels: Map<ByteVector32, ChannelState>) {
        val newBalance = calculateBalance(channels)
        model { copy(balance = newBalance) }
    }

    override fun process(intent: Home.Intent) {}
}
