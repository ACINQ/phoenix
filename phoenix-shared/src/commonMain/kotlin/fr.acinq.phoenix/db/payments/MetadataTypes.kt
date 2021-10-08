package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.data.LnurlPayMetadata
import fr.acinq.phoenix.data.WalletPaymentMetadata
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import org.kodein.memory.util.freeze

/**
 * Represents the data stored in the `payments_metadata` table, within columns:
 * - lnurl_base_type
 * - lnurl_base_blob
 */
sealed class LNUrlBase {

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
    ): LNUrlBase() {
        constructor(pay: LNUrl.Pay): this(
            lnurl = pay.lnurl.toString(),
            callback = pay.callback.toString(),
            minSendableMsat = pay.minSendable.msat,
            maxSendableMsat = pay.maxSendable.msat,
            maxCommentLength = pay.maxCommentLength
        )

        fun unwrap(metadata: LNUrl.Pay.Metadata) = LNUrl.Pay(
            lnurl = Url(this.lnurl),
            callback = Url(this.callback),
            minSendable = MilliSatoshi(this.minSendableMsat),
            maxSendable = MilliSatoshi(this.maxSendableMsat),
            maxCommentLength = this.maxCommentLength,
            metadata = metadata
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(typeVersion: TypeVersion, blob: ByteArray): LNUrlBase {
            return when (typeVersion) {
                TypeVersion.PAY_V0 -> {
                    Cbor.decodeFromByteArray<Pay>(blob)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(pay: LNUrl.Pay): Pair<TypeVersion, ByteArray> {
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
sealed class LNUrlMetadata {

    enum class TypeVersion {
        PAY_V0
    }

    @Serializable
    data class PayMetadata(
        val raw: String
    ): LNUrlMetadata() {
        constructor(metadata: LNUrl.Pay.Metadata): this(
            raw = metadata.raw
        )

        fun unwrap(): LNUrl.Pay.Metadata {
            return LNUrl.Helper.decodeLNUrlPayMetadata(this.raw)
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(typeVersion: TypeVersion, blob: ByteArray): LNUrlMetadata {
            return when (typeVersion) {
                TypeVersion.PAY_V0 -> {
                    Cbor.decodeFromByteArray<PayMetadata>(blob)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(metadata: LNUrl.Pay.Metadata): Pair<TypeVersion, ByteArray> {
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
sealed class LNUrlSuccessAction {

    enum class TypeVersion {
        MESSAGE_V0,
        URL_V0,
        AES_V0
    }

    @Serializable
    data class Message(
        val message: String
    ): LNUrlSuccessAction() {
        constructor(successAction: LNUrl.PayInvoice.SuccessAction.Message): this(
            message = successAction.message
        )

        fun unwrap() = LNUrl.PayInvoice.SuccessAction.Message(
            message = this.message
        )
    }

    @Serializable
    data class Url(
        val description: String,
        val url: String
    ): LNUrlSuccessAction() {
        constructor(successAction: LNUrl.PayInvoice.SuccessAction.Url): this(
            description = successAction.description,
            url = successAction.url.toString()
        )

        fun unwrap() = LNUrl.PayInvoice.SuccessAction.Url(
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
    ): LNUrlSuccessAction() {
        constructor(successAction: LNUrl.PayInvoice.SuccessAction.Aes): this(
            description = successAction.description,
            ciphertext = successAction.ciphertext.toByteArray(),
            iv = successAction.iv.toByteArray()
        )

        fun unwrap() = LNUrl.PayInvoice.SuccessAction.Aes(
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
        ): LNUrl.PayInvoice.SuccessAction {
            return when (typeVersion) {
                TypeVersion.MESSAGE_V0 -> {
                    Cbor.decodeFromByteArray<Message>(blob).unwrap()
                }
                TypeVersion.URL_V0 -> {
                    Cbor.decodeFromByteArray<Url>(blob).unwrap()
                }
                TypeVersion.AES_V0 -> {
                    Cbor.decodeFromByteArray<Aes>(blob).unwrap()
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(successAction: LNUrl.PayInvoice.SuccessAction): Pair<TypeVersion, ByteArray> {
            return when (successAction) {
                is LNUrl.PayInvoice.SuccessAction.Message -> {
                    val wrapper = Message(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.MESSAGE_V0, blob)
                }
                is LNUrl.PayInvoice.SuccessAction.Url -> {
                    val wrapper = Url(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.URL_V0, blob)
                }
                is LNUrl.PayInvoice.SuccessAction.Aes -> {
                    val wrapper = Aes(successAction)
                    val blob = Cbor.encodeToByteArray(wrapper)
                    Pair(TypeVersion.AES_V0, blob)
                }
            }
        }
    }
}

data class WalletPaymentMetadataRow(
    val lnurl_base: Pair<LNUrlBase.TypeVersion, ByteArray>? = null,
    val lnurl_metadata: Pair<LNUrlMetadata.TypeVersion, ByteArray>? = null,
    val lnurl_successAction: Pair<LNUrlSuccessAction.TypeVersion, ByteArray>? = null,
    val lnurl_description: String? = null,
    val user_description: String? = null
) {

    fun deserialize(): WalletPaymentMetadata {
        val base = lnurl_base?.let {
            when (val base = LNUrlBase.deserialize(it.first, it.second)) {
                is LNUrlBase.Pay -> {
                    lnurl_metadata?.let { (type, blob) ->
                        when (val metadata = LNUrlMetadata.deserialize(type, blob)) {
                            is LNUrlMetadata.PayMetadata -> {
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
            LNUrlSuccessAction.deserialize(it.first, it.second)
        }

        val lnurl = base?.let {
            LnurlPayMetadata(
                pay = it,
                description = lnurl_description ?: it.metadata.plainText,
                successAction = successAction
            )
        }

        return WalletPaymentMetadata(
            lnurl = lnurl,
            userDescription = user_description
        )
    }

    /**
     * This function exists because the `freeze()`
     * function isn't exposed to iOS.
     */
    fun copyAndFreeze(): WalletPaymentMetadataRow {
        return this.freeze()
    }

    companion object {
        fun serialize(
            pay: LNUrl.Pay,
            successAction: LNUrl.PayInvoice.SuccessAction?
        ): WalletPaymentMetadataRow {

            return WalletPaymentMetadataRow(
                lnurl_base = LNUrlBase.serialize(pay),
                lnurl_metadata = LNUrlMetadata.serialize(pay.metadata),
                lnurl_description = pay.metadata.plainText,
                lnurl_successAction = successAction?.let {
                    LNUrlSuccessAction.serialize(it)
                }
            )
        }
    }
}
