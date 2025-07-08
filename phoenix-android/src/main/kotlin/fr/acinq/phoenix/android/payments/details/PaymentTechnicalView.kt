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

@file:Suppress("DEPRECATION")

package fr.acinq.phoenix.android.payments.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.dialogs.IconPopup
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.txUrl
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingBolt11
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingBolt12
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingLegacyPayToOpen
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingLegacySwapIn
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingNewChannel
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingSpliceIn
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingAutoLiquidity
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingChannelClose
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingLightning
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingManualLiquidity
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingSplice
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingSpliceCpfp
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.WalletPaymentInfo


@Composable
fun PaymentDetailsTechnicalView(
    data: WalletPaymentInfo,
) {
    val payment = data.payment

    // use original fiat rate if the payment is older than 1 days
    val originalFiatRate = data.metadata.originalFiat

    @Suppress("DEPRECATION")
    when (payment) {
        is Bolt11IncomingPayment -> TechnicalIncomingBolt11(payment, originalFiatRate)
        is Bolt12IncomingPayment -> TechnicalIncomingBolt12(payment, originalFiatRate)
        is LegacyPayToOpenIncomingPayment -> TechnicalIncomingLegacyPayToOpen(payment, originalFiatRate)
        is LegacySwapInIncomingPayment -> TechnicalIncomingLegacySwapIn(payment, originalFiatRate)
        is NewChannelIncomingPayment -> TechnicalIncomingNewChannel(payment, originalFiatRate)
        is SpliceInIncomingPayment -> TechnicalIncomingSpliceIn(payment, originalFiatRate)

        is LightningOutgoingPayment -> TechnicalOutgoingLightning(payment, originalFiatRate)
        is ChannelCloseOutgoingPayment -> TechnicalOutgoingChannelClose(payment, originalFiatRate)
        is ManualLiquidityPurchasePayment -> TechnicalOutgoingManualLiquidity(payment, originalFiatRate)
        is AutomaticLiquidityPurchasePayment -> TechnicalOutgoingAutoLiquidity(payment, originalFiatRate)
        is SpliceCpfpOutgoingPayment -> TechnicalOutgoingSpliceCpfp(payment, originalFiatRate)
        is SpliceOutgoingPayment -> TechnicalOutgoingSplice(payment, originalFiatRate)
    }

    Spacer(modifier = Modifier.height(60.dp))
}

@Composable
fun TimestampSection(
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
fun Bolt11InvoiceSection(
    invoice: Bolt11Invoice,
    preimage: ByteVector32?,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    invoice.amount?.let {
        TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_invoice_requested_label), amount = it, rateThen = originalFiatRate)
    }
    (invoice.description ?: invoice.descriptionHash?.toHex())?.takeIf { it.isNotBlank() }?.let {
        TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_bolt11_description_label), value = it)
    }
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    preimage?.let { TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_preimage_label), value = preimage.toHex(), helpMessage = stringResource(id = R.string.paymentdetails_preimage_help)) }
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bolt11_label), value = invoice.write())
}

@Composable
fun Bolt12InvoiceSection(
    invoice: Bolt12Invoice,
    payerKey: PrivateKey,
    preimage: ByteVector32?,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    invoice.amount?.let {
        TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_invoice_requested_label), amount = it, rateThen = originalFiatRate)
    }
    invoice.description?.takeIf { it.isNotBlank() }?.let {
        TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_bolt11_description_label), value = it)
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
                textStyle = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                icon = R.drawable.ic_info,
                iconTint = MaterialTheme.typography.caption.color,
                iconSize = 14.dp,
            )
        }
    }
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    preimage?.let { TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_preimage_label), value = preimage.toHex(), helpMessage = stringResource(id = R.string.paymentdetails_preimage_help)) }
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_offer_label), value = invoice.invoiceRequest.offer.encode())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bolt12_label), value = invoice.write())
}

@Composable
fun OutgoingAmountSection(
    payment: OutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?
) {
    TechnicalRowAmount(
        label = stringResource(id = R.string.paymentdetails_amount_sent_label),
        amount = payment.amount,
        rateThen = originalFiatRate,
        mSatDisplayPolicy = MSatDisplayPolicy.SHOW
    )
    TechnicalRowAmount(
        label = stringResource(id = R.string.paymentdetails_fees_label),
        amount = payment.fees,
        rateThen = originalFiatRate,
        mSatDisplayPolicy = MSatDisplayPolicy.SHOW
    )
}

// ============== utility components for this view

@Composable
fun TechnicalRow(
    label: String,
    helpMessage: String? = null,
    space: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    content: @Composable () -> Unit
) {
    Row {
        Spacer(modifier = Modifier.width(12.dp))
        Row(modifier = Modifier
            .weight(1f)
            .alignBy(FirstBaseline)
            .padding(vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle2,
            )
            if (helpMessage != null) {
                IconPopup(
                    icon = R.drawable.ic_help,
                    iconSize = 16.dp,
                    iconPadding = 3.dp,
                    colorAtRest = MaterialTheme.typography.subtitle2.color,
                    spaceRight = 0.dp,
                    spaceLeft = 4.dp,
                    popupMessage = helpMessage
                )
            }
        }
        Spacer(modifier = Modifier.width(space))
        Column(
            modifier = Modifier
                .weight(3f)
                .alignBy(FirstBaseline)
                .padding(contentPadding)
        ) {
            content()
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
fun TechnicalRowSelectable(
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
fun TechnicalRowAmount(
    label: String,
    amount: MilliSatoshi,
    rateThen: ExchangeRate.BitcoinPriceRate?,
    mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.SHOW,
) {
    val rate = primaryFiatRate
    val prefFiat = LocalFiatCurrencies.current.primary
    val prefBtcUnit = LocalBitcoinUnits.current.primary

    TechnicalRow(label = label) {
        AmountView(amount = amount, showUnit = true, forceUnit = prefBtcUnit, mSatDisplayPolicy = mSatDisplayPolicy)

        if (rate != null && amount > 0.msat) {
            val fiatAmount = amount.toFiat(rate.price).toPrettyString(prefFiat, withUnit = true)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = stringResource(id = R.string.paymentdetails_amount_rate_now, fiatAmount), style = MaterialTheme.typography.subtitle2)
        }

        if (rateThen != null) {
            val fiatAmountThen = amount.toFiat(rateThen.price).toPrettyString(prefFiat, withUnit = true)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = stringResource(id = R.string.paymentdetails_amount_rate_then, fiatAmountThen), style = MaterialTheme.typography.subtitle2)
        }
    }
}

@Composable
fun TechnicalRowWithCopy(label: String, value: String, helpMessage: String? = null) {
    TechnicalRow(label = label, space = 0.dp, contentPadding = PaddingValues(0.dp), helpMessage = helpMessage) {
        val context = LocalContext.current
        Clickable(onClick = { copyToClipboard(context = context, data = value) }, shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(text = value, maxLines = 4, overflow = TextOverflow.MiddleEllipsis)
                Spacer(modifier = Modifier.height(4.dp))
                TextWithIcon(
                    text = stringResource(id = R.string.btn_copy),
                    textStyle = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                    icon = R.drawable.ic_copy,
                    iconTint = MaterialTheme.typography.caption.color,
                    iconSize = 14.dp
                )
            }
        }
    }
}

@Composable
fun TechnicalRowClickable(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TechnicalRow(label = label, space = 12.dp, contentPadding = PaddingValues(vertical = 4.dp)) {
        Clickable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = mutedBgColor,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun TransactionRow(txId: TxId) {
    val context = LocalContext.current
    val link = txUrl(txId = txId)
    TechnicalRowClickable(
        label = stringResource(id = R.string.paymentdetails_tx_id_label),
        onClick = { openLink(context, link) },
        onLongClick = { copyToClipboard(context, txId.toString()) }
    ) {
        TextWithIcon(text = txId.toString(), icon = R.drawable.ic_external_link, maxLines = 1, textOverflow = TextOverflow.MiddleEllipsis, space = 4.dp)
    }
}

@Composable
fun ChannelIdRow(channelId: ByteVector32, label: String = stringResource(id = R.string.paymentdetails_channel_id_label)) {
    val context = LocalContext.current
    val navController = navController
    TechnicalRowClickable(
        label = label,
        onClick = { navController.navigate("${Screen.ChannelDetails.route}?id=${channelId.toHex()}") },
        onLongClick = { copyToClipboard(context, channelId.toHex()) }
    ) {
        TextWithIcon(text = channelId.toHex(), icon = R.drawable.ic_zap, maxLines = 1, textOverflow = TextOverflow.MiddleEllipsis, space = 4.dp)
    }
}
