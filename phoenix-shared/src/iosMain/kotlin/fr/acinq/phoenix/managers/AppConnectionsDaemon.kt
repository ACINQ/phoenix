package fr.acinq.phoenix.managers

import fr.acinq.tor.Tor
import kotlinx.coroutines.*

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun Tor.startInProperScope(scope: CoroutineScope) {
    val currentDispatcher = scope.coroutineContext[CoroutineDispatcher.Key]
    if (currentDispatcher != Dispatchers.Main) {
        // on iOS, we must run tor operations on the main thread, to prevent issues with frozen objects.
        // TODO: remove this once we moved to the new memory model
        this.start(CoroutineScope(scope.coroutineContext.job + Dispatchers.Main))
    } else {
        this.start(scope)
    }
}