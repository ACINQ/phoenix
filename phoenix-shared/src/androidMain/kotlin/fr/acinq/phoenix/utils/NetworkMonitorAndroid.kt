package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@ExperimentalCoroutinesApi
actual class NetworkMonitor actual constructor(loggerFactory: LoggerFactory, ctx: PlatformContext) : CoroutineScope by MainScope() {

    val logger = newLogger(loggerFactory)

    actual fun start() {
        logger.error { "Not yet implemented!" }
    }

    actual fun stop() {
        logger.error { "Not yet implemented!" }
    }

    actual val networkState: StateFlow<Connection>
        get() = TODO("Not yet implemented")
}
