package fr.acinq.phoenix.utils

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/* Credit:
 * https://github.com/JetBrains/kotlinconf-app/
 *
 * (See file in project called FlowUtils.kt)
 */

// This doesn't work in Swift.
// For some reason, in Swift, it is exposed as a function within SwiftFlow itself.
/*
fun <T> Flow<T>.wrap() = SwiftFlow(this)
*/


class SwiftFlow<T>(private val origin: Flow<T>) : Flow<T> by origin {
    fun watch(block: (T) -> Unit): Closeable {
        val job = Job()

        onEach {
            block(it)
        }.launchIn(CoroutineScope(MainScope().coroutineContext + job))

        return object : Closeable {
            override fun close() {
                job.cancel()
            }
        }
    }
}


class SwiftStateFlow<T>(private val origin: StateFlow<T>) : StateFlow<T> by origin {
    fun watch(block: (T) -> Unit): Closeable {
        val job = Job()

        onEach {
            block(it)
        }.launchIn(CoroutineScope(MainScope().coroutineContext + job))

        return object : Closeable {
            override fun close() {
                job.cancel()
            }
        }
    }
}
