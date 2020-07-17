package fr.acinq.phoenix

import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class HandshakeTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun handshake() {
        runBlocking {
            val phoenix = Phoenix()
            var first = true
            phoenix.protocolLogs.openSubscription().consumeEach { list ->
                if (first) list.forEach { println("    log: $it") }
                else println("    log: ${list.last()}")
                first = false
            }
        }
    }

}
