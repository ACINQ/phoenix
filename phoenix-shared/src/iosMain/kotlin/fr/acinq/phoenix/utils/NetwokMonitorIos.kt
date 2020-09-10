package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import platform.Network.*
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalCoroutinesApi::class)
actual class NetworkMonitor : CoroutineScope by MainScope() {
    private val monitor = nw_path_monitor_create()
    private val connectivityChannel = ConflatedBroadcastChannel<Connection>()
    actual fun openNetworkStateSubscription(): ReceiveChannel<Connection> = connectivityChannel.openSubscription()

    @OptIn(ExperimentalUnsignedTypes::class)
    actual fun start(): Unit {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status =
                if (nw_path_get_status(path) == nw_path_status_satisfied) Connection.ESTABLISHED
                else Connection.CLOSED

            launch { connectivityChannel.send(status) }
        }

        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    actual fun stop(): Unit {
        nw_path_monitor_cancel(monitor)
    }
}
