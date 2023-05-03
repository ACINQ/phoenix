package fr.acinq.phoenix.managers

import android.content.Context
import android.net.*
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


actual class NetworkMonitor actual constructor(loggerFactory: LoggerFactory, val ctx: PlatformContext) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {

    val logger = newLogger(loggerFactory)

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    init {
        launch {
            val connectivityManager = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        logger.info { "network is now $network" }
                        connectivityManager.isDefaultNetworkActive
                        _networkState.value = NetworkState.Available
                    }

                    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                        super.onBlockedStatusChanged(network, blocked)
                        logger.info { "block status change to $blocked for network=$network"}
                    }

                    override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
                        logger.info { "default network changed capabilities to $networkCapabilities" }
                    }

                    override fun onLinkPropertiesChanged(network : Network, linkProperties : LinkProperties) {
                        logger.info { "default network changed link properties to $linkProperties" }
                    }

                    override fun onLosing(network: Network, maxMsToLive: Int) {
                        super.onLosing(network, maxMsToLive)
                        logger.info { "losing network in ${maxMsToLive}ms..." }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        logger.info { "network lost, last default was $network" }
                        _networkState.value = NetworkState.NotAvailable
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        logger.info { "network unavailable" }
                        _networkState.value = NetworkState.NotAvailable
                    }
                }
            )
        }
    }


    actual fun enable() {
    }

    actual fun disable() {
    }

    actual fun start() {
    }

    actual fun stop() {
    }
}
