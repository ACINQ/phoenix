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

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.errorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun PaymentDetailsSplashView(
    data: WalletPaymentInfo,
    onDetailsClick: (WalletPaymentId) -> Unit,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
    fromEvent: Boolean,
) {
    val context = LocalContext.current
    val payment = data.payment
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // status
        PaymentStatus(payment, fromEvent)
        Spacer(modifier = Modifier.height(58.dp))

        // details
        Card(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            AmountWithAltView(
                amount = payment.amount,
                amountTextStyle = MaterialTheme.typography.h2,
                unitTextStyle = MaterialTheme.typography.h4,
                separatorSpace = 4.dp,
                isOutgoing = payment is OutgoingPayment
            )
            Spacer(modifier = Modifier.height(36.dp))

            val paymentDesc = remember(payment) { payment.smartDescription(context) }
            val customDesc = remember(data) { data.metadata.userDescription?.takeIf { it.isNotBlank() } }
            DetailsRow(
                label = stringResource(id = R.string.paymentdetails_desc_label),
                // if no default desc, show custom desc.
                value = paymentDesc ?: customDesc ?: stringResource(id = R.string.paymentdetails_no_description),
                additionalContent = {
                    if (paymentDesc != null && customDesc != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HSeparator(width = 50.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = customDesc)
                    }
                    Button(
                        text = stringResource(id = when (customDesc) {
                            null -> R.string.paymentdetails_attach_desc_button
                            else -> R.string.paymentdetails_edit_desc_button
                        }),
                        textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
                        modifier = Modifier.offset(x = (-8).dp),
                        icon = R.drawable.ic_text,
                        space = 6.dp,
                        shape = CircleShape,
                        padding = PaddingValues(8.dp),
                        onClick = { showEditDescriptionDialog = true }
                    )
                },
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

            // fees
            when (payment) {
                is OutgoingPayment -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailsRow(
                        label = stringResource(id = R.string.paymentdetails_fees_label),
                        value = payment.fees.toPrettyString(LocalBitcoinUnit.current, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW)
                    )
                }
                is IncomingPayment -> {}
            }

            payment.errorMessage()?.let { errorMessage ->
                Spacer(modifier = Modifier.height(8.dp))
                DetailsRow(
                    label = stringResource(id = R.string.paymentdetails_error_label),
                    value = errorMessage
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        Card {
            Button(
                text = stringResource(id = R.string.paymentdetails_details_button),
                icon = R.drawable.ic_tool,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onDetailsClick(data.id()) },
            )
        }
    }

    if (showEditDescriptionDialog) {
        EditPaymentDetails(
            initialDescription = data.metadata.userDescription,
            onConfirm = {
                onMetadataDescriptionUpdate(data.id(), it?.trim()?.takeIf { it.isNotBlank() })
                showEditDescriptionDialog = false
            },
            onDismiss = { showEditDescriptionDialog = false }
        )
    }
}

@Composable
private fun PaymentStatus(
    payment: WalletPayment,
    fromEvent: Boolean,
) {
    when (payment) {
        is OutgoingPayment -> when (payment.status) {
            is OutgoingPayment.Status.Pending -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                isAnimated = false,
                color = mutedTextColor()
            )
            is OutgoingPayment.Status.Completed.Failed -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_failed),
                imageResId = R.drawable.ic_payment_details_failure_static,
                isAnimated = false,
                color = negativeColor()
            )
            is OutgoingPayment.Status.Completed.Succeeded -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_successful),
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor(),
                details = payment.completedAt().toAbsoluteDateTimeString()
            )
        }
        is IncomingPayment -> when {
            payment.received == null -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                isAnimated = false,
                color = mutedTextColor()
            )
            payment.received!!.receivedWith.filterIsInstance<IncomingPayment.ReceivedWith.NewChannel>().any() -> {
                val minDepth = business.nodeParamsManager.nodeParams.value?.minDepthBlocks
                PaymentStatusIcon(
                    message = stringResource(id = R.string.paymentdetails_status_received_unconfirmed),
                    isAnimated = false,
                    imageResId = R.drawable.ic_clock,
                    color = mutedTextColor(),
                    details = minDepth?.let { stringResource(id = R.string.paymentdetails_status_received_unconfirmed_details, it) }
                )
            }
            else -> PaymentStatusIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_successful),
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor(),
                details = payment.received?.receivedAt?.toAbsoluteDateTimeString()
            )
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun PaymentStatusIcon(
    message: String,
    details: String? = null,
    isAnimated: Boolean,
    imageResId: Int,
    color: Color,
) {
    val scope = rememberCoroutineScope()
    var atEnd by remember { mutableStateOf(false) }
    Image(
        painter = if (isAnimated) {
            rememberAnimatedVectorPainter(AnimatedImageVector.animatedVectorResource(imageResId), atEnd)
        } else {
            painterResource(id = imageResId)
        },
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(90.dp)
    )
    if (isAnimated) {
        LaunchedEffect(key1 = Unit) {
            scope.launch {
                delay(150)
                atEnd = true
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(text = message.uppercase(), style = MaterialTheme.typography.h5)
    details?.let {
        Text(text = details, style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun DetailsRow(
    label: String,
    value: String?,
    additionalContent: (@Composable () -> Unit)? = null,
    helpMessage: String? = null,
    fallbackValue: String = stringResource(id = R.string.utils_unknown),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 400.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.End), modifier = Modifier.weight(.7f))
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row {
                Text(
                    text = value ?: fallbackValue,
                    style = MaterialTheme.typography.body1.copy(fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal),
                    maxLines = maxLines,
                    overflow = overflow,
                )
                if (helpMessage != null) {
                    HelpPopup(message = helpMessage)
                }
            }
            if (additionalContent != null) {
                additionalContent()
            }
        }
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


