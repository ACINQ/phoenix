package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.channels.ReceiveChannel
import org.kodein.log.LoggerFactory


expect class NetworkMonitor(loggerFactory: LoggerFactory, ctx: PlatformContext) {
    fun openNetworkStateSubscription(): ReceiveChannel<Connection>
    fun start()
    fun stop()
}

operator fun Connection.plus(other: Connection) : Connection =
    when {
        this == other -> this
        this == Connection.ESTABLISHING || other == Connection.ESTABLISHING -> Connection.ESTABLISHING
        this == Connection.CLOSED || other == Connection.CLOSED -> Connection.CLOSED
        else -> error("Cannot add [$this + $other]")
    }
