package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.channels.ReceiveChannel
import org.kodein.di.DI
import org.kodein.di.DIAware

expect class NetworkMonitor(di: DI) : DIAware {
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