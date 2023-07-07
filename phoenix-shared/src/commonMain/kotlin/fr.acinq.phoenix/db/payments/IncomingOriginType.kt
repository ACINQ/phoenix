/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:UseSerializers(
    OutpointSerializer::class,
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.db.payments.DbTypesHelper.decodeBlob
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.OutpointSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json


enum class IncomingOriginTypeVersion {
    KEYSEND_V0,
    INVOICE_V0,
    SWAPIN_V0,
    ONCHAIN_V0,
}

sealed class IncomingOriginData {

    sealed class KeySend : IncomingOriginData() {
        @Serializable
        @SerialName("KEYSEND_V0")
        object V0 : KeySend()
    }

    sealed class Invoice : IncomingOriginData() {
        @Serializable
        data class V0(val paymentRequest: String) : Invoice()
    }

    sealed class SwapIn : IncomingOriginData() {
        @Serializable
        data class V0(val address: String?) : SwapIn()
    }

    sealed class OnChain : IncomingOriginData() {
        @Serializable
        data class V0(@Serializable val txId: ByteVector32, val outpoints: List<@Serializable OutPoint>) : SwapIn()
    }

    companion object {
        fun deserialize(typeVersion: IncomingOriginTypeVersion, blob: ByteArray): IncomingPayment.Origin = decodeBlob(blob) { json, format ->
            when (typeVersion) {
                IncomingOriginTypeVersion.KEYSEND_V0 -> IncomingPayment.Origin.KeySend
                IncomingOriginTypeVersion.INVOICE_V0 -> format.decodeFromString<Invoice.V0>(json).let { IncomingPayment.Origin.Invoice(PaymentRequest.read(it.paymentRequest)) }
                IncomingOriginTypeVersion.SWAPIN_V0 -> format.decodeFromString<SwapIn.V0>(json).let { IncomingPayment.Origin.SwapIn(it.address) }
                IncomingOriginTypeVersion.ONCHAIN_V0 -> format.decodeFromString<OnChain.V0>(json).let {
                    IncomingPayment.Origin.OnChain(it.txId, it.outpoints.toSet())
                }
            }
        }
    }
}

fun IncomingPayment.Origin.mapToDb(): Pair<IncomingOriginTypeVersion, ByteArray> = when (this) {
    is IncomingPayment.Origin.KeySend -> IncomingOriginTypeVersion.KEYSEND_V0 to
            Json.encodeToString(IncomingOriginData.KeySend.V0).toByteArray(Charsets.UTF_8)
    is IncomingPayment.Origin.Invoice -> IncomingOriginTypeVersion.INVOICE_V0 to
            Json.encodeToString(IncomingOriginData.Invoice.V0(paymentRequest.write())).toByteArray(Charsets.UTF_8)
    is IncomingPayment.Origin.SwapIn -> IncomingOriginTypeVersion.SWAPIN_V0 to
            Json.encodeToString(IncomingOriginData.SwapIn.V0(address)).toByteArray(Charsets.UTF_8)
    is IncomingPayment.Origin.OnChain -> IncomingOriginTypeVersion.ONCHAIN_V0 to
            Json.encodeToString(IncomingOriginData.OnChain.V0(txid, localInputs.toList())).toByteArray(Charsets.UTF_8)
}
