package fr.acinq.phoenix.data

import fr.acinq.lightning.utils.ServerAddress
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds


/**
 * This test checks if servers configured in the preset electrum list are valid.
 *
 * Some servers used pinned pubkey. To update the public key manually:
 * ```
 * $ openssl s_client -connect testnet.qtornado.com:51002
 * // write to file, then
 * $ openssl x509 -in testnet.qtornado.com.pem -noout -pubkey
 * ```
 */
class ElectrumServersTest {

    @Test
    fun connect_to_testnet_servers() = runBlocking {
        val res = testnetElectrumServers.map {
            testConnection(it)
        }.all { it }
        assertTrue("at least one of the testnet servers is unreachable/misconfigured") { res }
    }

    @Test
    fun connect_to_mainnet_servers() = runBlocking {
        val res = mainnetElectrumServers.map {
            testConnection(it)
        }.all { it }
        assertTrue("at least one of the mainnet servers is unreachable/misconfigured") { res }
    }

    /** Return false if cannot connect or timeout in 5s. */
    private suspend fun testConnection(server: ServerAddress): Boolean {
        return try {
            withTimeout(8.seconds) { connect(server) }
            println("✅ ${server.host}:${server.port}")
            true
        } catch (e: Exception) {
            val errorMessage = if (e is BadCertificate) {
                "${e::class.simpleName}: should be ${e.actualPubkey}"
            } else {
                "${e::class.simpleName}: ${e.message}"
            }
            println("❌ ${server.host}:${server.port} with tls=${server.tls::class.simpleName} $errorMessage")
            false
        }
    }
}

expect suspend fun connect(server: ServerAddress)
data class BadCertificate(val expectedPubkey: String, val actualPubkey: String): RuntimeException()