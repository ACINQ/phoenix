package fr.acinq.phoenix.db.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didUpdateWalletPaymentMetadata
import io.ktor.http.*

class PaymentsMetadataQueries(val database: PaymentsDatabase) {

    private val queries = database.paymentsMetadataQueries

    fun addMetadata(
        id: UUID,
        data: WalletPaymentMetadataRow
    ) {
        queries.addMetadata(
            payment_id = id,
            lnurl_base_type = data.lnurl_base?.first,
            lnurl_base_blob = data.lnurl_base?.second,
            lnurl_description = data.lnurl_description,
            lnurl_metadata_type = data.lnurl_metadata?.first,
            lnurl_metadata_blob = data.lnurl_metadata?.second,
            lnurl_successAction_type = data.lnurl_successAction?.first,
            lnurl_successAction_blob = data.lnurl_successAction?.second,
            user_description = data.user_description,
            user_notes = data.user_notes,
            modified_at = data.modified_at,
            original_fiat_type = data.original_fiat?.first,
            original_fiat_rate = data.original_fiat?.second
        )
        didUpdateWalletPaymentMetadata(id, database)
    }

    fun get(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): WalletPaymentMetadata? {
        // The following are commonly used, and so are optimized:
        // - descriptions + originalFiat
        // - descriptions
        // Other combinations are uncommon or never used, so remain unoptimized at this point.
        return when (options - WalletPaymentFetchOptions.Contact) {
            WalletPaymentFetchOptions.None -> {
                null
            }
            WalletPaymentFetchOptions.Descriptions + WalletPaymentFetchOptions.OriginalFiat -> {
                getDescriptionsAndOriginalFiat(id)
            }
            WalletPaymentFetchOptions.Descriptions -> {
                getDescriptions(id)
            }
            else -> {
                get(id)
            }
        }
    }

    private fun getDescriptions(id: UUID): WalletPaymentMetadata? {
        return queries.getDescriptions(payment_id = id, mapper = ::mapDescriptions).executeAsOneOrNull()
    }

    private fun getDescriptionsAndOriginalFiat(id: UUID): WalletPaymentMetadata? {
        return queries.getDescriptionsAndOriginalFiat(payment_id = id, mapper = ::mapDescriptionsAndOriginalFiat).executeAsOneOrNull()
    }

    private fun get(id: UUID): WalletPaymentMetadata? {
        return queries.get(payment_id = id, mapper = ::mapAll).executeAsOneOrNull()
    }

    fun updateUserInfo(
        id: UUID,
        userDescription: String?,
        userNotes: String?
    ) {
        database.transaction {
            val rowExists = queries.hasMetadata(payment_id = id).executeAsOne() > 0
            val modifiedAt = currentTimestampMillis()
            if (rowExists) {
                queries.updateUserInfo(
                    payment_id = id,
                    user_description = userDescription,
                    user_notes = userNotes,
                    modified_at = modifiedAt
                )
            } else {
                queries.addMetadata(
                    payment_id = id,
                    lnurl_base_type = null,
                    lnurl_base_blob = null,
                    lnurl_description = null,
                    lnurl_metadata_type = null,
                    lnurl_metadata_blob = null,
                    lnurl_successAction_type = null,
                    lnurl_successAction_blob = null,
                    user_description = userDescription,
                    user_notes = userNotes,
                    modified_at = modifiedAt,
                    original_fiat_type = null,
                    original_fiat_rate = null
                )
            }
            didUpdateWalletPaymentMetadata(id, database)
        }
    }

    companion object {
        fun mapDescriptions(
            lnurl_description: String?,
            user_description: String?,
            modified_at: Long?
        ): WalletPaymentMetadata {
            return WalletPaymentMetadata(
                userDescription = user_description,
                lnurl = lnurl_description?.let { LnurlPayMetadata.placeholder(it) },
                modifiedAt = modified_at
            )
        }

        fun mapDescriptionsAndOriginalFiat(
            lnurl_description: String?,
            user_description: String?,
            modified_at: Long?,
            original_fiat_type: String?,
            original_fiat_rate: Double?
        ): WalletPaymentMetadata {
            val originalFiat =
                if (original_fiat_type != null && original_fiat_rate != null) {
                    FiatCurrency.valueOfOrNull(original_fiat_type)?.let { fiatCurrency ->
                        ExchangeRate.BitcoinPriceRate(
                            fiatCurrency = fiatCurrency,
                            price = original_fiat_rate,
                            source = "originalFiat",
                            timestampMillis = 0
                        )
                    }
                } else null

            return WalletPaymentMetadata(
                lnurl = lnurl_description?.let { LnurlPayMetadata.placeholder(it) },
                originalFiat = originalFiat,
                userDescription = user_description,
                modifiedAt = modified_at
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun mapAll(
            id: UUID,
            lnurl_base_type: LnurlBase.TypeVersion?,
            lnurl_base_blob: ByteArray?,
            lnurl_description: String?,
            lnurl_metadata_type: LnurlMetadata.TypeVersion?,
            lnurl_metadata_blob: ByteArray?,
            lnurl_successAction_type: LnurlSuccessAction.TypeVersion?,
            lnurl_successAction_blob: ByteArray?,
            user_description: String?,
            user_notes: String?,
            modified_at: Long?,
            original_fiat_type: String?,
            original_fiat_rate: Double?
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

            val originalFiat =
                if (original_fiat_type != null && original_fiat_rate != null) {
                    Pair(original_fiat_type, original_fiat_rate)
                } else null

            return WalletPaymentMetadataRow(
                lnurl_base = lnurlBase,
                lnurl_metadata = lnurlMetadata,
                lnurl_successAction = lnurlSuccesssAction,
                lnurl_description = lnurl_description,
                original_fiat = originalFiat,
                user_description = user_description,
                user_notes = user_notes,
                modified_at = modified_at
            ).deserialize()
        }
    }
}

fun LnurlPayMetadata.Companion.placeholder(description: String) = LnurlPayMetadata(
    pay = LnurlPay.Intent(
        initialUrl = Url("https://phoenix.acinq.co/"),
        callback = Url("https://phoenix.acinq.co/"),
        minSendable = MilliSatoshi(0),
        maxSendable = MilliSatoshi(0),
        metadata = LnurlPay.Intent.Metadata(
            raw = "",
            plainText = description,
            longDesc = null,
            imageJpg = null,
            imagePng = null,
            identifier = null,
            email = null,
            unknown = null
        ),
        maxCommentLength = null
    ),
    description = description,
    successAction = null
)