package fr.acinq.phoenix.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@ExperimentalCoroutinesApi
actual class NetworkMonitor actual constructor(loggerFactory: LoggerFactory, val ctx: PlatformContext) : CoroutineScope by MainScope() {

    val logger = newLogger(loggerFactory)

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    private var enabled = true
    private var started = false

    actual fun enable() {
        enabled = true
        start()
    }

    actual fun disable() {
        enabled = false
        stop()
    }

    actual fun start() {
        if (!enabled || started) {
            return
        }
        val connectivityManager = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                logger.info { "network available" }
                launch { _networkState.value = NetworkState.Available }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                logger.info { "network lost" }
                launch { _networkState.value = NetworkState.NotAvailable }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                logger.info { "network unavailable" }
                launch { _networkState.value = NetworkState.NotAvailable }
            }
        })
        started = true
    }

    actual fun stop() {
        logger.error { "Not yet implemented!" }
    }
}
