package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import org.kodein.memory.util.freeze

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
// UUID is serialized as: {
//   mostSignificantBits: Long,
//   leastSignificantBits: Long
// }
// This is improved using a custom serializer.
//

enum class CloudDataVersion(val value: Int) {
    // Initial version
    V0(0)
    // Future versions go here
}

@Serializable
data class CloudData @OptIn(ExperimentalSerializationApi::class) constructor(
    @ByteString
    @SerialName("i")
    val incoming: IncomingPaymentWrapper?,
    @ByteString
    @SerialName("o")
    val outgoing: OutgoingPaymentWrapper?,
    @SerialName("v")
    val version: Int,
    @ByteString
    @SerialName("p")
    val padding: ByteArray?,
) {
    constructor(incoming: IncomingPayment, version: CloudDataVersion) : this(
        incoming = IncomingPaymentWrapper(incoming),
        outgoing = null,
        version = version.value,
        padding = ByteArray(size = 0)
    )

    constructor(outgoing: OutgoingPayment, version: CloudDataVersion) : this(
        incoming = null,
        outgoing = OutgoingPaymentWrapper(outgoing),
        version = version.value,
        padding = ByteArray(size = 0)
    )

    // This function exists because the Kotlin-generated
    // copy function doesn't translate to iOS very well.
    //
    fun copyWithPadding(padding: ByteArray): CloudData {
       return this.copy(padding = padding)
    }

    fun unwrap(): WalletPayment? = when {
        incoming != null -> incoming.unwrap()
        outgoing != null -> outgoing.unwrap()
        else -> null
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun CloudData.cborSerialize(): ByteArray {
    return Cbor.encodeToByteArray(this)
}

@OptIn(ExperimentalSerializationApi::class)
fun CloudData.Companion.cborDeserialize(blob: ByteArray): CloudData? {
    var result: CloudData? = null
    try {
        result = Cbor.decodeFromByteArray(blob)
    } catch (e: Throwable) {}

    return result
}

// For DEBUGGING:
//
// You can use the jsonSerializer to see what the data looks like.
// Just keep in mind that the ByteArray's will be encoded super-inefficiently.
// That's because we're optimizing for Cbor.
// To optimize for JSON, you would use ByteVector's,
// and encode the data as Base64 via ByteVectorJsonSerializer.

fun CloudData.jsonSerialize(): ByteArray {
    return Json.encodeToString(this).encodeToByteArray()
}
