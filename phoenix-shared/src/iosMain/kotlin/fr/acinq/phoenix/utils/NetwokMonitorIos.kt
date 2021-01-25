package fr.acinq.phoenix.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import platform.Network.*
import platform.darwin.dispatch_get_main_queue


@OptIn(ExperimentalCoroutinesApi::class)
actual class NetworkMonitor actual constructor(
    loggerFactory: LoggerFactory,
    ctx: PlatformContext
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private val monitor = nw_path_monitor_create()

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    @OptIn(ExperimentalUnsignedTypes::class)
    actual fun start() {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = when (nw_path_get_status(path)) {
                nw_path_status_satisfied -> {
                    logger.info { "status = nw_path_status_satisfied" }
                    NetworkState.Available
                }
                nw_path_status_satisfiable -> {
                    logger.info { "status = nw_path_status_satisfiable" }
                    NetworkState.Available
                }
                nw_path_status_unsatisfied -> {
                    logger.info { "status = nw_path_status_unsatisfied" }
                    NetworkState.NotAvailable
                }
                nw_path_status_invalid -> {
                    logger.info { "status = nw_path_status_invalid" }
                    NetworkState.NotAvailable
                }
                else -> {
                    logger.info { "status = nw_path_status_unknown" }
                    NetworkState.NotAvailable
                }
            }

            launch { _networkState.value = status }
        }

        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    actual fun stop() {
        // NB: once cancelled, monitor cannot be started again
        nw_path_monitor_cancel(monitor)
    }
}
