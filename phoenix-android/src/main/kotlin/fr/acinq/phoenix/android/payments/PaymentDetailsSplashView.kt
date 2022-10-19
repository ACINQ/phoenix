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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.errorMessage


@Composable
fun PaymentDetailsSplashView(
    data: WalletPaymentInfo,
    onDetailsClick: (WalletPaymentId) -> Unit
) {
    val payment = data.payment
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    // status
    PaymentStatus(payment)
    Spacer(modifier = Modifier.height(72.dp))

    // details
    Column(
        modifier = Modifier
            .widthIn(500.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colors.surface)
            .padding(top = 36.dp, bottom = 8.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AmountView(
            amount = payment.amount,
            amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 32.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
            unitTextStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
            separatorSpace = 4.dp,
            isOutgoing = payment is OutgoingPayment
        )
        Spacer(modifier = Modifier.height(32.dp))
        PrimarySeparator(height = 6.dp)
        Spacer(modifier = Modifier.height(28.dp))

        DetailsRow(
            label = stringResource(id = R.string.paymentdetails_desc_label),
            value = when (payment) {
                is OutgoingPayment -> when (val details = payment.details) {
                    is OutgoingPayment.Details.Normal -> details.paymentRequest.description ?: details.paymentRequest.descriptionHash?.toHex()
                    is OutgoingPayment.Details.ChannelClosing -> "Closing channel ${details.channelId}"
                    is OutgoingPayment.Details.KeySend -> "Donation"
                    is OutgoingPayment.Details.SwapOut -> "Swap to a Bitcoin address"
                }
                is IncomingPayment -> when (val origin = payment.origin) {
                    is IncomingPayment.Origin.Invoice -> origin.paymentRequest.description ?: origin.paymentRequest.descriptionHash?.toHex()
                    is IncomingPayment.Origin.KeySend -> "Spontaneous payment"
                    is IncomingPayment.Origin.SwapIn, is IncomingPayment.Origin.DualSwapIn -> "On-chain swap deposit"
                }
                else -> null
            }?.takeIf { it.isNotBlank() },
            fallbackValue = stringResource(id = R.string.paymentdetails_no_description)
        )

        if (payment is OutgoingPayment) {
            Spacer(modifier = Modifier.height(8.dp))
            DetailsRow(
                label = stringResource(id = R.string.paymentdetails_destination_label),
                value = when (val details = payment.details) {
                    is OutgoingPayment.Details.Normal -> details.paymentRequest.nodeId.toString()
                    is OutgoingPayment.Details.ChannelClosing -> details.closingAddress
                    is OutgoingPayment.Details.KeySend -> null
                    is OutgoingPayment.Details.SwapOut -> details.address
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        payment.errorMessage()?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            DetailsRow(
                label = stringResource(id = R.string.paymentdetails_error_label),
                value = errorMessage
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.height(44.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                text = stringResource(id = R.string.paymentdetails_details_button),
                textStyle = MaterialTheme.typography.caption,
                space = 4.dp,
                padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                onClick = {
                    onDetailsClick(data.id())
                })
            VSeparator(padding = PaddingValues(vertical = 8.dp))
            Button(
                text = stringResource(id = R.string.paymentdetails_edit_button),
                space = 4.dp,
                textStyle = MaterialTheme.typography.caption,
                padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                onClick = { showEditDescriptionDialog = true })
        }
    }

    if (showEditDescriptionDialog) {
        EditPaymentDetails(
            initialDescription = data.metadata.userDescription,
            onConfirm = {
                // TODO: save user desc
                showEditDescriptionDialog = false
            },
            onDismiss = { showEditDescriptionDialog = false }
        )
    }
}

@Composable
private fun PaymentStatus(
    payment: WalletPayment
) {
    when (payment) {
        is OutgoingPayment -> when (payment.status) {
            is OutgoingPayment.Status.Pending -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                color = mutedTextColor()
            )
            is OutgoingPayment.Status.Completed.Failed -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_failed),
                imageResId = R.drawable.ic_payment_details_failure_static,
                color = negativeColor()
            )
            is OutgoingPayment.Status.Completed.Succeeded -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_successful),
                imageResId = R.drawable.ic_payment_details_success_static,
                color = positiveColor(),
                timestamp = payment.completedAt()
            )
        }
        is IncomingPayment -> when (payment.received) {
            null -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                color = mutedTextColor()
            )
            else -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_successful),
                imageResId = R.drawable.ic_payment_details_success_static,
                color = positiveColor(),
                timestamp = payment.received?.receivedAt
            )
        }
    }
}

@Composable
private fun PaymentStatusIcon(
    message: String,
    timestamp: Long? = null,
    imageResId: Int,
    color: Color,
) {
    Image(
        painter = painterResource(id = imageResId),
        contentDescription = message,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(80.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(text = message.uppercase(), style = MaterialTheme.typography.body2)
    timestamp?.let {
        Text(text = timestamp.toRelativeDateString(), style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun DetailsRow(
    label: String,
    value: String?,
    fallbackValue: String = stringResource(id = R.string.utils_unknown),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Row {
        Text(text = label, style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.End), modifier = Modifier.width(96.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = value ?: fallbackValue,
            style = MaterialTheme.typography.body1.copy(fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal),
            modifier = Modifier.width(300.dp),
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

@Composable
private fun EditPaymentDetails(
    initialDescription: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    Dialog(
        onDismiss = onDismiss,
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    onConfirm(description)
                },
                text = stringResource(id = R.string.btn_save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(text = stringResource(id = R.string.paymentdetails_edit_dialog_title))
            Spacer(modifier = Modifier.height(16.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text = description ?: "",
                onTextChange = { description = it.takeIf { it.isNotBlank() } },
            )
        }
    }
}