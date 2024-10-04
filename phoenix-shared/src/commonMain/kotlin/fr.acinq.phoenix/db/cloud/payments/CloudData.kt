package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.db.*
import fr.acinq.phoenix.db.cloud.payments.InboundLiquidityLegacyWrapper
import fr.acinq.phoenix.db.cloud.payments.InboundLiquidityPaymentWrapper
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
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

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CloudData(
    @SerialName("i")
    val incoming: IncomingPaymentWrapper?,
    @SerialName("o")
    val outgoing: LightningOutgoingPaymentWrapper?,
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
) {
    constructor(incoming: IncomingPayment) : this(
        incoming = IncomingPaymentWrapper(incoming),
        outgoing = null,
        spliceOutgoing = null,
        channelClose = null,
        spliceCpfp = null,
        inboundLegacyLiquidity = null,
        inboundPurchaseLiquidity = null,
        version = CloudDataVersion.V0.value,
        padding = ByteArray(size = 0)
    )

    constructor(outgoing: OutgoingPayment) : this(
        incoming = null,
        outgoing = if (outgoing is LightningOutgoingPayment) LightningOutgoingPaymentWrapper(outgoing) else null,
        spliceOutgoing = if (outgoing is SpliceOutgoingPayment) SpliceOutgoingPaymentWrapper(outgoing) else null,
        channelClose = if (outgoing is ChannelCloseOutgoingPayment) ChannelClosePaymentWrapper(outgoing) else null,
        spliceCpfp = if (outgoing is SpliceCpfpOutgoingPayment) SpliceCpfpPaymentWrapper(outgoing) else null,
        inboundLegacyLiquidity = null,
        inboundPurchaseLiquidity = if (outgoing is InboundLiquidityOutgoingPayment) InboundLiquidityPaymentWrapper(outgoing) else null,
        version = CloudDataVersion.V0.value,
        padding = ByteArray(size = 0)
    )

    /**
     * This function exists because the Kotlin-generated
     * copy function doesn't translate to iOS very well.
     */
    fun copyWithPadding(padding: ByteArray): CloudData {
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

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun CloudData.cborSerialize(): ByteArray {
    return Cbor.encodeToByteArray(this)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun CloudData.Companion.cborDeserialize(
    blob: ByteArray
): CloudData {
    return cborSerializer().decodeFromByteArray(blob)
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
