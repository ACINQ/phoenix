package fr.acinq.phoenix.managers

import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.StateFlow
import co.touchlab.kermit.Logger

enum class NetworkState {
    Available,
    NotAvailable
}

expect class NetworkMonitor(loggerFactory: Logger, ctx: PlatformContext) {
    val networkState: StateFlow<NetworkState>
    fun enable()
    fun disable()
    fun start()
    fun stop()
}
