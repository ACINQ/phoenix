package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.phoenix.db.cloud.cborSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Architecture & Notes:
//
// We make every attempt to re-use code from the database serialization routines.
// However, cloud serialization is a bit different from database serialization.
//
// DIFFERENCE #1:
//
// SqlDelight allows us to version the database, and make changes to the database structure.
// For example, when upgrading the app from v1.5 to v1.6,
// we might upgrade the database from v2 to v3.
// This upgrade mechanism allows us to write code that **only** supports database v3,
// since SqlDelight handles the upgrade mechanics.
//
// This upgrade mechanism isn't available for the cloud.
// Thus, in the cloud we might have serialized objects in v1, v2, v3...
// And we will need to support deserializing all these versions.
//
// DIFFERENCE #2:
//
// For the local system, space is cheap, and the disk is fast.
// But for the cloud, space is really expensive.
// On iOS, the user only has 5 GB. But that's NOT per app.
// That 5GB is meant to be shared by every app on their phone.
// Which means we're expected to be a good steward of their cloud space.
//
// So we make certain changes.
//
// OPTIMIZATION #1:
//
// We use CBOR instead of JSON.
// This allows us to very efficiently encode ByteArray's.
//
// For a discussion of the space savings in practice,
// see the comments in CloudSerializers.kt.
//
// OPTIMIZATION #2:
//
// We use custom serializers when the other versions are found to
// be inefficient (in terms of space).
// For example, we supply an alternative UUIDSerializer.


enum class CloudDataVersion(val value: Int) {
    // Initial version
    V0(0)
    // Future versions go here
}

sealed class CloudData {
    @Serializable
    data class V0(
        @SerialName("i")
        val incoming: IncomingPaymentWrapperV10Legacy? = null,
        @ByteString
        @SerialName("o")
        val outgoing: LightningOutgoingPaymentWrapper? = null,
        @SerialName("so")
        val spliceOutgoing: SpliceOutgoingPaymentWrapper? = null,
        @SerialName("cc")
        val channelClose: ChannelClosePaymentWrapper? = null,
        @SerialName("sc")
        val spliceCpfp: SpliceCpfpPaymentWrapper? = null,
        @SerialName("il")
        val inboundLegacyLiquidity: InboundLiquidityLegacyWrapper? = null,
        @SerialName("ip")
        val inboundPurchaseLiquidity: InboundLiquidityPaymentWrapper? = null,
        @SerialName("v")
        val version: Int,
        @ByteString
        @SerialName("p")
        val padding: ByteArray?,
    ) : CloudData() {

        /**
         * This function exists because the Kotlin-generated
         * copy function doesn't translate to iOS very well.
         */
        fun copyWithPadding(padding: ByteArray): CloudData.V0 {
            return this.copy(padding = padding)
        }

        @Throws(Exception::class)
        fun unwrap(): WalletPayment? = when {
            incoming != null -> incoming.unwrap()
            outgoing != null -> outgoing.unwrap()
            spliceOutgoing != null -> spliceOutgoing.unwrap()
            channelClose != null -> channelClose.unwrap()
            spliceCpfp != null -> spliceCpfp.unwrap()
            inboundLegacyLiquidity != null -> inboundLegacyLiquidity.unwrap()
            inboundPurchaseLiquidity != null -> inboundPurchaseLiquidity.unwrap()
            else -> null
        }

        override fun serialize(): ByteArray = throw NotImplementedError("cannot create V0 cloud data anymore")

        companion object {
            @OptIn(ExperimentalSerializationApi::class)
            private fun cborDeserialize(blob: ByteArray): CloudData {
                return cborSerializer().decodeFromByteArray(blob)
            }
        }
    }

    data class V1(val payment: WalletPayment) : CloudData() {
        override fun serialize(): ByteArray {
            // TODO: prepend a version byte to avoid the brutal try/catch in the deserialization ?
            return Serialization.serialize(payment)
        }
    }

    abstract fun serialize(): ByteArray
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(data: ByteArray): CloudData? {
            return kotlin.runCatching {
                V1(Serialization.deserialize(data).getOrThrow())
            }.recoverCatching {
                cborSerializer().decodeFromByteArray<V0>(data)
            }.getOrNull()
        }
    }
}

/**
 * For DEBUGGING:
 *
 * You can use the jsonSerializer to see what the data looks like.
 * Just keep in mind that the ByteArray's will be encoded super-inefficiently.
 * That's because we're optimizing for Cbor.
 * To optimize for JSON, you would use ByteVector's,
 * and encode the data as Base64 via ByteVectorJsonSerializer.
 */
fun CloudData.jsonSerialize(): ByteArray {
    return Json.encodeToString(this).encodeToByteArray()
}
