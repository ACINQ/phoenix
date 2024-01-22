package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.cloud.cborSerializer
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.data.lnurl.LnurlPay
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor

/**
 * Represents the data stored in the `payments_metadata` table, within columns:
 * - lnurl_base_type
 * - lnurl_base_blob
 */
sealed class LnurlBase {

    enum class TypeVersion {
        PAY_V0
    }

    @Serializable
    data class Pay(
        val lnurl: String,
        val callback: String,
        val minSendableMsat: Long,
        val maxSendableMsat: Long,
        val maxCommentLength: Long?
    ): LnurlBase() {
        constructor(intent: LnurlPay.Intent): this(
            lnurl = intent.initialUrl.toString(),
            callback = intent.callback.toString(),
            minSendableMsat = intent.minSendable.msat,
            maxSendableMsat = intent.maxSendable.msat,
            maxCommentLength = intent.maxCommentLength
        )

        fun unwrap(metadata: LnurlPay.Intent.Metadata) = LnurlPay.Intent(
            initialUrl = Url(this.lnurl),
            callback = Url(this.callback),
            minSendable = MilliSatoshi(this.minSendableMsat),
            maxSendable = MilliSatoshi(this.maxSendableMsat),
            maxCommentLength = this.maxCommentLength,
            metadata = metadata
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(typeVersion: TypeVersion, blob: ByteArray): LnurlBase {
            return when (typeVersion) {
                TypeVersion.PAY_V0 -> {
                    cborSerializer().decodeFromByteArray<Pay>(blob)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(pay: LnurlPay.Intent): Pair<TypeVersion, ByteArray> {
            val wrapper = Pay(pay)
            val blob = Cbor.encodeToByteArray(wrapper)
            return Pair(TypeVersion.PAY_V0, blob)
        }
    }
}

/**
 * Represents the data stored in the `payments_metadata` table, within columns:
 * - lnurl_metadata_type
 * - lnurl_metadata_blob
 */
sealed class LnurlMetadata {

    enum class TypeVersion {
        PAY_V0
    }

    @Serializable
    data class PayMetadata(
        val raw: String
    ): LnurlMetadata() {
        constructor(metadata: LnurlPay.Intent.Metadata): this(
            raw = metadata.raw
        )

        fun unwrap(): LnurlPay.Intent.Metadata {
            return LnurlPay.parseMetadata(this.raw)
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(typeVersion: TypeVersion, blob: ByteArray): LnurlMetadata {
            return when (typeVersion) {
                TypeVersion.PAY_V0 -> {
                    cborSerializer().decodeFromByteArray<PayMetadata>(blob)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(metadata: LnurlPay.Intent.Metadata): Pair<TypeVersion, ByteArray> {
            val wrapper = PayMetadata(metadata)
            val blob = Cbor.encodeToByteArray(wrapper)
            return Pair(TypeVersion.PAY_V0, blob)
        }
    }
}

/**
 * Represents the data stored in the `payments_metadata` table, within columns:
 * - lnurl_successAction_type
 * - lnurl_successAction_blob
 */
sealed class LnurlSuccessAction {

    enum class TypeVersion {
        MESSAGE_V0,
        URL_V0,
        AES_V0
    }

    @Serializable
    data class Message(
        val message: String
    ): LnurlSuccessAction() {
        constructor(successAction: LnurlPay.Invoice.SuccessAction.Message): this(
            message = successAction.message
        )

        fun unwrap() = LnurlPay.Invoice.SuccessAction.Message(
            message = this.message
        )
    }

    @Serializable
    data class Url(
        val description: String,
        val url: String
    ): LnurlSuccessAction() {
        constructor(successAction: LnurlPay.Invoice.SuccessAction.Url): this(
            description = successAction.description,
            url = successAction.url.toString()
        )

        fun unwrap() = LnurlPay.Invoice.SuccessAction.Url(
            description = this.description,
            url = Url(this.url)
        )
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class Aes(
        val description: String,
        @ByteString
        val ciphertext: ByteArray,
        @ByteString
        val iv: ByteArray
    ): LnurlSuccessAction() {
        constructor(successAction: LnurlPay.Invoice.SuccessAction.Aes): this(
            description = successAction.description,
            ciphertext = successAction.ciphertext.toByteArray(),
            iv = successAction.iv.toByteArray()
        )

        fun unwrap() = LnurlPay.Invoice.SuccessAction.Aes(
            description = this.description,
            ciphertext = ByteVector(this.ciphertext),
            iv = ByteVector(this.iv)
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: TypeVersion,
            blob: ByteArray
        ): LnurlPay.Invoice.SuccessAction {
            return when (typeVersion) {
                TypeVersion.MESSAGE_V0 -> {
                    cborSerializer().decodeFromByteArray<Message>(blob).unwrap()
                }
                TypeVersion.URL_V0 -> {
                    cborSerializer().decodeFromByteArray<Url>(blob).unwrap()
                }
                TypeVersion.AES_V0 -> {
                    cborSerializer().decodeFromByteArray<Aes>(blob).unwrap()
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(successAction: LnurlPay.Invoice.SuccessAction): Pair<TypeVersion, ByteArray> {
            return when (successAction) {
                is LnurlPay.Invoice.SuccessAction.Message -> {
                    val wrapper = Message(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.MESSAGE_V0, blob)
                }
                is LnurlPay.Invoice.SuccessAction.Url -> {
                    val wrapper = Url(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.URL_V0, blob)
                }
                is LnurlPay.Invoice.SuccessAction.Aes -> {
                    val wrapper = Aes(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.AES_V0, blob)
                }
            }
        }
    }
}

data class WalletPaymentMetadataRow(
    val lnurl_base: Pair<LnurlBase.TypeVersion, ByteArray>? = null,
    val lnurl_metadata: Pair<LnurlMetadata.TypeVersion, ByteArray>? = null,
    val lnurl_successAction: Pair<LnurlSuccessAction.TypeVersion, ByteArray>? = null,
    val lnurl_description: String? = null,
    val original_fiat: Pair<String, Double>? = null,
    val user_description: String? = null,
    val user_notes: String? = null,
    val modified_at: Long? = null
) {

    fun deserialize(): WalletPaymentMetadata {
        val base = lnurl_base?.let { (baseType, baseBlob) ->
            when (val base = LnurlBase.deserialize(baseType, baseBlob)) {
                is LnurlBase.Pay -> {
                    lnurl_metadata?.let { (metaType, metaBlob) ->
                        when (val metadata = LnurlMetadata.deserialize(metaType, metaBlob)) {
                            is LnurlMetadata.PayMetadata -> {
                                metadata.unwrap()
                            }
                        }
                    }?.let { metadata ->
                        base.unwrap(metadata)
                    }
                }
            }
        }

        val successAction = lnurl_successAction?.let {
            LnurlSuccessAction.deserialize(it.first, it.second)
        }

        val lnurl = base?.let {
            LnurlPayMetadata(
                pay = it,
                description = lnurl_description ?: it.metadata.plainText,
                successAction = successAction
            )
        }

        val originalFiat = original_fiat?.let {
            FiatCurrency.valueOfOrNull(it.first)?.let { fiatCurrency ->
                ExchangeRate.BitcoinPriceRate(
                    fiatCurrency = fiatCurrency,
                    price = it.second,
                    source = "originalFiat",
                    timestampMillis = 0
                )
            }
        }

        return WalletPaymentMetadata(
            lnurl = lnurl,
            originalFiat = originalFiat,
            userDescription = user_description,
            userNotes = user_notes,
            modifiedAt = modified_at
        )
    }

    /**
     * Returns true if all columns are null (excluding modified_at).
     */
    fun isEmpty(): Boolean {
        return lnurl_base == null
            && lnurl_metadata == null
            && lnurl_successAction == null
            && lnurl_description == null
            && original_fiat == null
            && user_description == null
            && user_notes == null
    }

    companion object {
        fun serialize(
            metadata: WalletPaymentMetadata
        ): WalletPaymentMetadataRow? {

            var lnurlBase: Pair<LnurlBase.TypeVersion, ByteArray>? = null
            var lnurlMetadata: Pair<LnurlMetadata.TypeVersion, ByteArray>? = null
            var lnurlSuccessAction: Pair<LnurlSuccessAction.TypeVersion, ByteArray>? = null
            var lnurlDescription: String? = null

            metadata.lnurl?.let {
                lnurlBase = LnurlBase.serialize(it.pay)
                lnurlMetadata = LnurlMetadata.serialize(it.pay.metadata)
                lnurlSuccessAction = it.successAction?.let { successAction ->
                    LnurlSuccessAction.serialize(successAction)
                }
                lnurlDescription = it.pay.metadata.plainText
            }

            val originalFiat = metadata.originalFiat?.let {
                Pair(it.fiatCurrency.name, it.price)
            }

            val row = WalletPaymentMetadataRow(
                lnurl_base = lnurlBase,
                lnurl_metadata = lnurlMetadata,
                lnurl_successAction = lnurlSuccessAction,
                lnurl_description = lnurlDescription,
                original_fiat = originalFiat,
                user_description = metadata.userDescription,
                user_notes = metadata.userNotes,
                modified_at = metadata.modifiedAt
            )

            return if (row.isEmpty()) null else row
        }
    }
}
