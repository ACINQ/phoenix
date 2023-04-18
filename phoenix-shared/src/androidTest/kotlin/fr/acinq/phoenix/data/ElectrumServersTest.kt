package fr.acinq.phoenix.data

import fr.acinq.lightning.io.JvmTcpSocket
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.Dispatchers
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private object ElectrumServersTestHelper {
    val selectorManager = ActorSelectorManager(Dispatchers.IO)
    val loggerFactory = LoggerFactory.default
}

actual suspend fun connect(server: ServerAddress) {
    val socket = aSocket(ElectrumServersTestHelper.selectorManager).tcp().connect(server.host, server.port).let { socket ->
        when (val tls = server.tls) {
            is TcpSocket.TLS.TRUSTED_CERTIFICATES -> socket.tls(Dispatchers.IO)
            is TcpSocket.TLS.PINNED_PUBLIC_KEY -> socket.tls(
                coroutineContext = Dispatchers.IO,
                config = TLSConfigBuilder().apply {
                    val expectedPubkey = tls.pubKey
                    val logger = ElectrumServersTestHelper.loggerFactory.newLogger(this::class)

                    // build a default X509 trust manager.
                    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())!!
                    factory.init(null as KeyStore?)
                    val defaultX509TrustManager = factory.trustManagers!!.filterIsInstance<X509TrustManager>().first()

                    // create a new trust manager that always accepts certificates for the pinned public key, or falls back to standard procedure.
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            defaultX509TrustManager.checkClientTrusted(chain, authType)
                        }

                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            val serverKey = JvmTcpSocket.buildPublicKey(chain?.asList()?.firstOrNull()?.publicKey?.encoded ?: throw CertificateException("certificate missing"), logger)
                            val pinnedKey = JvmTcpSocket.buildPublicKey(Base64.getDecoder().decode(expectedPubkey), logger)

                            if (serverKey != pinnedKey) {
                                throw BadCertificate(expectedPubkey, actualPubkey = Base64.getEncoder().encodeToString(serverKey.encoded))
                            }
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> = defaultX509TrustManager.acceptedIssuers
                    }
                }.build()
            )
            else -> throw IllegalArgumentException("reject connection to server with tls=$tls")
        }
    }
    socket.close()
}