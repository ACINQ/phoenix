/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.settings.electrum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import kotlin.time.Duration.Companion.seconds

class ElectrumDialogViewModel : ViewModel() {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    var state by mutableStateOf<CertificateCheckState>(CertificateCheckState.Init)

    internal suspend fun checkCertificate(host: String, port: Int, onCertificateValid: (ServerAddress) -> Unit) {
        return withContext(viewModelScope.coroutineContext + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error in checkCertificate: ", e)
            state = CertificateCheckState.Failure(e)
        }) {
            state = CertificateCheckState.Checking
            delay(500) // add a small pause for better ux
            try {
                withTimeout(10.seconds) {
                    val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(host, port).tls(this.coroutineContext)
                    socket.close()
                    onCertificateValid(ServerAddress(host, port, TcpSocket.TLS.TRUSTED_CERTIFICATES()))
                    state = CertificateCheckState.Init
                }
            } catch (e: Exception) {
                log.error("failed to connect to $host:$port: ${e.message}: ", e)
                state = when (e) {
                    is java.security.cert.CertificateException -> {
                        val cause = e.cause
                        if (cause is CertPathValidatorException) {
                            val certs = cause.certPath.certificates
                            if (certs.isEmpty()) {
                                CertificateCheckState.Failure(RuntimeException("No certificate in path", e))
                            } else {
                                CertificateCheckState.Rejected(host, port, certs.first())
                            }
                        } else {
                            CertificateCheckState.Failure(e)
                        }
                    }
                    is CertPathValidatorException -> {
                        val certs = e.certPath.certificates
                        if (certs.isEmpty()) {
                            CertificateCheckState.Failure(RuntimeException("No certificate in path", e))
                        } else {
                            CertificateCheckState.Rejected(host, port, certs.first())
                        }
                    }
                    else -> {
                        CertificateCheckState.Failure(e)
                    }
                }
            }
        }
    }

    sealed class CertificateCheckState {
        data object Init : CertificateCheckState()
        data object Checking : CertificateCheckState()
        data class Rejected(val host: String, val port: Int, val certificate: Certificate) : CertificateCheckState()
        data class Failure(val e: Throwable) : CertificateCheckState()
    }
}