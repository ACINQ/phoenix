package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.db.cloud.cborSerializer
import fr.acinq.phoenix.db.payments.LnurlBase
import fr.acinq.phoenix.db.payments.LnurlMetadata
import fr.acinq.phoenix.db.payments.LnurlSuccessAction
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor

enum class CloudAssetVersion(val value: Int) {
    // Initial version
    V0(0),
    // V1:
    // - added `original_fiat`
    V1(1)
    // Future versions go here
}

// Upgrade notes:
// If needed in the future, you can use code like this to extract only the version:
//
// data class CloudAssetVersion(
//    @SerialName("v")
//    val version: Int
// )
// val version = try {
//    cborSerializer().decodeFromByteArray<CloudAssetVersion>(blob)
// } catch (e: Throwable) { null }

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CloudAsset(
    @SerialName("v")
    val version: Int,
    val lnurl_base: LnurlBaseWrapper?,
    val lnurl_metadata: LnurlMetadataWrapper?,
    val lnurl_successAction: LnurlSuccessActionWrapper?,
    val lnurl_description: String?,
    val user_description: String?,
    val user_notes: String?,
    val original_fiat: OriginalFiatWrapper? = null // added in V1
) {
    constructor(row: WalletPaymentMetadataRow) : this(
        version = CloudAssetVersion.V1.value,
        lnurl_base = row.lnurl_base?.let {
            LnurlBaseWrapper(it.first.name, it.second)
        },
        lnurl_metadata = row.lnurl_metadata?.let {
            LnurlMetadataWrapper(it.first.name, it.second)
        },
        lnurl_successAction = row.lnurl_successAction?.let {
            LnurlSuccessActionWrapper(it.first.name, it.second)
        },
        lnurl_description = row.lnurl_description,
        user_description = row.user_description,
        user_notes = row.user_notes,
        original_fiat = row.original_fiat?.let {
            OriginalFiatWrapper(it.first, it.second)
        }
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LnurlBaseWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        var typeVersion = LnurlBase.TypeVersion.valueOf(type)
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LnurlMetadataWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        var typeVersion = LnurlMetadata.TypeVersion.valueOf(type)
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LnurlSuccessActionWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        var typeVersion = LnurlSuccessAction.TypeVersion.valueOf(type)
    }

    @Serializable
    data class OriginalFiatWrapper(
        val type: String,
        val rate: Double
    )

    @Throws(Exception::class)
    fun unwrap() = WalletPaymentMetadataRow(
        lnurl_base = lnurl_base?.let {
            Pair(it.typeVersion, it.blob)
        },
        lnurl_metadata = lnurl_metadata?.let {
            Pair(it.typeVersion, it.blob)
        },
        lnurl_successAction = lnurl_successAction?.let {
            Pair(it.typeVersion, it.blob)
        },
        lnurl_description = lnurl_description,
        user_description = user_description,
        user_notes = user_notes,
        original_fiat = original_fiat?.let {
            Pair(it.type, it.rate)
        },
        modified_at = currentTimestampMillis()
    )

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun CloudAsset.cborSerialize(): ByteArray {
    return Cbor.encodeToByteArray(this)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun CloudAsset.Companion.cborDeserialize(
    blob: ByteArray
): CloudAsset {
    return cborSerializer().decodeFromByteArray(blob)
}
