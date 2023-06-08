package fr.acinq.phoenix.data

import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.*
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import kotlin.Double
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Example JSON from: https://acinq.co/phoenix/walletcontext.json:
 * {
 *   "testnet": {
 *     "version": 25,
 *     "latest_critical_version": 0,
 *     "trampoline": {
 *       "v1": {
 *         "fee_base_sat": 2,
 *         "fee_percent": 0.001,
 *         "hops_count": 5,
 *         "cltv_expiry": 143
 *       },
 *       "v2": {
 *         "attempts": [{
 *           "fee_base_sat": 1,
 *           "fee_percent": 0.0001,
 *           "fee_per_millionths": 100,
 *           "cltv_expiry": 576
 *         }, ... ],
 *         "nodes": [{
 *           "name": "endurance",
 *           "uri": "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@13.248.222.197:9735"
 *         }]
 *       }
 *     },
 *     "pay_to_open": {
 *       "v1": {
 *         "min_funding_sat": 10000,
 *         "min_fee_sat": 1000,
 *         "fee_percent": 0.001,
 *         "status": 0
 *       }
 *     },
 *     "swap_in": {
 *       "v1": {
 *         "min_funding_sat": 10000,
 *         "min_fee_sat": 3000,
 *         "fee_percent": 0.001,
 *         "status": 0
 *       }
 *     },
 *     "swap_out": {
 *       "v1": {
 *         "min_feerate_sat_byte": 0,
 *         "status": 0
 *       }
 *     },
 *     "mempool": {
 *       "v1": {
 *         "high_usage": false
 *       }
 *     }
 *   },
 *   "mainnet": {
 *     ...
 *   }
 * }
 */

object WalletContext {
    enum class Version { V0 }

    @Serializable
    data class V0(val testnet: ChainContext, val mainnet: ChainContext) {
        fun export(chain: NodeParams.Chain): ChainContext = if (chain.isMainnet()) {
            mainnet
        } else {
           testnet
        }

        @Serializable
        data class ChainContext(
            val version: Int,
            @SerialName("latest_critical_version") val latestCriticalVersion: Int,
            val trampoline: TrampolineParams,
            @SerialName("pay_to_open") val payToOpen: PayToOpen,
            @SerialName("swap_in") val swapIn: SwapIn,
            val mempool: Mempool
        ) {
            fun walletParams(): WalletParams = WalletParams(
                trampolineNode = trampoline.v2.nodes.first().export(),
                trampolineFees = trampoline.v3.map { it.export() },
                invoiceDefaultRoutingFees = InvoiceDefaultRoutingFees(
                    feeBase = 1000.msat,
                    feeProportional = 100,
                    cltvExpiryDelta = CltvExpiryDelta(144)
                ),
                swapInConfirmations = 3,
            )
        }

        @Serializable
        data class TrampolineParams(val v2: V2, val v3: List<TrampolineFees>) {
            @Serializable
            data class TrampolineFees(
                @SerialName("fee_base_sat") val feeBaseSat: Long,
                @SerialName("fee_per_millionths") val feePerMillionths: Long,
                @SerialName("cltv_expiry") val cltvExpiry: Int,
            ) {
                fun export(): fr.acinq.lightning.TrampolineFees = TrampolineFees(feeBaseSat.sat, feePerMillionths, CltvExpiryDelta(cltvExpiry))
            }

            @Serializable
            data class NodeUri(val name: String, val uri: String) {
                fun export(): fr.acinq.lightning.NodeUri {
                    val parts = uri.split("@", ":")

                    val publicKey = PublicKey.fromHex(parts[0])
                    val host = parts[1]
                    val port = parts[2].toInt()

                    return NodeUri(publicKey, host, port)
                }
            }

            @Serializable
            data class V2(val attempts: List<TrampolineFees>, val nodes: List<NodeUri> = emptyList())
        }

        /**
         * Maps the generic "status: Int" values in JSON to human-readable meaning.
         */
        sealed class ServiceStatus {
            object Unknown : ServiceStatus()
            object Active : ServiceStatus()
            sealed class Disabled : ServiceStatus() {
                object Generic : Disabled()
                object MempoolFull : Disabled()
            }

            companion object {
                fun valueOf(code: Int) = when (code) {
                    -1 -> Unknown
                    1 -> Disabled.Generic
                    2 -> Disabled.MempoolFull
                    else -> Active
                }
            }
        }

        @Serializable
        data class PayToOpen(val v1: V1) {

            @Serializable
            data class V1(
                @SerialName("min_funding_sat") val minFundingSat: Long,
                @SerialName("min_fee_sat") val minFeeSat: Long,
                @SerialName("fee_percent") val feePercent: Double,
                @SerialName("status") private val _status: Int
            ) {
                @Transient
                val status: ServiceStatus = ServiceStatus.valueOf(_status)
            }
        }

        @Serializable
        data class SwapIn(val v1: V1) {

            @Serializable
            data class V1(
                @SerialName("min_funding_sat") val minFundingSat: Long,
                @SerialName("min_fee_sat") val minFeeSat: Long,
                @SerialName("fee_percent") val feePercent: Double,
                @SerialName("status") private val _status: Int
            ) {
                @Transient
                val status: ServiceStatus = ServiceStatus.valueOf(_status)
            }
        }

        @Serializable
        data class Mempool(val v1: V1) {

            @Serializable
            data class V1(@SerialName("high_usage") val highUsage: Boolean)
        }
    }
}
