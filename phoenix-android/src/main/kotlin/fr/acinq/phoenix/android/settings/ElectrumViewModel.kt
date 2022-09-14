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

package fr.acinq.phoenix.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class ElectrumViewModel : ViewModel() {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @OptIn(ExperimentalTime::class)
    internal suspend fun checkCertificate(host: String, port: Int): CertificateCheckState {
        return withContext(viewModelScope.coroutineContext + Dispatchers.IO) {
            delay(500) // add a small pause for better ux
            try {
                withTimeout(Duration.seconds(10)) {
                    val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(host, port).tls(viewModelScope.coroutineContext + Dispatchers.IO)
                    socket.close()
                    CertificateCheckState.Valid(host, port)
                }
            } catch (e: Exception) {
                log.error("failed to connect to $host:$port: ${e.message}: ", e)
                when (e) {
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

    internal sealed class CertificateCheckState {
        object Init : CertificateCheckState()
        object Checking : CertificateCheckState()
        data class Valid(val host: String, val port: Int) : CertificateCheckState()
        data class Rejected(val host: String, val port: Int, val certificate: Certificate) : CertificateCheckState()
        data class Failure(val e: Exception) : CertificateCheckState()
    }
}