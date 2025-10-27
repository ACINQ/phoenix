/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.managers.global

import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.Network.*
import platform.darwin.dispatch_get_main_queue


actual class NetworkMonitor actual constructor(
    loggerFactory: LoggerFactory,
    ctx: PlatformContext
) : CoroutineScope by MainScope() {

    private val logger = loggerFactory.newLogger(this::class)

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
