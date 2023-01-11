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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.walletPaymentId


@Composable
fun PaymentLineLoading(
    paymentId: WalletPaymentId,
    onPaymentClick: (WalletPaymentId) -> Unit
) {
    val backgroundColor = MaterialTheme.colors.background.copy(alpha = 0.65f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onPaymentClick(paymentId) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PaymentIconComponent(
            icon = null,
            backgroundColor = backgroundColor,
            description = stringResource(id = R.string.paymentdetails_status_sent_pending)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row {
                Text(
                    text = "",
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = "",
                    modifier = Modifier
                        .width(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "",
                fontSize = 12.sp,
                modifier = Modifier
                    .width(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(backgroundColor)
            )
        }
    }
}

@Composable
fun PaymentLine(
    paymentInfo: WalletPaymentInfo,
    onPaymentClick: (WalletPaymentId) -> Unit,
    isAmountRedacted: Boolean = false,
) {
    val payment = paymentInfo.payment

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onPaymentClick(payment.walletPaymentId()) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PaymentIcon(payment)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row {
                PaymentDescription(paymentInfo = paymentInfo, modifier = Modifier.weight(1.0f))
                Spacer(modifier = Modifier.width(16.dp))
                if (!isPaymentFailed(payment)) {
                    val isOutgoing = payment is OutgoingPayment
                    val amount = when (payment) {
                        is OutgoingPayment -> if (payment.details is OutgoingPayment.Details.ChannelClosing) {
                            payment.recipientAmount
                        } else {
                            payment.parts.filterIsInstance<OutgoingPayment.LightningPart>().map { it.amount }.sum()
                        }
                        is IncomingPayment -> payment.received?.amount ?: 0.msat
                    }
                    if (isAmountRedacted) {
                        Text(text = "****")
                    } else {
                        AmountView(
                            amount = amount,
                            amountTextStyle = MaterialTheme.typography.body1.copy(color = if (isOutgoing) negativeColor() else positiveColor()),
                            unitTextStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                            prefix = stringResource(if (isOutgoing) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val timestamp: Long = remember {
                payment.completedAt().takeIf { it > 0 } ?: when (payment) {
                    is OutgoingPayment -> payment.createdAt
                    is IncomingPayment -> payment.createdAt
                }
            }
            Text(text = timestamp.toRelativeDateString(), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun PaymentDescription(paymentInfo: WalletPaymentInfo, modifier: Modifier = Modifier) {
    val payment = paymentInfo.payment
    val metadata = paymentInfo.metadata
    val desc = metadata.userDescription ?: when (payment) {
        is OutgoingPayment -> when (val d = payment.details) {
            is OutgoingPayment.Details.Normal -> d.paymentRequest.description
            is OutgoingPayment.Details.KeySend -> stringResource(id = R.string.paymentline_keysend_outgoing)
            is OutgoingPayment.Details.SwapOut -> d.address
            is OutgoingPayment.Details.ChannelClosing -> if (d.closingAddress.isBlank()) {
                stringResource(R.string.paymentline_closing_desc_no_address)
            } else {
                stringResource(R.string.paymentline_closing_desc, d.closingAddress)
            }
        }
        is IncomingPayment -> when (val o = payment.origin) {
            is IncomingPayment.Origin.Invoice -> o.paymentRequest.description
            is IncomingPayment.Origin.KeySend -> stringResource(id = R.string.paymentline_keysend_incoming)
            is IncomingPayment.Origin.SwapIn -> o.address ?: stringResource(id = R.string.paymentline_swap_in_desc)
            is IncomingPayment.Origin.DualSwapIn -> stringResource(id = R.string.paymentline_swap_in_desc)
        }
    }.takeIf { !it.isNullOrBlank() }
    Text(
        text = desc ?: stringResource(id = R.string.paymentdetails_no_description),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (desc != null) MaterialTheme.typography.body1 else MaterialTheme.typography.body1.copy(color = mutedTextColor()),
        modifier = modifier
    )
}

private fun isPaymentFailed(payment: WalletPayment) = (payment is OutgoingPayment && payment.status is OutgoingPayment.Status.Completed.Failed)

@Composable
private fun PaymentIcon(payment: WalletPayment) {
    when (payment) {
        is OutgoingPayment -> when (payment.status) {
            is OutgoingPayment.Status.Completed.Failed -> PaymentIconComponent(
                icon = R.drawable.ic_payment_failed,
                description = stringResource(id = R.string.paymentdetails_status_sent_failed)
            )
            is OutgoingPayment.Status.Pending -> PaymentIconComponent(
                icon = R.drawable.ic_payment_pending,
                description = stringResource(id = R.string.paymentdetails_status_sent_pending)
            )
            is OutgoingPayment.Status.Completed.Succeeded.OffChain -> PaymentIconComponent(
                icon = R.drawable.ic_payment_success,
                description = stringResource(id = R.string.paymentdetails_status_sent_successful),
                iconSize = 18.dp,
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
            is OutgoingPayment.Status.Completed.Succeeded.OnChain -> PaymentIconComponent(
                icon = R.drawable.ic_payment_success_onchain,
                description = stringResource(id = R.string.paymentdetails_status_sent_successful),
                iconSize = 14.dp,
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
        }
        is IncomingPayment -> when {
            payment.received == null -> {
                PaymentIconComponent(
                    icon = R.drawable.ic_payment_pending,
                    description = stringResource(id = R.string.paymentdetails_status_received_pending),
                    iconSize = 18.dp,
                    iconColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
            payment.received!!.receivedWith.filterIsInstance<IncomingPayment.ReceivedWith.NewChannel>().any { !it.confirmed } -> {
                PaymentIconComponent(
                    icon = R.drawable.ic_clock,
                    description = stringResource(id = R.string.paymentdetails_status_received_unconfirmed),
                    iconSize = 18.dp,
                    iconColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
            else -> {
                PaymentIconComponent(
                    icon = if (payment.origin is IncomingPayment.Origin.SwapIn || payment.origin is IncomingPayment.Origin.DualSwapIn) {
                        R.drawable.ic_payment_success_onchain
                    } else {
                        R.drawable.ic_payment_success
                    },
                    description = stringResource(id = R.string.paymentdetails_status_received_successful),
                    iconSize = if (payment.origin is IncomingPayment.Origin.SwapIn || payment.origin is IncomingPayment.Origin.DualSwapIn) {
                        14.dp
                    } else {
                        18.dp
                    },
                    iconColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Composable
private fun PaymentIconComponent(
    icon: Int?,
    description: String,
    iconSize: Dp = 18.dp,
    iconColor: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = Color.Unspecified
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .size(24.dp)
            .background(color = backgroundColor)
            .padding(4.dp)
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
}
