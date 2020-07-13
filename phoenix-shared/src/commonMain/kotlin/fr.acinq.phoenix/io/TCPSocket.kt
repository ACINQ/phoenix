package fr.acinq.phoenix.io

import kotlin.coroutines.cancellation.CancellationException


@OptIn(ExperimentalStdlibApi::class)
interface TCPSocket {

    sealed class IOException(message: String?) : Error(message) {
        object ConnectionRefused: IOException("Connection refused")
        object ConnectionClosed: IOException("Connection closed")
        object NoNetwork: IOException("No network available")
        class Unknown(message: String?): IOException(message)
    }

    sealed class Result<T> {
        abstract fun unpack(): T
        data class Failure<T>(val error: IOException): Result<T>() { override fun unpack(): T = throw error }
        data class Success<T>(val result: T): Result<T>() { override fun unpack(): T = result }
        fun resultOrNull(): T? = (this as? Success)?.result
    }

    suspend fun send(bytes: ByteArray?, flush: Boolean = true): Result<Unit>

    suspend fun receive(min: Int, max: Int): Result<ByteArray>

    fun close()

    fun interface Builder {
        @Throws(CancellationException::class, IOException::class)
        suspend fun connect(host: String, port: Int): Result<TCPSocket>
    }

}

suspend fun TCPSocket.receive(exactly: Int): TCPSocket.Result<ByteArray> = receive(exactly, exactly)

suspend fun TCPSocket.receiveBytes(min: Int, max: Int): ByteArray = receive(min, max).unpack()
suspend fun TCPSocket.receiveBytes(exactly: Int): ByteArray = receive(exactly, exactly).unpack()
