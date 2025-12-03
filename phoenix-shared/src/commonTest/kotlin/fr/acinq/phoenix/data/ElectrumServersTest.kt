package fr.acinq.phoenix.data

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumConnectionStatus
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.utils.testLoggerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue


/**
 * This test checks if servers configured in the preset electrum list are valid.
 *
 * Some servers used pinned pubkey. To update the public key manually:
 * ```
 * openssl s_client -connect host:port -servername host </dev/null 2>/dev/null | openssl x509 -pubkey -noout
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
        val res = mainnetElectrumServers.associate { server ->
            val client = ElectrumClient(scope = this, loggerFactory = testLoggerFactory, pingInterval = 30.seconds, rpcTimeout = 5.seconds)
            try {
                println("-------- testing [ ${server.host}:${server.port} ${server.tls::class.simpleName} ] --------")
                withTimeout(5.seconds) {
                    val (status, t) = measureTimedValue {
                        client.connect(socketBuilder = TcpSocket.Builder(), serverAddress = server)
                        client.connectionStatus.filterIsInstance<ElectrumConnectionStatus.Connected>().first()
                    }
                    println("✅ [${t.inWholeMilliseconds} ms] connected at height=${status.height}")
                }
                // evaluate the server's estimate-fee performance
                withTimeout(10.seconds) {
                    val (fees, t) = measureTimedValue { client.estimateFees(1) }
                    println("✅ [${t.inWholeMilliseconds} ms] next block is ${fees?.let { FeeratePerByte(it) }}")
                }
                // evaluate the server's get-confirmation count on a random transaction
                withTimeout(10.seconds) {
                    val (tx, t1) = measureTimedValue { client.getTx(TxId("6703c9dc67a2d66ec027b2aaa4016e72dcb7ecc5fe6f4a270a35bdf52f3c4eeb")) }
                    tx?.let {
                        println("✅ [${t1.inWholeMilliseconds} ms] tx found")
                        val (conf, t2) = measureTimedValue { client.getConfirmations(tx) }
                        println("✅ [${t2.inWholeMilliseconds} ms] tx reached $conf confs")
                    } ?: error("could not find tx")
                }

                server.host to true
            } catch (e: Exception) {
                println("❌ failure: ${e.message}")
                server.host to false
            } finally {
                client.disconnect()
                client.stop()
                println("------------------------------------\n")
            }
        }
        val faulty = res.filter { (server, success) -> !success }
        if (faulty.isNotEmpty()) {
            println("\uD83D\uDD34 ${faulty.size} servers are unreliable:")
            println(faulty.keys.joinToString(", "))
            error("consider updating list or removing unreliable servers")
        } else {
            println("\uD83D\uDFE2 no unreliable servers in mainnet list")
        }
    }

    /** Return false if cannot connect or timeout in 5s. */
    private suspend fun testConnection(server: ServerAddress): Boolean {
        return try {
            withTimeout(5.seconds) { connect(server) }
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