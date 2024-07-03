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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.isLegacyMigration
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.android.utils.smartDescription
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import fr.acinq.phoenix.utils.extensions.state


@Composable
fun PaymentLineLoading(
    paymentId: WalletPaymentId,
    onPaymentClick: (WalletPaymentId) -> Unit
) {
    val backgroundColor = mutedBgColor.copy(alpha = 0.9f)
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
                if (payment.state() != WalletPaymentState.Failure) {
                    val isOutgoing = payment is OutgoingPayment
                    if (isAmountRedacted) {
                        Text(text = "****")
                    } else {
                        AmountView(
                            amount = payment.amount,
                            amountTextStyle = MaterialTheme.typography.body1.copy(color = if (isOutgoing) negativeColor else positiveColor),
                            unitTextStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                            prefix = stringResource(if (isOutgoing) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            if ((payment is SpliceOutgoingPayment && payment.confirmedAt == null)
                || (payment is SpliceCpfpOutgoingPayment && payment.confirmedAt == null)) {
                Text(text = stringResource(id = R.string.paymentline_outgoing_unconfirmed), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
            } else {
                Row {
                    Text(text = payment.createdAt.toRelativeDateString(), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
                    PaymentContactInfo(paymentInfo = paymentInfo)
                }
            }
        }
    }
}

@Composable
private fun PaymentDescription(paymentInfo: WalletPaymentInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val payment = paymentInfo.payment
    val metadata = paymentInfo.metadata
    val peer by business.peerManager.peerState.collectAsState()

    val desc = when (paymentInfo.isLegacyMigration(peer)) {
        null -> stringResource(id = R.string.paymentdetails_desc_closing_channel) // not sure yet, but we still know it's a closing
        true -> stringResource(id = R.string.paymentdetails_desc_legacy_migration)
        false -> metadata.userDescription
            ?: metadata.lnurl?.description
            ?: payment.incomingOfferMetadata()?.payerNote
            ?: payment.outgoingInvoiceRequest()?.payerNote
            ?: payment.smartDescription(context)
    }

    Text(
        text = desc ?: stringResource(id = R.string.paymentdetails_no_description),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (desc != null) MaterialTheme.typography.body1 else MaterialTheme.typography.body1.copy(color = mutedTextColor),
        modifier = modifier
    )
}

@Composable
private fun PaymentIcon(payment: WalletPayment) {
    when (payment.state()) {
        WalletPaymentState.PendingOnChain -> {
            PaymentIconComponent(
                icon = R.drawable.ic_payment_pending_onchain,
                iconSize = 20.dp,
                description = stringResource(id = R.string.paymentline_desc_pending_onchain),
            )
        }
        WalletPaymentState.PendingOffChain -> {
            PaymentIconComponent(
                icon = R.drawable.ic_payment_pending,
                description = stringResource(id = R.string.paymentline_desc_pending_offchain),
                backgroundColor = Color.Transparent
            )
        }
        WalletPaymentState.SuccessOnChain -> {
            if (payment is IncomingPayment && payment.origin is IncomingPayment.Origin.Invoice) {
                PaymentIconComponent(
                    icon = R.drawable.ic_payment_success,
                    description = stringResource(id = R.string.paymentline_desc_success_offchain),
                    iconColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = MaterialTheme.colors.primary
                )
            } else {
                PaymentIconComponent(
                    icon = R.drawable.ic_payment_success_onchain,
                    description = stringResource(id = R.string.paymentline_desc_success_onchain),
                    iconColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
        }
        WalletPaymentState.SuccessOffChain -> {
            PaymentIconComponent(
                icon = R.drawable.ic_payment_success,
                description = stringResource(id = R.string.paymentline_desc_success_offchain),
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
        }
        WalletPaymentState.Failure -> {
            PaymentIconComponent(
                icon = R.drawable.ic_payment_failed,
                description = stringResource(id = R.string.paymentline_desc_failed)
            )
        }
    }
}

@Composable
private fun PaymentIconComponent(
    icon: Int?,
    description: String,
    iconSize: Dp = 18.dp,
    iconColor: Color = MaterialTheme.colors.primary,
    backgroundColor: Color? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .size(24.dp)
            .then(
                if (backgroundColor != null) {
                    Modifier
                        .background(backgroundColor)
                        .padding(4.dp)
                } else Modifier
            )
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

@Composable
private fun PaymentContactInfo(
    paymentInfo: WalletPaymentInfo
) {
    val offerMetadata = paymentInfo.payment.incomingOfferMetadata()
    if (offerMetadata != null) {
        val contactsManager = business.contactsManager
        val contactForKey = produceState<ContactInfo?>(initialValue = null, producer = {
            value = contactsManager.getContactForPayerPubkey(offerMetadata.payerKey)
        })

        when (val contact = contactForKey.value) {
            null -> FromToNameView(isOutgoing = false, userName = stringResource(id = R.string.paymentdetails_desc_unknown))
            else -> FromToNameView(isOutgoing = false, userName = contact.name)
        }
        return
    }

    val invoiceRequest = paymentInfo.payment.outgoingInvoiceRequest()
    if (invoiceRequest != null) {
        val offer = invoiceRequest.offer
        val contactsManager = business.contactsManager
        val contactForOffer = produceState<ContactInfo?>(initialValue = null, producer = {
            value = contactsManager.getContactForOffer(offer)
        })

        when (val contact = contactForOffer.value) {
            null -> Unit
            else -> FromToNameView(isOutgoing = true, userName = contact.name)
        }
        return
    }

    val lnid = paymentInfo.metadata.lnurl?.pay?.metadata?.lnid?.takeIf { it.isNotBlank() }
    if (!lnid.isNullOrBlank()) {
        FromToNameView(isOutgoing = true, userName = lnid)
    }
}

@Composable
private fun FromToNameView(isOutgoing: Boolean, userName: String) {
    Spacer(modifier = Modifier.width(4.dp))
    Text(text = "•", style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = if (isOutgoing) stringResource(id = R.string.paymentdetails_desc_to, userName) else stringResource(id = R.string.paymentdetails_desc_from, userName),
        style = MaterialTheme.typography.subtitle2.copy(fontSize = 12.sp)
    )
}
