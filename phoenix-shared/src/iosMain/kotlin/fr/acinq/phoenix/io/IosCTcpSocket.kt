package fr.acinq.phoenix.io

import fr.acinq.phoenix.io.ios_network.create_tcp_parameters
import fr.acinq.phoenix.utils.AtomicOnce
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.dataWithBytesNoCopy
import platform.Network.*
import platform.darwin.*
import platform.posix.ECONNREFUSED
import platform.posix.ECONNRESET
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalUnsignedTypes::class)
class IosCTcpSocket(private val connection: nw_connection_t) : TcpSocket {

    override suspend fun send(bytes: ByteArray?, flush: Boolean): Unit =
        suspendCoroutine { continuation ->
            val pinned = bytes?.pin()
            // dispatch_data_create copies the buffer if DISPATCH_DATA_DESTRUCTOR_DEFAULT (=null),
            // is specified, and attempts to free the buffer if DISPATCH_DATA_DESTRUCTOR_FREE is specified,
            // so an empty destructor is specified.
            val data = pinned?.let { dispatch_data_create(it.addressOf(0), bytes.size.toULong(), dispatch_get_main_queue(), ({})) }

            nw_connection_send(connection, data, NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT, flush) { error ->
                pinned?.unpin()
                data?.let { objc_release(it.objcPtr()) }
                if (error != null) continuation.resumeWithException(error.toIOException())
                else {
                    continuation.resume(Unit)
                }
            }
        }

    private suspend fun receive(min: Int, buffer: ByteArray): Int =
        suspendCoroutine { continuation ->
            nw_connection_receive(connection, min.toUInt(), buffer.size.toUInt()) { data, _, isComplete, error ->
                when {
                    error != null -> continuation.resumeWithException(error.toIOException())
                    data != null -> {
                        buffer.usePinned { pinned ->
                            dispatch_data_apply(data) { _, offset, buffer, size ->
                                memcpy(pinned.addressOf(offset.toInt()), buffer, size)
                                true
                            }
                        }
                        continuation.resume(dispatch_data_get_size(data).toInt())
                    }
                    isComplete -> continuation.resumeWithException(TcpSocket.IOException.ConnectionClosed)
                    else -> error("NWConnection receive returned no data, no error, and no completion")
                }
            }
        }

    override suspend fun receiveFully(buffer: ByteArray) { receive(buffer.size, buffer) }

    override suspend fun receiveAvailable(buffer: ByteArray): Int = receive(0, buffer)

    override fun close() {
        nw_connection_cancel(connection)
        objc_release(connection.objcPtr())
    }
}

internal actual object PlatformSocketBuilder : TcpSocket.Builder {
    override suspend fun connect(host: String, port: Int, tls: Boolean): TcpSocket =
        suspendCoroutine { continuation ->
            autoreleasepool {
                val endpoint = nw_endpoint_create_host(host, port.toString())
//                val configureTls = if (tls) NW_PARAMETERS_DEFAULT_CONFIGURATION else NW_PARAMETERS_DISABLE_PROTOCOL
//                val parameters = nw_parameters_create_secure_tcp(configureTls, NW_PARAMETERS_DEFAULT_CONFIGURATION)
                val parameters = create_tcp_parameters(tls)
                val connection = nw_connection_create(endpoint, interpretObjCPointer(parameters.rawValue))
                objc_retain(connection.objcPtr())
                nw_connection_set_queue(connection, dispatch_get_main_queue())
                nw_connection_set_state_changed_handler(connection) { state, error ->
                    println("New state: $state")
                    when (state) {
                        nw_connection_state_ready -> {
                            nw_connection_set_state_changed_handler(connection, null)
                            continuation.resume(IosCTcpSocket(connection))
                        }
                        nw_connection_state_failed -> {
                            nw_connection_set_state_changed_handler(connection, null)
                            continuation.resumeWithException(error?.toIOException() ?: TcpSocket.IOException.Unknown("Socket failed without error"))
                        }
                    }
                }
                println("Starting")
                nw_connection_start(connection)
            }
        }
}

private fun nw_error_t.toIOException(): TcpSocket.IOException =
    when (nw_error_get_error_domain(this)) {
        nw_error_domain_posix -> when (nw_error_get_error_code(this)) {
            ECONNREFUSED -> TcpSocket.IOException.ConnectionRefused
            ECONNRESET -> TcpSocket.IOException.ConnectionClosed
            else -> TcpSocket.IOException.Unknown(this?.debugDescription)
        }
        else -> TcpSocket.IOException.Unknown(this?.debugDescription)
    }
