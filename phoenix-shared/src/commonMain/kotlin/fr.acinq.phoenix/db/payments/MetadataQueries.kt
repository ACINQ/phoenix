package fr.acinq.phoenix.db.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didUpdateWalletPaymentMetadata
import io.ktor.http.*

class MetadataQueries(val database: PaymentsDatabase) {

    private val queries = database.paymentsMetadataQueries

    fun addMetadata(
        id: WalletPaymentId,
        data: WalletPaymentMetadataRow
    ) {
        queries.addMetadata(
            type = id.dbType.value,
            id = id.dbId,
            lnurl_base_type = data.lnurl_base?.first,
            lnurl_base_blob = data.lnurl_base?.second,
            lnurl_description = data.lnurl_description,
            lnurl_metadata_type = data.lnurl_metadata?.first,
            lnurl_metadata_blob = data.lnurl_metadata?.second,
            lnurl_successAction_type = data.lnurl_successAction?.first,
            lnurl_successAction_blob = data.lnurl_successAction?.second,
            user_description = data.user_description,
            user_notes = data.user_notes,
            modified_at = data.modified_at
        )
    }

    fun getMetadata(
        id: WalletPaymentId,
        options: WalletPaymentFetchOptions
    ): WalletPaymentMetadata? {
        // Optimization notes:
        // We have a reason to optimize fetching descriptions, because it's commonly used.
        // However, at this point, other custom options are uncommon.
        // So we're not optimizing other combinations at this point,
        // but the possibility is there for future use.
        return when (options) {
            WalletPaymentFetchOptions.None -> {
                null
            }
            WalletPaymentFetchOptions.Descriptions -> {
                getMetadataDescriptions(id)
            }
            else -> {
                getMetadataAll(id)
            }
        }
    }

    private fun getMetadataDescriptions(id: WalletPaymentId): WalletPaymentMetadata? {
        return queries.fetchDescriptions(
            type = id.dbType.value,
            id = id.dbId,
            mapper = ::mapDescriptions
        ).executeAsOneOrNull()
    }

    private fun getMetadataAll(id: WalletPaymentId): WalletPaymentMetadata? {
        return queries.fetchMetadata(
            type = id.dbType.value,
            id = id.dbId,
            mapper = ::mapAll
        ).executeAsOneOrNull()
    }

    fun updateUserInfo(
        id: WalletPaymentId,
        userDescription: String?,
        userNotes: String?
    ) {
        database.transaction {
            val rowExists = queries.hasMetadata(
                type = id.dbType.value,
                id = id.dbId
            ).executeAsOne() > 0
            val modifiedAt = currentTimestampMillis()
            if (rowExists) {
                queries.updateUserInfo(
                    type = id.dbType.value,
                    id = id.dbId,
                    user_description = userDescription,
                    user_notes = userNotes,
                    modified_at = modifiedAt
                )
            } else {
                queries.addMetadata(
                    type = id.dbType.value,
                    id = id.dbId,
                    lnurl_base_type = null,
                    lnurl_base_blob = null,
                    lnurl_description = null,
                    lnurl_metadata_type = null,
                    lnurl_metadata_blob = null,
                    lnurl_successAction_type = null,
                    lnurl_successAction_blob = null,
                    user_description = userDescription,
                    user_notes = userNotes,
                    modified_at = modifiedAt
                )
            }
            didUpdateWalletPaymentMetadata(id, database)
        }
    }

    companion object {
        fun mapDescriptions(
            lnurl_description: String?,
            user_description: String?
        ): WalletPaymentMetadata {
            return WalletPaymentMetadata(
                userDescription = user_description,
                lnurl = if (lnurl_description != null) {
                    LnurlPayMetadata.placeholder(lnurl_description)
                } else null
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun mapAll(
            type: Long,
            id: String,
            lnurl_base_type: LNUrlBase.TypeVersion?,
            lnurl_base_blob: ByteArray?,
            lnurl_description: String?,
            lnurl_metadata_type: LNUrlMetadata.TypeVersion?,
            lnurl_metadata_blob: ByteArray?,
            lnurl_successAction_type: LNUrlSuccessAction.TypeVersion?,
            lnurl_successAction_blob: ByteArray?,
            user_description: String?,
            user_notes: String?,
            modified_at: Long?
        ): WalletPaymentMetadata {
            val lnurlBase =
                if (lnurl_base_type != null && lnurl_base_blob != null) {
                    Pair(lnurl_base_type, lnurl_base_blob)
                } else null

            val lnurlMetadata =
                if (lnurl_metadata_type != null && lnurl_metadata_blob != null) {
                    Pair(lnurl_metadata_type, lnurl_metadata_blob)
                } else null

            val lnurlSuccesssAction =
                if (lnurl_successAction_type != null && lnurl_successAction_blob != null) {
                    Pair(lnurl_successAction_type, lnurl_successAction_blob)
                } else null

            return WalletPaymentMetadataRow(
                lnurl_base = lnurlBase,
                lnurl_metadata = lnurlMetadata,
                lnurl_successAction = lnurlSuccesssAction,
                lnurl_description = lnurl_description,
                user_description = user_description,
                user_notes = user_notes,
                modified_at = modified_at
            ).deserialize()
        }
    }
}

fun LnurlPayMetadata.Companion.placeholder(description: String) = LnurlPayMetadata(
    pay = LNUrl.Pay(
        lnurl = Url("https://phoenix.acinq.co/"),
        callback = Url("https://phoenix.acinq.co/"),
        minSendable = MilliSatoshi(0),
        maxSendable = MilliSatoshi(0),
        metadata = LNUrl.Pay.Metadata(
            raw = "",
            plainText = description,
            longDesc = null,
            imageJpg = null,
            imagePng = null,
            unknown = null
        ),
        maxCommentLength = null
    ),
    description = description,
    successAction = null
)