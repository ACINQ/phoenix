package fr.acinq.phoenix.io

import fr.acinq.phoenix.utils.decodeToString
import fr.acinq.phoenix.utils.splitByLines
import fr.acinq.phoenix.utils.subArray
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow


@OptIn(ExperimentalStdlibApi::class)
interface TcpSocket {

    sealed class IOException(message: String?) : Error(message) {
        object ConnectionRefused: IOException("Connection refused")
        object ConnectionClosed: IOException("Connection closed")
        object NoNetwork: IOException("No network available")
        class Unknown(message: String?): IOException(message)
    }

    suspend fun send(bytes: ByteArray?, flush: Boolean = true)

    suspend fun receiveFully(buffer: ByteArray)
    suspend fun receiveAvailable(buffer: ByteArray): Int

    fun close()

    fun interface Builder {
        suspend fun connect(host: String, port: Int): TcpSocket

        companion object {
            operator fun invoke(): Builder = PlatformSocketBuilder
        }
    }
}

internal expect object PlatformSocketBuilder : TcpSocket.Builder

suspend fun TcpSocket.receiveFully(size: Int): ByteArray =
    ByteArray(size).also { receiveFully(it) }

@OptIn(ExperimentalCoroutinesApi::class)
fun TcpSocket.receiveChannel(scope: CoroutineScope, maxChunkSize: Int = 8192) : ReceiveChannel<ByteArray> =
    scope.produce {
        val buffer = ByteArray(maxChunkSize)
        while (true) {
            val size = receiveAvailable(buffer)
            send(buffer.subArray(size))
        }
    }

fun TcpSocket.linesFlow(scope: CoroutineScope = AppMainScope()) =
    receiveChannel(scope).consumeAsFlow().decodeToString().splitByLines()
