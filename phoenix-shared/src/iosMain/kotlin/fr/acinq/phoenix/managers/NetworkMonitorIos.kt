package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.loggerExtensions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.Network.*
import platform.darwin.dispatch_get_main_queue


actual class NetworkMonitor actual constructor(
    loggerFactory: Logger,
    ctx: PlatformContext
) : CoroutineScope by MainScope() {

    private val logger = loggerFactory.appendingTag("NetworkMonitor")

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    private var enabled = true
    private var monitor: nw_path_monitor_t = null

    actual fun enable() {
        enabled = true
        start()
    }

    actual fun disable() {
        enabled = false
        stop()
        _networkState.value = NetworkState.Available
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    actual fun start() {
        if (!enabled || monitor != null) {
            return
        }

        monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = when (nw_path_get_status(path)) {
                nw_path_status_satisfied -> {
                    logger.debug { "status = nw_path_status_satisfied" }
                    NetworkState.Available
                }
                nw_path_status_satisfiable -> {
                    logger.debug { "status = nw_path_status_satisfiable" }
                    NetworkState.Available
                }
                nw_path_status_unsatisfied -> {
                    logger.debug { "status = nw_path_status_unsatisfied" }
                    NetworkState.NotAvailable
                }
                nw_path_status_invalid -> {
                    logger.debug { "status = nw_path_status_invalid" }
                    NetworkState.NotAvailable
                }
                else -> {
                    logger.debug { "status = nw_path_status_unknown" }
                    NetworkState.NotAvailable
                }
            }

            launch { _networkState.value = status }
        }

        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    actual fun stop() {
        if (monitor != null) {
            // NB: once cancelled, a monitor instance cannot be started again
            nw_path_monitor_cancel(monitor)
            monitor = null
        }
    }
}
