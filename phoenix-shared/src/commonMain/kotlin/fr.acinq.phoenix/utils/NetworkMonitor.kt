package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory

enum class NetworkState {
    Available,
    NotAvailable
}

@OptIn(ExperimentalCoroutinesApi::class)
expect class NetworkMonitor(loggerFactory: LoggerFactory, ctx: PlatformContext) {
    val networkState: StateFlow<NetworkState>
    fun start()
    fun stop()
}
