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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
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
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingBolt11
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingBolt12
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingLegacyPayToOpen
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingLegacySwapIn
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingNewChannel
import fr.acinq.phoenix.android.payments.details.technical.TechnicalIncomingSpliceIn
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingChannelClose
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingLightning
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingLiquidity
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingSplice
import fr.acinq.phoenix.android.payments.details.technical.TechnicalOutgoingSpliceCpfp
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.relatedPaymentIds


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
        is InboundLiquidityOutgoingPayment -> TechnicalOutgoingLiquidity(payment, originalFiatRate)
        is SpliceCpfpOutgoingPayment -> TechnicalOutgoingSpliceCpfp(payment, originalFiatRate)
        is SpliceOutgoingPayment -> TechnicalOutgoingSplice(payment, originalFiatRate)
    }
}

//@Composable
//private fun HeaderForOutgoing(
//    payment: OutgoingPayment
//) {
//    // -- payment type
//    TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
//        Text(
//            when (payment) {
//                is ChannelCloseOutgoingPayment -> stringResource(id = R.string.paymentdetails_closing)
//                is SpliceOutgoingPayment -> stringResource(id = R.string.paymentdetails_splice_outgoing)
//                is LightningOutgoingPayment -> when (payment.details) {
//                    is LightningOutgoingPayment.Details.Normal -> stringResource(R.string.paymentdetails_normal_outgoing)
//                    is LightningOutgoingPayment.Details.SwapOut -> stringResource(R.string.paymentdetails_swapout)
//                    is LightningOutgoingPayment.Details.Blinded -> stringResource(id = R.string.paymentdetails_offer_outgoing)
//                }
//                is SpliceCpfpOutgoingPayment -> stringResource(id = R.string.paymentdetails_splice_cpfp_outgoing)
//                is InboundLiquidityOutgoingPayment -> stringResource(id = R.string.paymentdetails_inbound_liquidity)
//            }
//        )
//    }
//
//    // -- status
//    TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
//        Text(
//            when (payment) {
//                is InboundLiquidityOutgoingPayment -> when (payment.lockedAt) {
//                    null -> stringResource(R.string.paymentdetails_status_pending)
//                    else -> stringResource(R.string.paymentdetails_status_success)
//                }
//                is OnChainOutgoingPayment -> when (payment.confirmedAt) {
//                    null -> stringResource(R.string.paymentdetails_status_pending)
//                    else -> stringResource(R.string.paymentdetails_status_success)
//                }
//                is LightningOutgoingPayment -> when (payment.status) {
//                    is LightningOutgoingPayment.Status.Pending -> stringResource(R.string.paymentdetails_status_pending)
//                    is LightningOutgoingPayment.Status.Completed.Succeeded -> stringResource(R.string.paymentdetails_status_success)
//                    is LightningOutgoingPayment.Status.Completed.Failed -> stringResource(R.string.paymentdetails_status_failed)
//                }
//            }
//        )
//    }
//}

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
fun IncomingAmountSection(
    amountReceived: MilliSatoshi,
    minerFee: Satoshi?,
    serviceFee: MilliSatoshi?,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalRowAmount(
        label = stringResource(R.string.paymentdetails_amount_received_label),
        amount = amountReceived,
        rateThen = originalFiatRate,
        mSatDisplayPolicy = MSatDisplayPolicy.SHOW
    )
    serviceFee?.let {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_service_fees_label),
            amount = serviceFee,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
    }
    minerFee?.let {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_funding_fees_label),
            amount = minerFee.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.HIDE
        )
    }
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

// ============== utility components for this view

@Composable
fun TechnicalCard(
    content: @Composable () -> Unit
) {
    Card(internalPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
    }
}

@Composable
fun TechnicalRow(
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
fun TechnicalRowWithCopy(label: String, value: String) {
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
fun TechnicalRowClickable(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TechnicalRow(label = label) {
        Clickable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-8).dp),
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
fun TransactionRow(txId: TxId) {
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
fun ChannelIdRow(channelId: ByteVector32, label: String = stringResource(id = R.string.paymentdetails_channel_id_label)) {
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
