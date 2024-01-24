package fr.acinq.phoenix.managers

import android.content.Context
import android.net.*
import co.touchlab.kermit.Logger
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.loggerExtensions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


actual class NetworkMonitor actual constructor(loggerFactory: Logger, val ctx: PlatformContext) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {

    val logger = loggerFactory.appendingTag("NetworkMonitor")

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    init {
        launch {
            val connectivityManager = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        logger.debug { "network is now $network" }
                        connectivityManager.isDefaultNetworkActive
                        _networkState.value = NetworkState.Available
                    }

                    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                        super.onBlockedStatusChanged(network, blocked)
                        logger.debug { "block status change to $blocked for network=$network"}
                    }

                    override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
                        logger.debug { "default network changed capabilities to $networkCapabilities" }
                    }

                    override fun onLinkPropertiesChanged(network : Network, linkProperties : LinkProperties) {
                        logger.debug { "default network changed link properties to $linkProperties" }
                    }

                    override fun onLosing(network: Network, maxMsToLive: Int) {
                        super.onLosing(network, maxMsToLive)
                        logger.debug { "losing network in ${maxMsToLive}ms..." }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        logger.info { "network has been lost" }
                        _networkState.value = NetworkState.NotAvailable
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        logger.info { "network is unavailable" }
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
