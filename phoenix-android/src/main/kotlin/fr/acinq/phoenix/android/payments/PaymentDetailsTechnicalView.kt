/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.WebLink
import fr.acinq.phoenix.android.components.txLink
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateString
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.createdAt


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
                Text(text = stringResource(id = R.string.paymentdetails_parts_label), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.subtitle1)
                receivedWith.forEach {
                    TechnicalCard {
                        when (it) {
                            is IncomingPayment.ReceivedWith.NewChannel -> ReceivedWithNewChannel(it, rateThen)
                            is IncomingPayment.ReceivedWith.LightningPayment -> ReceivedWithLightning(it, rateThen)
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
                DetailsForOutgoingPayment(payment)
            }

            // show successful LN parts and closing txs
            val lightningParts = payment.parts.filterIsInstance<OutgoingPayment.LightningPart>().filter { it.status is OutgoingPayment.LightningPart.Status.Succeeded }
            val closingTxsParts = payment.parts.filterIsInstance<OutgoingPayment.ClosingTxPart>()
            if (lightningParts.isNotEmpty() || closingTxsParts.isNotEmpty()) {
                Text(text = stringResource(id = R.string.paymentdetails_parts_label), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.subtitle1)
            }
            lightningParts.forEachIndexed { index, part ->
                TechnicalCard {
                    LightningPart(index, part, rateThen)
                }
            }
            closingTxsParts.forEachIndexed { index, part ->
                TechnicalCard {
                    ClosingTxPart(index, part, rateThen)
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
            when (payment.details) {
                is OutgoingPayment.Details.Normal -> stringResource(R.string.paymentdetails_normal_outgoing)
                is OutgoingPayment.Details.SwapOut -> stringResource(R.string.paymentdetails_swapout)
                is OutgoingPayment.Details.ChannelClosing -> stringResource(R.string.paymentdetails_closing)
                is OutgoingPayment.Details.KeySend -> stringResource(R.string.paymentdetails_keysend)
            }
        )
    }

    // -- status
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
        Text(
            when (payment.status) {
                is OutgoingPayment.Status.Pending -> stringResource(R.string.paymentdetails_status_pending)
                is OutgoingPayment.Status.Completed.Succeeded -> stringResource(R.string.paymentdetails_status_success)
                is OutgoingPayment.Status.Completed.Failed -> stringResource(R.string.paymentdetails_status_failed)
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
                is IncomingPayment.Origin.DualSwapIn -> stringResource(R.string.paymentdetails_swapin)
            }
        )
    }

    // -- status
    val receivedWith = payment.received?.receivedWith?.takeIf { it.isNotEmpty() }
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
        if (receivedWith != null) {
            Text(text = stringResource(R.string.paymentdetails_status_success))
        } else {
            Text(text = stringResource(id = R.string.paymentdetails_status_pending))
        }
    }
}

@Composable
private fun TimestampSection(
    payment: WalletPayment
) {
    if (payment.completedAt() > 0) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_completed_at_label)) {
            Text(text = payment.completedAt().toAbsoluteDateString())
        }
    }

    TechnicalRow(label = stringResource(id = R.string.paymentdetails_created_at_label)) {
        Text(text = payment.createdAt.toAbsoluteDateString())
    }

    if (payment.completedAt() > 0) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_elapsed_label)) {
            Text(text = stringResource(id = R.string.paymentdetails_elapsed, (payment.completedAt() - payment.createdAt).toString()))
        }
    }
}

@Composable
private fun AmountSection(
    payment: WalletPayment,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    val hideMsat = payment is OutgoingPayment && payment.details is OutgoingPayment.Details.ChannelClosing
    TechnicalRowAmount(
        label = stringResource(id = if (payment is OutgoingPayment) R.string.paymentdetails_amount_sent_label else R.string.paymentdetails_amount_received_label),
        amount = payment.amount,
        rateThen = rateThen,
        mSatDisplayPolicy = if (hideMsat) MSatDisplayPolicy.HIDE else MSatDisplayPolicy.SHOW
    )
    TechnicalRowAmount(
        label = stringResource(id = R.string.paymentdetails_fees_label),
        amount = payment.fees,
        rateThen = rateThen,
        mSatDisplayPolicy = if (hideMsat) MSatDisplayPolicy.HIDE else MSatDisplayPolicy.SHOW
    )
}

@Composable
private fun DetailsForOutgoingPayment(
    payment: OutgoingPayment
) {
    val details = payment.details
    val status = payment.status

    // -- recipient's public key
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_pubkey_label), value = payment.recipient.toHex())

    // -- details of the payment
    when (details) {
        is OutgoingPayment.Details.Normal -> {
            InvoiceSection(paymentRequest = details.paymentRequest)
        }
        is OutgoingPayment.Details.SwapOut -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_swapout_address_label), value = details.address)
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
        }
        is OutgoingPayment.Details.KeySend -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
        }
        is OutgoingPayment.Details.ChannelClosing -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_closing_channel_label), value = details.channelId.toHex())
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_closing_address_label), value = details.closingAddress)
        }
    }

    // -- status details
    when (status) {
        is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = status.preimage.toHex())
        }
        is OutgoingPayment.Status.Completed.Failed -> {
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
                Text(text = status.reason.toString())
            }
        }
        else -> {}
    }
}

@Composable
private fun DetailsForIncoming(
    payment: IncomingPayment
) {
    // -- details about the origin of the payment
    when (val origin = payment.origin) {
        is IncomingPayment.Origin.Invoice -> {
            InvoiceSection(paymentRequest = origin.paymentRequest)
        }
        is IncomingPayment.Origin.SwapIn -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = payment.preimage.toHex())
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_swapin_address_label)) {
                Text(origin.address ?: stringResource(id = R.string.utils_unknown))
            }
        }
        is IncomingPayment.Origin.KeySend -> {}
        is IncomingPayment.Origin.DualSwapIn -> {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = payment.preimage.toHex())
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_dualswapin_tx_label)) {
                origin.localInputs.mapIndexed { index, outpoint ->
                    Row {
                        Text(text = stringResource(id = R.string.paymentdetails_dualswapin_tx_value, index + 1))
                        Spacer(modifier = Modifier.width(4.dp))
                        WebLink(text = outpoint.txid.toHex(), url = txLink(txId = outpoint.txid.toHex()), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val channelId = receivedWith.channelId
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_channel_id_label)) {
        Text(text = channelId.toHex())
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
    if (channelId != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_channel_id_label)) {
            Text(text = channelId.toHex())
        }
    }
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = receivedWith.amount, rateThen = rateThen)
}

@Composable
private fun LightningPart(
    index: Int,
    part: OutgoingPayment.LightningPart,
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
private fun ClosingTxPart(
    index: Int,
    part: OutgoingPayment.ClosingTxPart,
    rateThen: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_label)) {
        Text("#$index")
    }
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_part_transaction_label), value = part.txId.toHex())
    TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_part_claimed_label), amount = part.claimed.toMilliSatoshi(), rateThen = rateThen, mSatDisplayPolicy = MSatDisplayPolicy.HIDE)
    TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_closing_type_label)) {
        Text(
            when (part.closingType) {
                ChannelClosingType.Mutual -> stringResource(R.string.paymentdetails_part_closing_type_mutual)
                ChannelClosingType.Local -> stringResource(R.string.paymentdetails_part_closing_type_local)
                ChannelClosingType.Remote -> stringResource(R.string.paymentdetails_part_closing_type_remote)
                ChannelClosingType.Revoked -> stringResource(R.string.paymentdetails_part_closing_type_revoked)
                ChannelClosingType.Other -> stringResource(R.string.paymentdetails_part_closing_type_other)
            }
        )
    }
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