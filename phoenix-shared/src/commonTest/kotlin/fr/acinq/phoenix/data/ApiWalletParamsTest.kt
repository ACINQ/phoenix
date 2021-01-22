package fr.acinq.phoenix.data

import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.CltvExpiryDelta
import fr.acinq.eclair.NodeUri
import fr.acinq.eclair.TrampolineFees
import fr.acinq.eclair.WalletParams
import fr.acinq.eclair.utils.sat
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiWalletParamsTest {

    @Test
    fun `wallet params deserialization`() {
        val json = Json { ignoreUnknownKeys = true }
        val apiWalletParams = json.decodeFromString(ApiWalletParams.serializer(), jsonApi)
        assertNotNull(apiWalletParams)

        apiWalletParams.testnet.checkStructure()
        apiWalletParams.mainnet.checkStructure()

        assertEquals(1, apiWalletParams.testnet.trampoline.v2.nodes.size)
        assertEquals(TrampolineParams.NodeUri("ACINQ", "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@13.248.222.197:9735"), apiWalletParams.testnet.trampoline.v2.nodes.first())

        assertTrue(apiWalletParams.mainnet.trampoline.v2.nodes.isEmpty())
    }

    @Test
    fun `export to fr.acinq.eclair.WalletParams`() {
        val json = Json { ignoreUnknownKeys = true }
        val apiWalletParams = json.decodeFromString(ApiWalletParams.serializer(), jsonApi)
        assertNotNull(apiWalletParams)

        assertEquals(WalletParams(
            NodeUri(
                PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
                "13.248.222.197",
                9735
            ),
            listOf(
                TrampolineFees(0.sat, 0, CltvExpiryDelta(576)),
                TrampolineFees(1.sat, 100, CltvExpiryDelta(576))
            )
        ),
            apiWalletParams.export(Chain.TESTNET))

    }

    private fun ChainParams.checkStructure() {
        assertEquals(4, version)
        assertEquals(0, latestCriticalVersion)
        assertEquals(2, trampoline.v2.attempts.size)
        assertEquals(TrampolineParams.TrampolineFees(0, 0, 576), trampoline.v2.attempts.first())
        assertEquals(TrampolineParams.TrampolineFees(1, 100, 576), trampoline.v2.attempts.last())
    }

    private val jsonApi = """
        {
          "testnet": {
            "version": 4,
            "latest_critical_version": 0,
            "trampoline": {
              "v1": {
                "fee_base_sat": 2,
                "fee_percent": 0.001,
                "hops_count": 5,
                "cltv_expiry": 143
              },
              "v2": {
                "attempts": [
                  {
                    "fee_base_sat": 0,
                    "fee_percent": 0,
                    "fee_per_millionths": 0,
                    "cltv_expiry": 576
                  },
                  {
                    "fee_base_sat": 1,
                    "fee_percent": 0.0001,
                    "fee_per_millionths": 100,
                    "cltv_expiry": 576
                  }
                ],
                "nodes": [
                  {
                    "name": "ACINQ",
                    "uri": "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@13.248.222.197:9735"
                  }
                ]
              }
            },
            "swap_in": {
              "v1": {
                "fee_percent": 0.001
              }
            },
            "swap_out": {
              "v1": {
                "min_feerate_sat_byte": 0
              }
            },
            "mempool": {
              "v1": {
                "high_usage": true
              }
            }
          },
          "mainnet": {
            "version": 4,
            "latest_critical_version": 0,
            "trampoline": {
              "v1": {
                "fee_base_sat": 2,
                "fee_percent": 0.001,
                "hops_count": 5,
                "cltv_expiry": 143
              },
              "v2": {
                "attempts": [
                  {
                    "fee_base_sat": 0,
                    "fee_percent": 0,
                    "fee_per_millionths": 0,
                    "cltv_expiry": 576
                  },
                  {
                    "fee_base_sat": 1,
                    "fee_percent": 0.0001,
                    "fee_per_millionths": 100,
                    "cltv_expiry": 576
                  }
                ]
              }
            },
            "swap_in": {
              "v1": {
                "fee_percent": 0.001
              }
            },
            "swap_out": {
              "v1": {
                "min_feerate_sat_byte": 40
              }
            },
            "mempool": {
              "v1": {
                "high_usage": false
              }
            }
          }
        }
    """

}