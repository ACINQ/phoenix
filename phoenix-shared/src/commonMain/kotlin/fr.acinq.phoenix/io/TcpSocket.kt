package fr.acinq.phoenix.io


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
