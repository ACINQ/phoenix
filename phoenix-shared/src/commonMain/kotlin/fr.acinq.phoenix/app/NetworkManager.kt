package fr.acinq.phoenix.app

import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory

enum class NetworkState {
    Available,
    NotAvailable
}

@OptIn(ExperimentalCoroutinesApi::class)
expect class NetworkManager(loggerFactory: LoggerFactory, ctx: PlatformContext) {
    val networkState: StateFlow<NetworkState>
    fun start()
    fun stop()
}
