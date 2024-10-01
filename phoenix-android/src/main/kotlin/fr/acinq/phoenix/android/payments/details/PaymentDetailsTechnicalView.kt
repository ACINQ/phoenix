/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.details

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.InlineTransactionLink
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.txUrl
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.amountFeeCredit
import fr.acinq.phoenix.utils.extensions.relatedPaymentIds


@Composable
fun PaymentDetailsTechnicalView(
    data: WalletPaymentInfo
) {
    val payment = data.payment
    // only use original fiat rate if the payment is older than 3 days
    val rateThen = data.metadata.originalFiat?.takeIf { currentTimestampMillis() - data.payment.createdAt >= 3 * DateUtils.DAY_IN_MILLIS }

    when (payment) {
        is IncomingPayment -> {
            TechnicalCard {
                HeaderForIncoming(payment)
            }
            TechnicalCard {
                TimestampSection(payment)
            }
            TechnicalCard {
                AmountSection(payment, rateThen)
                DetailsForIncoming(payment)
            }

            val receivedWith = payment.received?.receivedWith
            if (!receivedWith.isNullOrEmpty()) {
                CardHeader(text = stringResource(id = R.string.paymentdetails_parts_label, receivedWith.size))
                receivedWith.forEach {
                    TechnicalCard {
                        when (it) {
                            is IncomingPayment.ReceivedWith.LightningPayment -> ReceivedWithLightning(it, rateThen)
                            is IncomingPayment.ReceivedWith.NewChannel -> ReceivedWithNewChannel(it, rateThen)
                            is IncomingPayment.ReceivedWith.SpliceIn -> ReceivedWithSpliceIn(it, rateThen)
                            is IncomingPayment.ReceivedWith.AddedToFeeCredit -> ReceivedWithFeeCredit(it, rateThen)
                        }
                    }
                }
            }
        }
        is OutgoingPayment -> {
            TechnicalCard {
                HeaderForOutgoing(payment)
            }
            TechnicalCard {
                TimestampSection(payment)
            }
            TechnicalCard {
                AmountSection(payment, rateThen)
                when (payment) {
                    is LightningOutgoingPayment -> DetailsForLightningOutgoingPayment(payment)
                    is SpliceOutgoingPayment -> DetailsForSpliceOut(payment)
                    is ChannelCloseOutgoingPayment -> DetailsForChannelClose(payment)
                    is SpliceCpfpOutgoingPayment -> DetailsForCpfp(payment)
                    is InboundLiquidityOutgoingPayment -> DetailsForInboundLiquidity(payment)
                }
            }

            if (payment is LightningOutgoingPayment) {
                val successfulParts = payment.parts.filter { it.status is LightningOutgoingPayment.Part.Status.Succeeded }
                if (successfulParts.isNotEmpty()) {
                    CardHeader(text = stringResource(id = R.string.paymentdetails_parts_label, successfulParts.size))
                }
                successfulParts.forEachIndexed { index, part ->
                    TechnicalCard {
                        LightningPart(index, part, rateThen)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderForOutgoing(
    payment: OutgoingPayment
) {
    // -- payment type
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
        Text(
            when (payment) {
                is ChannelCloseOutgoingPayment -> stringResource(id = R.string.paymentdetails_closing)
                is SpliceOutgoingPayment -> stringResource(id = R.string.paymentdetails_splice_outgoing)
                is LightningOutgoingPayment -> when (payment.details) {
                    is LightningOutgoingPayment.Details.Normal -> stringResource(R.string.paymentdetails_normal_outgoing)
                    is LightningOutgoingPayment.Details.SwapOut -> stringResource(R.string.paymentdetails_swapout)
                    is LightningOutgoingPayment.Details.Blinded -> stringResource(id = R.string.paymentdetails_offer_outgoing)
                }
                is SpliceCpfpOutgoingPayment -> stringResource(id = R.string.paymentdetails_splice_cpfp_outgoing)
                is InboundLiquidityOutgoingPayment -> stringResource(id = R.string.paymentdetails_inbound_liquidity)
            }
        )
    }

    // -- status
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
        Text(
            when (payment) {
                is InboundLiquidityOutgoingPayment -> when (payment.lockedAt) {
                    null -> stringResource(R.string.paymentdetails_status_pending)
                    else -> stringResource(R.string.paymentdetails_status_success)
                }
                is OnChainOutgoingPayment -> when (payment.confirmedAt) {
                    null -> stringResource(R.string.paymentdetails_status_pending)
                    else -> stringResource(R.string.paymentdetails_status_success)
                }
                is LightningOutgoingPayment -> when (payment.status) {
                    is LightningOutgoingPayment.Status.Pending -> stringResource(R.string.paymentdetails_status_pending)
                    is LightningOutgoingPayment.Status.Completed.Succeeded -> stringResource(R.string.paymentdetails_status_success)
                    is LightningOutgoingPayment.Status.Completed.Failed -> stringResource(R.string.paymentdetails_status_failed)
                }
            }
        )
    }
}

@Composable
private fun HeaderForIncoming(
    payment: IncomingPayment
) {
    // -- payment type
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
        Text(
            text = when (payment.origin) {
                is IncomingPayment.Origin.Invoice -> stringResource(R.string.paymentdetails_normal_incoming)
                is IncomingPayment.Origin.SwapIn -> stringResource(R.string.paymentdetails_swapin)
                is IncomingPayment.Origin.OnChain -> stringResource(R.string.paymentdetails_swapin)
                is IncomingPayment.Origin.Offer -> stringResource(id = R.string.paymentdetails_offer_incoming)
            }
        )
    }

    // -- status
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
        if (payment.received == null) {
            Text(text = stringResource(id = R.string.paymentdetails_status_pending))
        } else if (payment.completedAt == null) {
            Text(text= stringResource(id = R.string.paymentdetails_status_confirming))
        } else {
            Text(text = stringResource(R.string.paymentdetails_status_success))
        }
    }
}

@Composable
private fun TimestampSection(
    payment: WalletPayment
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_created_at_label)) {
        Text(text = payment.createdAt.toAbsoluteDateTimeString())
    }
    // completion date when relevant
    payment.completedAt?.let {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_completed_at_label)) {
            Text(text = it.toAbsoluteDateTimeString())
        }
    }
    // time to completion for lightning outgoing payments
    if (payment is LightningOutgoingPayment && payment.details is LightningOutgoingPayment.Details.Normal) {
        payment.completedAt?.let {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_elapsed_label)) {
                Text(text = stringResource(id = R.string.paymentdetails_elapsed, (it - payment.createdAt).toString()))
            }
        }
    }
}

@Composable
private fun AmountSection(
    payment: WalletPayment,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    when (payment) {
        is InboundLiquidityOutgoingPayment -> {
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_liquidity_amount_label),
                amount = payment.purchase.amount.toMilliSatoshi(),
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
                amount = payment.miningFees.toMilliSatoshi(),
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
                amount = payment.purchase.fees.serviceFee.toMilliSatoshi(),
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
        }
        is OutgoingPayment -> {
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_amount_sent_label),
                amount = payment.amount,
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_fees_label),
                amount = payment.fees,
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
        }
        is IncomingPayment -> {
            TechnicalRowAmount(
                label = stringResource(R.string.paymentdetails_amount_received_label),
                amount = payment.amount,
                rateThen = rateThen,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
            payment.amountFeeCredit?.let {
                TechnicalRowAmount(
                    label = stringResource(R.string.paymentdetails_amount_fee_credit_label),
                    amount = it,
                    rateThen = rateThen,
                    mSatDisplayPolicy = MSatDisplayPolicy.SHOW
                )
            }
            val receivedWithNewChannel = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.NewChannel>() ?: emptyList()
            val receivedWithSpliceIn = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.SpliceIn>() ?: emptyList()
            if ((receivedWithNewChannel + receivedWithSpliceIn).isNotEmpty()) {
                val serviceFee = receivedWithNewChannel.map { it.serviceFee }.sum() +  receivedWithSpliceIn.map { it.serviceFee }.sum()
                val fundingFee = receivedWithNewChannel.map { it.miningFee }.sum() + receivedWithSpliceIn.map { it.miningFee }.sum()
                TechnicalRowAmount(
                    label = stringResource(id = R.string.paymentdetails_service_fees_label),
                    amount = serviceFee,
                    rateThen = rateThen,
                    mSatDisplayPolicy = MSatDisplayPolicy.SHOW
                )
                TechnicalRowAmount(
                    label = stringResource(id = R.string.paymentdetails_funding_fees_label),
                    amount = fundingFee.toMilliSatoshi(),
                    rateThen = rateThen,
                    mSatDisplayPolicy = MSatDisplayPolicy.HIDE
                )
            }
        }
    }
}

@Composable
private fun DetailsForLightningOutgoingPayment(
    payment: LightningOutgoingPayment
) {
    val details = payment.details
    val status = payment.status

    // -- details of the payment
    when (details) {
        is LightningOutgoingPayment.Details.Normal -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_pubkey_label), value = payment.recipient.toHex())
            Bolt11InvoiceSection(invoice = details.paymentRequest)
        }
        is LightningOutgoingPayment.Details.SwapOut -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_bitcoin_address_label), value = details.address)
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
        }
        is LightningOutgoingPayment.Details.Blinded -> {
            Bolt12InvoiceSection(invoice = details.paymentRequest, payerKey = details.payerKey)
        }
    }

    // -- status details
    when (status) {
        is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = status.preimage.toHex())
        }
        is LightningOutgoingPayment.Status.Completed.Failed -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
                Text(text = status.reason.toString())
            }
        }
        else -> {}
    }
}

@Composable
private fun DetailsForChannelClose(
    payment: ChannelCloseOutgoingPayment
) {
    ChannelIdRow(payment.channelId)
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_bitcoin_address_label),
        value = payment.address
    )
    TransactionRow(payment.txId)
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_closing_type_label),
        value = when (payment.closingType) {
            ChannelClosingType.Mutual -> stringResource(id = R.string.paymentdetails_closing_type_mutual)
            ChannelClosingType.Local -> stringResource(id = R.string.paymentdetails_closing_type_local)
            ChannelClosingType.Remote -> stringResource(id = R.string.paymentdetails_closing_type_remote)
            ChannelClosingType.Revoked -> stringResource(id = R.string.paymentdetails_closing_type_revoked)
            ChannelClosingType.Other -> stringResource(id = R.string.paymentdetails_closing_type_other)
        }
    )
}

@Composable
private fun DetailsForCpfp(
    payment: SpliceCpfpOutgoingPayment
) {
    TransactionRow(payment.txId)
}

@Composable
private fun DetailsForInboundLiquidity(
    payment: InboundLiquidityOutgoingPayment
) {
    TechnicalRow(label = "Purchase Type") {
        Text(text = "${
            when (payment.purchase) {
                is LiquidityAds.Purchase.Standard -> "Standard"
                is LiquidityAds.Purchase.WithFeeCredit -> "Fee credit"
            }
        } [${payment.purchase.paymentDetails.paymentType}]")
    }
    TransactionRow(payment.txId)
    ChannelIdRow(channelId = payment.channelId)
    val paymentIds = payment.relatedPaymentIds()
    val navController = navController
    paymentIds.forEach {
        TechnicalRowClickable(
            label = "Caused by",
            onClick = { navigateToPaymentDetails(navController, it, isFromEvent = false) },
        ) {
            TextWithIcon(
                text = "(incoming) ${it.dbId}",
                icon = R.drawable.ic_arrow_down_circle,
                maxLines = 1, textOverflow = TextOverflow.Ellipsis,
                space = 4.dp
            )
        }
    }
}

@Composable
private fun DetailsForSpliceOut(
    payment: SpliceOutgoingPayment
) {
    ChannelIdRow(channelId = payment.channelId, label = stringResource(id = R.string.paymentdetails_splice_out_channel_label))
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_bitcoin_address_label),
        value = payment.address
    )
    TransactionRow(payment.txId)
}

@Composable
private fun DetailsForIncoming(
    payment: IncomingPayment
) {
    // -- details about the origin of the payment
    when (val origin = payment.origin) {
        is IncomingPayment.Origin.Invoice -> {
            Bolt11InvoiceSection(invoice = origin.paymentRequest)
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = payment.preimage.toHex())
        }
        is IncomingPayment.Origin.SwapIn -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_swapin_address_label)) {
                Text(origin.address ?: stringResource(id = R.string.utils_unknown))
            }
        }
        is IncomingPayment.Origin.OnChain -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_dualswapin_tx_label)) {
                origin.localInputs.mapIndexed { index, outpoint ->
                    Row {
                        Text(text = stringResource(id = R.string.paymentdetails_dualswapin_tx_value, index + 1))
                        Spacer(modifier = Modifier.width(4.dp))
                        InlineTransactionLink(txId = outpoint.txid)
                    }
                }
            }
        }
        is IncomingPayment.Origin.Offer -> {
            Bolt12MetadataSection(metadata = origin.metadata)
        }
    }
}

@Composable
private fun ReceivedWithLightning(
    receivedWith: IncomingPayment.ReceivedWith.LightningPayment,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
        Text(text = stringResource(id = R.string.paymentdetails_received_with_lightning))
    }
    if (receivedWith.channelId != ByteVector32.Zeroes) {
        ChannelIdRow(receivedWith.channelId)
    }
    receivedWith.fundingFee?.let {
        TransactionRow(it.fundingTxId)
    }
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amountReceived, rateThen = rateThen)
}

@Composable
private fun ReceivedWithNewChannel(
    receivedWith: IncomingPayment.ReceivedWith.NewChannel,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
        Text(text = stringResource(id = R.string.paymentdetails_received_with_channel))
    }
    val channelId = receivedWith.channelId
    if (channelId != ByteVector32.Zeroes) { // backward compat
        ChannelIdRow(channelId)
    }
    TransactionRow(receivedWith.txId)
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amountReceived, rateThen = rateThen)
}

@Composable
private fun ReceivedWithSpliceIn(
    receivedWith: IncomingPayment.ReceivedWith.SpliceIn,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
        Text(text = stringResource(id = R.string.paymentdetails_received_with_splicein))
    }
    val channelId = receivedWith.channelId
    if (channelId != ByteVector32.Zeroes) { // backward compat
        ChannelIdRow(channelId)
    }
    TransactionRow(receivedWith.txId)
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amountReceived, rateThen = rateThen)
}

@Composable
private fun ReceivedWithFeeCredit(
    receivedWith: IncomingPayment.ReceivedWith.AddedToFeeCredit,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
        Text(text = stringResource(id = R.string.paymentdetails_received_with_fee_credit))
    }
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_added_to_fee_credit_label), amount = receivedWith.amountReceived, rateThen = rateThen)
}

@Composable
private fun LightningPart(
    index: Int,
    part: LightningOutgoingPayment.Part,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_label)) {
        Text("#$index")
    }
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_hops_label)) {
        part.route.forEach {
            Text("-> ${it.nextNodeId.toHex()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_sent_label), amount = part.amount, rateThen = rateThen)
}

@Composable
private fun Bolt11InvoiceSection(
    invoice: Bolt11Invoice
) {
    val requestedAmount = invoice.amount
    if (requestedAmount != null) {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
            amount = requestedAmount,
            rateThen = null
        )
    }

    val description = (invoice.description ?: invoice.descriptionHash?.toHex())?.takeIf { it.isNotBlank() }
    if (description != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_request_description_label)) {
            Text(text = description)
        }
    }
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_request_label), value = invoice.write())
}

@Composable
private fun Bolt12InvoiceSection(
    invoice: Bolt12Invoice,
    payerKey: PrivateKey,
) {
    val requestedAmount = invoice.amount
    if (requestedAmount != null) {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
            amount = requestedAmount,
            rateThen = null
        )
    }

    val description = invoice.description?.takeIf { it.isNotBlank() }
    if (description != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_request_description_label)) {
            Text(text = description)
        }
    }

    TechnicalRow(label = stringResource(id = R.string.paymentdetails_payerkey_label)) {
        Text(text = payerKey.toHex())
        val nodeParamsManager = business.nodeParamsManager
        val offerPayerKey by produceState<PrivateKey?>(initialValue = null) {
            value = nodeParamsManager.defaultOffer().payerKey
        }
        if (offerPayerKey != null && payerKey == offerPayerKey) {
            Spacer(modifier = Modifier.heightIn(4.dp))
            TextWithIcon(
                text = stringResource(id = R.string.paymentdetails_payerkey_is_mine),
                textStyle = MaterialTheme.typography.subtitle2,
                icon = R.drawable.ic_info,
                iconTint = MaterialTheme.typography.subtitle2.color,
            )
        }
    }
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_offer_label), value = invoice.invoiceRequest.offer.encode())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bolt12_label), value = invoice.write())
}

@Composable
private fun Bolt12MetadataSection(
    metadata: OfferPaymentMetadata
) {
    TechnicalRowAmount(
        label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
        amount = metadata.amount,
        rateThen = null
    )
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = metadata.paymentHash.toHex())
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = metadata.preimage.toHex())
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_offer_metadata_label), value = metadata.encode().toHex())
    if (metadata is OfferPaymentMetadata.V1) {
        TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payerkey_label), value = metadata.payerKey.toHex())
    }
}

// ============== utility components for this view

@Composable
private fun TechnicalCard(
    content: @Composable () -> Unit
) {
    Card(internalPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
    }
}

@Composable
private fun TechnicalRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row {
        Text(
            modifier = Modifier
                .weight(1f)
                .alignBy(FirstBaseline),
            text = label,
            style = MaterialTheme.typography.subtitle2
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(3f)
                .alignBy(FirstBaseline)
        ) {
            content()
        }
    }
}

@Composable
private fun TechnicalRowSelectable(
    label: String,
    value: String,
) {
    TechnicalRow(label = label) {
        SelectionContainer {
            Text(value)
        }
    }
}

@Composable
private fun TechnicalRowAmount(
    label: String,
    amount: MilliSatoshi,
    rateThen: ExchangeRate.BitcoinPriceRate?,
    mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.SHOW,
) {
    val rate = fiatRate
    val prefFiat = LocalFiatCurrency.current
    val prefBtcUnit = LocalBitcoinUnit.current

    TechnicalRow(label = label) {
        AmountView(amount = amount, showUnit = true, forceUnit = prefBtcUnit, mSatDisplayPolicy = mSatDisplayPolicy)

        if (rate != null && amount > 0.msat) {
            val fiatAmount = amount.toFiat(rate.price).toPrettyString(prefFiat, withUnit = true)
            Text(text = stringResource(id = R.string.paymentdetails_amount_rate_now, fiatAmount))
        }

        if (rateThen != null) {
            val fiatAmountThen = amount.toFiat(rateThen.price).toPrettyString(prefFiat, withUnit = true)
            Text(text = stringResource(id = R.string.paymentdetails_amount_rate_then, fiatAmountThen))
        }
    }
}

@Composable
private fun TechnicalRowWithCopy(label: String, value: String) {
    TechnicalRow(label = label) {
        val context = LocalContext.current
        Clickable(onClick = { copyToClipboard(context = context, data = value) }, modifier = Modifier.offset((-6).dp), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text(text = value, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                TextWithIcon(
                    text = stringResource(id = R.string.btn_copy),
                    textStyle = MaterialTheme.typography.subtitle2,
                    icon = R.drawable.ic_copy,
                    iconTint = MaterialTheme.typography.subtitle2.color
                )
            }
        }
    }
}

@Composable
private fun TechnicalRowClickable(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TechnicalRow(label = label) {
        Clickable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().offset(x = (-8).dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = mutedBgColor,
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun TransactionRow(txId: TxId) {
    val context = LocalContext.current
    val link = txUrl(txId = txId)
    TechnicalRowClickable(
        label = stringResource(id = R.string.paymentdetails_tx_id_label),
        onClick = { openLink(context, link) },
        onLongClick = { copyToClipboard(context, txId.toString()) }
    ) {
        TextWithIcon(text = txId.toString(), icon = R.drawable.ic_external_link, maxLines = 1, textOverflow = TextOverflow.Ellipsis, space = 4.dp)
    }
}

@Composable
private fun ChannelIdRow(channelId: ByteVector32, label: String = stringResource(id = R.string.paymentdetails_channel_id_label)) {
    val context = LocalContext.current
    val navController = navController
    TechnicalRowClickable(
        label = label,
        onClick = { navController.navigate("${Screen.ChannelDetails.route}?id=${channelId.toHex()}") },
        onLongClick = { copyToClipboard(context, channelId.toHex()) }
    ) {
        TextWithIcon(text = channelId.toHex(), icon = R.drawable.ic_zap, maxLines = 1, textOverflow = TextOverflow.Ellipsis, space = 4.dp)
    }
}
