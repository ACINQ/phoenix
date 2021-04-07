package fr.acinq.phoenix.data

import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.InvoiceDefaultRoutingFees
import fr.acinq.lightning.WalletParams
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ApiWalletParams {
    enum class Version { V0 }

    @Serializable
    data class V0(val testnet: ChainParams, val mainnet: ChainParams) {
        fun export(chain: Chain): WalletParams = if (chain.isMainnet()) {
            mainnet.export()
        } else {
            testnet.export()
        }


        @Serializable
        data class ChainParams(
            val version: Int,
            @SerialName("latest_critical_version") val latestCriticalVersion: Int,
            val trampoline: TrampolineParams,
        ) {
            fun export(): WalletParams = trampoline.v2.run {
                WalletParams(nodes.first().export(), attempts.map { it.export() }, InvoiceDefaultRoutingFees(1000.msat, 100, CltvExpiryDelta(144)))
            }
        }

        @Serializable
        data class TrampolineParams(val v2: V2) {
            @Serializable
            data class TrampolineFees(
                @SerialName("fee_base_sat") val feeBaseSat: Long,
                @SerialName("fee_per_millionths") val feePerMillionths: Long,
                @SerialName("cltv_expiry") val cltvExpiry: Int,
            ) {
                fun export(): fr.acinq.lightning.TrampolineFees = fr.acinq.lightning.TrampolineFees(feeBaseSat.sat, feePerMillionths, CltvExpiryDelta(cltvExpiry))
            }

            @Serializable
            data class NodeUri(val name: String, val uri: String) {
                fun export(): fr.acinq.lightning.NodeUri {
                    val parts = uri.split("@", ":")

                    val publicKey = PublicKey.fromHex(parts[0])
                    val host = parts[1]
                    val port = parts[2].toInt()

                    return fr.acinq.lightning.NodeUri(publicKey, host, port)
                }
            }

            @Serializable
            data class V2(val attempts: List<TrampolineFees>, val nodes: List<NodeUri> = emptyList())
        }
    }
}
