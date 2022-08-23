package fr.acinq.phoenix.managers

import fr.acinq.tor.Tor
import kotlinx.coroutines.*

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun Tor.startInProperScope(scope: CoroutineScope) {
    val currentDispatcher = scope.coroutineContext[CoroutineDispatcher.Key]
    if (currentDispatcher != Dispatchers.Default || currentDispatcher != Dispatchers.IO) {
        // on Android, tor startup MUST be run in a background thread, because it is a network operation.
        // see [android.os.NetworkOnMainThreadException]
        this.start(CoroutineScope(scope.coroutineContext.job + Dispatchers.Default))
    } else {
        this.start(scope)
    }
}