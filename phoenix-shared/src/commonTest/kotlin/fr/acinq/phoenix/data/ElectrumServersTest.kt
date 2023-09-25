package fr.acinq.phoenix.data

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumConnectionStatus
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.kodein.log.LoggerFactory
import kotlin.test.Ignore
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
@Ignore
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

    @Test
    fun connect_and_get_fee_mainnet() = runBlocking {
        val res = mainnetElectrumServers.map { server ->
            try {
                val client = ElectrumClient(scope = this, loggerFactory = LoggerFactory.default, pingInterval = 30.seconds, rpcTimeout = 5.seconds)
                client.connect(socketBuilder = TcpSocket.Builder(), serverAddress = server)
                withTimeout(5.seconds) {
                    client.connectionStatus.filterIsInstance<ElectrumConnectionStatus.Connected>().first()
                    val fees = client.estimateFees(1)
                    println("✅ ${fees?.let { FeeratePerByte(it) }} sat/b (1 block) from ${server.host}:${server.port}")
                }
                client.disconnect()
                client.stop()

                true
            } catch (e: Exception) {
                println("❌ failed to establish connection with ${server.host}:${server.port} with tls=${server.tls::class.simpleName}: ${e.message}")
                false
            }
        }
        assertTrue("at least one of the mainnet servers is unreliable") { res.all { it } }
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