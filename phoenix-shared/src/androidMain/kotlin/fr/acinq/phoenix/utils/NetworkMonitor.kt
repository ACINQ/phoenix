package fr.acinq.phoenix.utils

import fr.acinq.eklair.utils.Connection
import kotlinx.coroutines.channels.ReceiveChannel


actual class NetworkMonitor() {
    actual fun openNetworkStateSubscription(): ReceiveChannel<Connection> {
        TODO("Not yet implemented")
    }
}