package fr.acinq.phoenix.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class Aggregator<T>(scope: CoroutineScope, private val input: ReceiveChannel<T>) {

    private val output = ConflatedBroadcastChannel<List<T>>(emptyList())

    init {
        scope.launch {
            input.consumeEach {
                output.send(output.value + it)
            }
        }
    }

    fun openSubscription(): ReceiveChannel<List<T>> = output.openSubscription()

}
