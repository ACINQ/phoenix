package fr.acinq.phoenix.ctrl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.consumeEach

@OptIn(ExperimentalCoroutinesApi::class)
class EventBus<EventType> : CoroutineScope {

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val channel = BroadcastChannel<EventType>(10)

    suspend fun send(event: EventType) {
        channel.send(event)
    }

    fun subscribe(onEvent: (EventType) -> Unit): () -> Unit {
        val subscription = launch {
            channel.openSubscription().consumeEach { onEvent(it) }
        }

        return ({ subscription.cancel() })
    }

    fun stop() {
        job.cancel()
    }
}