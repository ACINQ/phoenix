/*
 * Copyright 2022 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.tor.Tor
import fr.acinq.tor.socks.socks5Handshake
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class Socks5Proxy(
    private val socketBuilder: TcpSocket.Builder,
    loggerFactory: LoggerFactory,
    private val proxyHost: String,
    private val proxyPort: Int
): TcpSocket.Builder {

    val logger = newLogger(loggerFactory)

    override suspend fun connect(host: String, port: Int, tls: TcpSocket.TLS, loggerFactory: LoggerFactory): TcpSocket {
        val socket = socketBuilder.connect(proxyHost, proxyPort, TcpSocket.TLS.DISABLED, loggerFactory)
        val (cHost, cPort) = socks5Handshake(
            destinationHost = host,
            destinationPort = port,
            receive = { socket.receiveFully(it, offset = 0, length = it.size) },
            send = { socket.send(it, offset = 0, length = it.size, flush = true) }
        )
        logger.info { "connected through socks5 to $cHost:$cPort" }
        val updatedTls = when (tls) {
            is TcpSocket.TLS.TRUSTED_CERTIFICATES ->
                TcpSocket.TLS.TRUSTED_CERTIFICATES(tls.expectedHostName ?: host)
            else -> tls
        }
        return socket.startTls(updatedTls)
    }
}

fun TcpSocket.Builder.torProxy(loggerFactory: LoggerFactory) = Socks5Proxy(this, loggerFactory, Tor.SOCKS_ADDRESS, Tor.SOCKS_PORT)
