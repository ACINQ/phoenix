package fr.acinq.phoenix

import fr.acinq.bitcoin.ByteVector32
import kotlin.native.concurrent.ThreadLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach

@OptIn(ExperimentalCoroutinesApi::class)
// TODO to be replaced by a real DB
class FakeDataStore(scope: CoroutineScope): CoroutineScope by scope {

    private val trigger = ConflatedBroadcastChannel<Boolean>()
    fun openTriggerSubscription() = trigger.openSubscription()

    var seed: ByteVector32? = null
        set(value) {
            field = value
            launch  { trigger.send(true) }
        }

}
