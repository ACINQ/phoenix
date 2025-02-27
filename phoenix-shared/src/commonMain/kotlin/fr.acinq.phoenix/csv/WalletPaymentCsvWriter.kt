/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.csv

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.*
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.data.WalletPaymentMetadata
import kotlinx.datetime.Instant

class WalletPaymentCsvWriter(val configuration: Configuration) : CsvWriter() {

    data class Configuration(
        val includesFiat: Boolean,
        val includesDescription: Boolean,
        val includesNotes: Boolean,
        val includesOriginDestination: Boolean,
    )

    private val FIELD_DATE = "date"
    private val FIELD_ID = "id"
    private val FIELD_TYPE = "type"
    private val FIELD_AMOUNT_MSAT = "amount_msat"
    private val FIELD_AMOUNT_FIAT = "amount_fiat"
    private val FIELD_FEE_CREDIT_MSAT = "fee_credit_msat"
    private val FIELD_MINING_FEE_SAT = "mining_fee_sat"
    private val FIELD_MINING_FEE_FIAT = "mining_fee_fiat"
    private val FIELD_SERVICE_FEE_MSAT = "service_fee_msat"
    private val FIELD_SERVICE_FEE_FIAT = "service_fee_fiat"
    private val FIELD_PAYMENT_HASH = "payment_hash"
    private val FIELD_TX_ID = "tx_id"
    private val FIELD_DESTINATION = "destination"
    private val FIELD_DESCRIPTION = "description"

    init {
        addRow(
            FIELD_DATE,
            FIELD_ID,
            FIELD_TYPE,
            FIELD_AMOUNT_MSAT,
            FIELD_AMOUNT_FIAT,
            FIELD_FEE_CREDIT_MSAT,
            FIELD_MINING_FEE_SAT,
            FIELD_MINING_FEE_FIAT,
            FIELD_SERVICE_FEE_MSAT,
            FIELD_SERVICE_FEE_FIAT,
            FIELD_PAYMENT_HASH,
            FIELD_TX_ID,
            FIELD_DESTINATION,
            FIELD_DESCRIPTION
        )
    }

    @Suppress("EnumEntryName")
    enum class Type {
        legacy_swap_in,
        legacy_swap_out,
        legacy_pay_to_open,
        swap_in,
        swap_out,
        fee_bumping,
        lightning_received,
        lightning_sent,
        liquidity_purchase,
        channel_close,
    }

    data class Details(
        val type: Type,
        val amount: MilliSatoshi,
        val feeCredit: MilliSatoshi,
        val miningFee: Satoshi,
        val serviceFee: MilliSatoshi,
        val paymentHash: ByteVector32?,
        val txId: TxId?,
        val destination: String? = null,
        val description: String? = null,
    )

    private fun addRow(
        timestamp: Long,
        id: UUID,
        details: Details,
        metadata: WalletPaymentMetadata?,
    ) {
        val dateStr = Instant.fromEpochMilliseconds(timestamp).toString() // ISO-8601 format
        val originalFiat = metadata?.originalFiat
        addRow(
            dateStr,
            id.toString(),
            details.type.toString(),
            details.amount.msat.toString(),
            if (configuration.includesFiat) convertToFiat(details.amount, originalFiat) else "",
            details.feeCredit.msat.toString(),
            details.miningFee.sat.toString(),
            if (configuration.includesFiat) convertToFiat(details.miningFee.toMilliSatoshi(), originalFiat) else "",
            details.serviceFee.msat.toString(),
            if (configuration.includesFiat) convertToFiat(details.serviceFee, originalFiat) else "",
            details.paymentHash?.toHex() ?: "",
            if (configuration.includesOriginDestination) details.txId?.toString() ?: "" else "",
            if (configuration.includesDescription) listOf(
                details.description, metadata?.userDescription, metadata?.userNotes, metadata?.lnurl?.pay?.metadata?.longDesc
            ).mapNotNull { it.takeIf { !it.isNullOrBlank() } }.joinToString("\n---\n") else "",
        )
    }

    @Suppress("DEPRECATION")
    fun add(payment: WalletPayment, metadata: WalletPaymentMetadata?) {
        val timestamp = payment.completedAt ?: payment.createdAt
        val id = payment.id

        val details: Details? = when (payment) {
            is LightningIncomingPayment -> Details(
                type = Type.lightning_received,
                amount = payment.amountReceived,
                feeCredit = payment.parts.filterIsInstance<LightningIncomingPayment.Part.FeeCredit>().map { it.amountReceived }.sum() - (payment.liquidityPurchaseDetails?.feeCreditUsed ?: 0.msat),
                miningFee = payment.liquidityPurchaseDetails?.miningFee ?: 0.sat,
                serviceFee = payment.liquidityPurchaseDetails?.purchase?.fees?.serviceFee?.toMilliSatoshi() ?: 0.msat,
                paymentHash = payment.paymentHash,
                txId = payment.liquidityPurchaseDetails?.txId,
                description = (payment as? Bolt11IncomingPayment)?.paymentRequest?.description
            )

            is LegacySwapInIncomingPayment -> Details(
                type = Type.legacy_swap_in,
                amount = payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.fees.truncateToSatoshi(),
                serviceFee = 0.msat,
                paymentHash = null,
                txId = null,
                destination = payment.address
            )

            is LegacyPayToOpenIncomingPayment -> Details(
                type = Type.legacy_pay_to_open,
                amount = payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.parts.filterIsInstance<LegacyPayToOpenIncomingPayment.Part.OnChain>().map { it.miningFee }.sum(),
                serviceFee = payment.parts.filterIsInstance<LegacyPayToOpenIncomingPayment.Part.OnChain>().map { it.serviceFee }.sum(),
                paymentHash = payment.paymentHash,
                txId = payment.parts.filterIsInstance<LegacyPayToOpenIncomingPayment.Part.OnChain>().map { it.txId }.firstOrNull(),
                description = (payment.origin as? LegacyPayToOpenIncomingPayment.Origin.Invoice)?.paymentRequest?.description
            )

            is OnChainIncomingPayment -> Details(
                type = Type.swap_in,
                amount = payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.miningFee,
                serviceFee = payment.serviceFee,
                paymentHash = null,
                txId = payment.txId
            )

            is LightningOutgoingPayment -> when (val details = payment.details) {
                is LightningOutgoingPayment.Details.Normal -> Details(
                    type = Type.lightning_sent,
                    amount = -payment.amount,
                    feeCredit = 0.msat,
                    miningFee = 0.sat,
                    serviceFee = payment.fees,
                    paymentHash = payment.paymentHash,
                    txId = null,
                    destination = details.paymentRequest.nodeId.toHex(),
                    description = details.paymentRequest.description
                )

                is LightningOutgoingPayment.Details.SwapOut -> Details(
                    type = Type.legacy_swap_out,
                    amount = -payment.amount,
                    feeCredit = 0.msat,
                    miningFee = details.swapOutFee,
                    serviceFee = 0.msat,
                    paymentHash = null,
                    txId = null,
                    destination = details.address
                )

                is LightningOutgoingPayment.Details.Blinded -> Details(
                    type = Type.lightning_sent,
                    amount = -payment.amount,
                    feeCredit = 0.msat,
                    miningFee = 0.sat,
                    serviceFee = payment.fees,
                    paymentHash = payment.paymentHash,
                    txId = null,
                    description = details.paymentRequest.description
                )
            }

            is SpliceOutgoingPayment -> Details(
                type = Type.swap_out,
                amount = -payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.miningFee,
                serviceFee = 0.msat,
                paymentHash = null,
                txId = payment.txId,
                destination = payment.address
            )

            is ChannelCloseOutgoingPayment -> Details(
                type = Type.channel_close,
                amount = -payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.miningFee,
                serviceFee = 0.msat,
                paymentHash = null,
                txId = payment.txId,
                destination = payment.address
            )

            is SpliceCpfpOutgoingPayment -> Details(
                type = Type.fee_bumping,
                amount = -payment.amount,
                feeCredit = 0.msat,
                miningFee = payment.miningFee,
                serviceFee = 0.msat,
                paymentHash = null,
                txId = payment.txId
            )

            is AutomaticLiquidityPurchasePayment -> if (payment.incomingPaymentReceivedAt == null) {
                Details(
                    type = Type.liquidity_purchase,
                    amount = -payment.amount,
                    feeCredit = -payment.liquidityPurchaseDetails.feeCreditUsed,
                    miningFee = payment.miningFee,
                    serviceFee = payment.serviceFee,
                    paymentHash = null,
                    txId = payment.txId
                )
            } else {
                // If the corresponding Lightning payment was received, then liquidity fees will be included in the Lightning payment
                null
            }

            is ManualLiquidityPurchasePayment -> Details(
                type = Type.liquidity_purchase,
                amount = -payment.amount,
                feeCredit = -payment.liquidityPurchaseDetails.feeCreditUsed,
                miningFee = payment.miningFee,
                serviceFee = payment.serviceFee,
                paymentHash = null,
                txId = payment.txId
            )
        }

        details?.let { addRow(timestamp, id, it, metadata) }
    }

}