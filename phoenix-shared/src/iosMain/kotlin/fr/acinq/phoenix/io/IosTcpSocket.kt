package fr.acinq.phoenix.io

import fr.acinq.phoenix.utils.AtomicOnce
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytesNoCopy
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalUnsignedTypes::class)
class IosTcpSocket(val connection: ConnectionBridge) : TcpSocket {
    interface ConnectionBridge {
        fun receive(minimumIncompleteLength: Int, maximumLength: Int, completion: (NSData?, Boolean, TcpSocket.IOException?) -> Unit)
        fun send(content: NSData?, isComplete: Boolean, completion: (TcpSocket.IOException?) -> Unit)
        fun cancel()

        interface Builder {
            fun connect(host: String, port: Int, tls: Boolean, completion: (ConnectionBridge?, TcpSocket.IOException?) -> Unit)

            companion object {
                var native: Builder by AtomicOnce()
            }
        }
    }

    override suspend fun send(bytes: ByteArray?, flush: Boolean): Unit =
        suspendCoroutine { continuation ->
            val pinned = bytes?.pin()
            val data = pinned?.let { NSData.dataWithBytesNoCopy(it.addressOf(0), bytes.size.toULong(), false) }
            connection.send(data, flush) { error ->
                pinned?.unpin()
                if (error != null) continuation.resumeWithException(error)
                else continuation.resume(Unit)
            }
        }

    override suspend fun receiveFully(buffer: ByteArray) {
        receive(buffer.size, buffer)
    }

    override suspend fun receiveAvailable(buffer: ByteArray): Int =
        receive(0, buffer)

    private suspend fun receive(min: Int, buffer: ByteArray): Int =
        suspendCoroutine { continuation ->
            connection.receive(min, buffer.size) { data, isComplete, error ->
                when {
                    error != null -> continuation.resumeWithException(error)
                    data != null -> {
                        buffer.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), data.bytes, data.length)
                        }
                        continuation.resume(data.length.toInt())
                    }
                    isComplete -> continuation.resumeWithException(TcpSocket.IOException.ConnectionClosed)
                    else -> error("NWConnection receive returned no data, no error, and no completion")
                }
            }
        }

    override fun close() {
        connection.cancel()
    }
}

internal actual object PlatformSocketBuilder : TcpSocket.Builder {
    override suspend fun connect(host: String, port: Int, tls: Boolean): TcpSocket =
        suspendCoroutine { continuation ->
            IosTcpSocket.ConnectionBridge.Builder.native.connect(host, port, tls) { connection, error ->
                when {
                    error != null -> continuation.resumeWithException(error)
                    connection != null -> continuation.resume(IosTcpSocket(connection))
                }
            }
        }
}
