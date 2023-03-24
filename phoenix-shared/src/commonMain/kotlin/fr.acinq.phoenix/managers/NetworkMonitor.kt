package fr.acinq.phoenix.managers

import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory

enum class NetworkState {
    Available,
    NotAvailable
}

expect class NetworkMonitor(loggerFactory: LoggerFactory, ctx: PlatformContext) {
    val networkState: StateFlow<NetworkState>
    fun enable()
    fun disable()
    fun start()
    fun stop()
}
