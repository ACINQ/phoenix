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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.TransactionLinkButton
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.WalletPaymentInfo


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
                    is LightningOutgoingPayment.Details.KeySend -> stringResource(R.string.paymentdetails_keysend)
                }
                is SpliceCpfpOutgoingPayment -> stringResource(id = R.string.paymentdetails_splice_cpfp_outgoing)
            }

        )
    }

    // -- status
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
        Text(
            when (payment) {
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
            when (payment.origin) {
                is IncomingPayment.Origin.Invoice -> stringResource(R.string.paymentdetails_normal_incoming)
                is IncomingPayment.Origin.KeySend -> stringResource(R.string.paymentdetails_keysend)
                is IncomingPayment.Origin.SwapIn -> stringResource(R.string.paymentdetails_swapin)
                is IncomingPayment.Origin.OnChain -> stringResource(R.string.paymentdetails_swapin)
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

    // -- recipient's public key
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_pubkey_label), value = payment.recipient.toHex())

    // -- details of the payment
    when (details) {
        is LightningOutgoingPayment.Details.Normal -> {
            InvoiceSection(paymentRequest = details.paymentRequest)
        }
        is LightningOutgoingPayment.Details.SwapOut -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_swapout_address_label), value = details.address)
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
        }
        is LightningOutgoingPayment.Details.KeySend -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
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
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_closing_channel_label),
        value = payment.channelId.toHex()
    )
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_closing_address_label),
        value = payment.address
    )
    TechnicalRow(
        label = stringResource(id = R.string.paymentdetails_closing_tx_label),
        content = { TransactionLinkButton(txId = payment.txId.toHex()) }
    )
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
    TechnicalRow(
        label = stringResource(id = R.string.paymentdetails_splice_cpfp_transaction_label),
        content = { TransactionLinkButton(txId = payment.txId.toHex()) }
    )
}

@Composable
private fun DetailsForSpliceOut(
    payment: SpliceOutgoingPayment
) {
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_splice_out_channel_label),
        value = payment.channelId.toHex()
    )
    TechnicalRowSelectable(
        label = stringResource(id = R.string.paymentdetails_splice_out_address_label),
        value = payment.address
    )
    TechnicalRow(
        label = stringResource(id = R.string.paymentdetails_splice_out_tx_label),
        content = { TransactionLinkButton(txId = payment.txId.toHex()) }
    )

}

@Composable
private fun DetailsForIncoming(
    payment: IncomingPayment
) {
    // -- details about the origin of the payment
    when (val origin = payment.origin) {
        is IncomingPayment.Origin.Invoice -> {
            InvoiceSection(paymentRequest = origin.paymentRequest)
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = payment.preimage.toHex())
        }
        is IncomingPayment.Origin.SwapIn -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_swapin_address_label)) {
                Text(origin.address ?: stringResource(id = R.string.utils_unknown))
            }
        }
        is IncomingPayment.Origin.KeySend -> {}
        is IncomingPayment.Origin.OnChain -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_dualswapin_tx_label)) {
                origin.localInputs.mapIndexed { index, outpoint ->
                    Row {
                        Text(text = stringResource(id = R.string.paymentdetails_dualswapin_tx_value, index + 1))
                        Spacer(modifier = Modifier.width(4.dp))
                        TransactionLinkButton(txId = outpoint.txid.toHex())
                    }
                }
            }
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
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_channel_id_label)) {
            Text(text = receivedWith.channelId.toHex())
        }
    }
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amount, rateThen = rateThen)
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
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_channel_id_label)) {
            Text(text = channelId.toHex())
        }
    }
    TechnicalRow(
        label = stringResource(id = R.string.paymentdetails_tx_id_label),
        content = { TransactionLinkButton(txId = receivedWith.txId.toHex()) }
    )
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amount, rateThen = rateThen)
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
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_channel_id_label)) {
            Text(text = channelId.toHex())
        }
    }
    TechnicalRow(
        label = stringResource(id = R.string.paymentdetails_tx_id_label),
        content = { TransactionLinkButton(txId = receivedWith.txId.toHex()) }
    )
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amount, rateThen = rateThen)
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
private fun InvoiceSection(
    paymentRequest: PaymentRequest
) {
    val requestedAmount = paymentRequest.amount
    if (requestedAmount != null) {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
            amount = requestedAmount,
            rateThen = null
        )
    }

    val description = (paymentRequest.description ?: paymentRequest.descriptionHash?.toHex())?.takeIf { it.isNotBlank() }
    if (description != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_request_description_label)) {
            Text(text = description)
        }
    }

    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = paymentRequest.paymentHash.toHex())
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_request_label), value = paymentRequest.write())
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