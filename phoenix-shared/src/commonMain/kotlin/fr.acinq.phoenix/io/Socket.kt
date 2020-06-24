package fr.acinq.phoenix.io


interface Socket {

    sealed class State {
        object Ready: State()
        class Error(val exception: IOException): State()
        object Closed: State()
        companion object
    }

    sealed class IOException(message: String?) : Exception(message) {
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

    fun interface Factory {
        fun createSocket(host: String, port: Int, onStateChange: (Socket, State) -> Unit): Socket
    }

}

suspend fun Socket.receive(exactly: Int): Socket.Result<ByteArray> = receive(exactly, exactly)

suspend fun Socket.receiveBytes(min: Int, max: Int): ByteArray = receive(min, max).unpack()
suspend fun Socket.receiveBytes(exactly: Int): ByteArray = receive(exactly, exactly).unpack()
