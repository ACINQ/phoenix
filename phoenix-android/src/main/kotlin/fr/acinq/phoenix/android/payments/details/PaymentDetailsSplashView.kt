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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.blockchain.electrum.getConfirmations
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.payments.cpfp.CpfpView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.errorMessage
import fr.acinq.phoenix.utils.extensions.minDepthForFunding
import fr.acinq.phoenix.utils.extensions.state
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun PaymentDetailsSplashView(
    onBackClick: () -> Unit,
    data: WalletPaymentInfo,
    onDetailsClick: (WalletPaymentId) -> Unit,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
    fromEvent: Boolean,
) {
    val payment = data.payment
    SplashLayout(
        header = { DefaultScreenHeader(onBackClick = onBackClick) },
        topContent = { PaymentStatus(data.payment, fromEvent, onCpfpSuccess = onBackClick) }
    ) {
        AmountView(
            amount = if (payment is OutgoingPayment) payment.amount - payment.fees else payment.amount,
            amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 30.sp),
            separatorSpace = 4.dp,
            prefix = stringResource(id = if (payment is OutgoingPayment) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received)
        )

        Spacer(modifier = Modifier.height(36.dp))
        PrimarySeparator(
            height = 6.dp,
            color = when (payment.state()) {
                WalletPaymentState.Failure -> negativeColor
                WalletPaymentState.SuccessOffChain, WalletPaymentState.SuccessOnChain -> positiveColor
                else -> mutedBgColor
            }
        )
        Spacer(modifier = Modifier.height(36.dp))

        PaymentDescriptionView(data = data, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
        PaymentDestinationView(payment = payment)
        PaymentFeeView(payment = payment)

        data.payment.errorMessage()?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
                Text(text = errorMessage)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        BorderButton(
            text = stringResource(id = R.string.paymentdetails_details_button),
            borderColor = borderColor,
            textStyle = MaterialTheme.typography.caption,
            icon = R.drawable.ic_tool,
            iconTint = MaterialTheme.typography.caption.color,
            onClick = { onDetailsClick(data.id()) },
        )
    }
}

@Composable
private fun PaymentStatus(
    payment: WalletPayment,
    fromEvent: Boolean,
    onCpfpSuccess: () -> Unit,
) {
    val peerManager = business.peerManager
    when (payment) {
        is LightningOutgoingPayment -> when (payment.status) {
            is LightningOutgoingPayment.Status.Pending -> PaymentStatusIcon(
                message = { Text(text = stringResource(id = R.string.paymentdetails_status_sent_pending)) },
                imageResId = R.drawable.ic_payment_details_pending_static,
                isAnimated = false,
                color = mutedTextColor
            )
            is LightningOutgoingPayment.Status.Completed.Failed -> PaymentStatusIcon(
                message = { Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_failed), textAlign = TextAlign.Center) },
                imageResId = R.drawable.ic_payment_details_failure_static,
                isAnimated = false,
                color = negativeColor
            )
            is LightningOutgoingPayment.Status.Completed.Succeeded -> PaymentStatusIcon(
                message = {
                    Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt?.toRelativeDateString() ?: ""))
                },
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor,
            )
        }
        is ChannelCloseOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                val nodeParams = business.nodeParamsManager.nodeParams.value
                // TODO get depth for closing
                PaymentStatusIcon(
                    message = {
                        Text(text = stringResource(id = R.string.paymentdetails_status_unconfirmed))
                    },
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_channelclose_confirmed, payment.completedAt?.toRelativeDateString() ?: ""))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, onCpfpSuccess)
            }
        }
        is SpliceOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                PaymentStatusIcon(
                    message = { Text(text = stringResource(id = R.string.paymentdetails_status_unconfirmed)) },
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt!!.toRelativeDateString()))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, onCpfpSuccess)
            }
        }
        is SpliceCpfpOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                PaymentStatusIcon(
                    message = { Text(text = stringResource(id = R.string.paymentdetails_status_unconfirmed)) },
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt!!.toRelativeDateString()))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, onCpfpSuccess)
            }
        }
        is IncomingPayment -> {
            val received = payment.received
            when {
                received == null -> {
                    PaymentStatusIcon(
                        message = { Text(text = stringResource(id = R.string.paymentdetails_status_received_pending)) },
                        imageResId = R.drawable.ic_payment_details_pending_static,
                        isAnimated = false,
                        color = mutedTextColor
                    )
                }
                received.receivedWith.isEmpty() -> {
                    PaymentStatusIcon(
                        message = { Text(text = stringResource(id = R.string.paymentdetails_status_received_paytoopen_pending)) },
                        isAnimated = false,
                        imageResId = R.drawable.ic_clock,
                        color = mutedTextColor,
                    )
                }
                received.receivedWith.any { it is IncomingPayment.ReceivedWith.OnChainIncomingPayment && it.lockedAt == null } -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = stringResource(id = R.string.paymentdetails_status_unconfirmed))
                        },
                        isAnimated = false,
                        imageResId = R.drawable.ic_clock,
                        color = mutedTextColor,
                    )
                }
                payment.completedAt == null -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = stringResource(id = R.string.paymentdetails_status_received_pending))
                        },
                        imageResId = R.drawable.ic_payment_details_pending_static,
                        isAnimated = false,
                        color = mutedTextColor
                    )
                }
                else -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = annotatedStringResource(id = R.string.paymentdetails_status_received_successful, payment.completedAt!!.toRelativeDateString()))
                        },
                        imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                        isAnimated = fromEvent,
                        color = positiveColor,
                    )
                }
            }
            received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>()?.firstOrNull()?.let {
                val nodeParams = business.nodeParamsManager.nodeParams.value
                val channelMinDepth by produceState<Int?>(initialValue = null, key1 = Unit) {
                    nodeParams?.let { params ->
                        val channelId = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>()?.firstOrNull()?.channelId
                        channelId?.let { peerManager.getChannelWithCommitments(it)?.minDepthForFunding(params) }
                    }
                }
                ConfirmationView(it.txId, it.channelId, isConfirmed = it.confirmedAt != null, onCpfpSuccess, channelMinDepth)
            }
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun PaymentStatusIcon(
    message: @Composable ColumnScope.() -> Unit,
    isAnimated: Boolean,
    imageResId: Int,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
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
            modifier = Modifier.size(80.dp)
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
        Column {
            message()
        }
    }

}

@Composable
private fun PaymentDescriptionView(
    data: WalletPaymentInfo,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    val paymentDesc = data.payment.smartDescription(LocalContext.current)
    val customDesc = remember(data) { data.metadata.userDescription?.takeIf { it.isNotBlank() } }
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_desc_label)) {
        val finalDesc = paymentDesc ?: customDesc
        Text(
            text = finalDesc ?: stringResource(id = R.string.paymentdetails_no_description),
            style = if (finalDesc == null) MaterialTheme.typography.caption.copy(fontStyle = FontStyle.Italic) else MaterialTheme.typography.body1
        )
        if (paymentDesc != null && customDesc != null) {
            Spacer(modifier = Modifier.height(8.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = customDesc)
        }
        Button(
            text = stringResource(
                id = when (customDesc) {
                    null -> R.string.paymentdetails_attach_desc_button
                    else -> R.string.paymentdetails_edit_desc_button
                }
            ),
            textStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
            modifier = Modifier.offset(x = (-8).dp),
            icon = R.drawable.ic_text,
            iconTint = MaterialTheme.typography.caption.color,
            space = 6.dp,
            shape = CircleShape,
            padding = PaddingValues(8.dp),
            onClick = { showEditDescriptionDialog = true }
        )
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
private fun PaymentDestinationView(payment: WalletPayment) {
    when (payment) {
        is OnChainOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_chain) {
                Text(
                    text = when (payment) {
                        is SpliceOutgoingPayment -> payment.address
                        is ChannelCloseOutgoingPayment -> payment.address
                        is SpliceCpfpOutgoingPayment -> stringResource(id = R.string.paymentdetails_destination_cpfp_value)
                    }
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun PaymentFeeView(payment: WalletPayment) {
    val btcUnit = LocalBitcoinUnit.current
    when {
        payment is LightningOutgoingPayment && (payment.state() == WalletPaymentState.SuccessOffChain) -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is SpliceOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is ChannelCloseOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is SpliceCpfpOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is IncomingPayment -> {
            val receivedWithNewChannel = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.NewChannel>() ?: emptyList()
            val receivedWithSpliceIn = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.SpliceIn>() ?: emptyList()
            if ((receivedWithNewChannel + receivedWithSpliceIn).isNotEmpty()) {
                val serviceFee = receivedWithNewChannel.map { it.serviceFee }.sum() + receivedWithSpliceIn.map { it.serviceFee }.sum()
                val fundingFee = receivedWithNewChannel.map { it.miningFee }.sum() + receivedWithSpliceIn.map { it.miningFee }.sum()
                Spacer(modifier = Modifier.height(8.dp))
                if (serviceFee > 0.msat) {
                    SplashLabelRow(
                        label = stringResource(id = R.string.paymentdetails_service_fees_label),
                        helpMessage = stringResource(R.string.paymentdetails_service_fees_desc)
                    ) {
                        Text(text = serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SplashLabelRow(
                    label = stringResource(id = R.string.paymentdetails_funding_fees_label),
                    helpMessage = stringResource(R.string.paymentdetails_funding_fees_desc)
                ) {
                    Text(text = fundingFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE))
                }
            }
        }
        else -> {}
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
                onClick = { onConfirm(description) },
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
                maxLines = 6,
                maxChars = 280,
                staticLabel = stringResource(id = R.string.paymentdetails_edit_dialog_input_label)
            )
        }
    }
}

@Composable
private fun ConfirmationView(
    txId: ByteVector32,
    channelId: ByteVector32,
    isConfirmed: Boolean,
    onCpfpSuccess: () -> Unit,
    minDepth: Int? = null, // sometimes we know how many confirmations are needed
) {
    val txUrl = txUrl(txId = txId.toHex())
    val context = LocalContext.current
    val electrumClient = business.electrumClient
    var showBumpTxDialog by remember { mutableStateOf(false) }

    if (isConfirmed) {
        FilledButton(
            text = stringResource(id = R.string.paymentdetails_status_confirmed),
            icon = R.drawable.ic_chain,
            backgroundColor = Color.Transparent,
            padding = PaddingValues(8.dp),
            textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp),
            iconTint = MaterialTheme.colors.primary,
            space = 6.dp,
            onClick = { openLink(context, txUrl) }
        )
    } else {
        val confirmations by produceState<Int?>(initialValue = null) {
            value = electrumClient.getConfirmations(txId)
        }
        confirmations?.let { conf ->
            FilledButton(
                text = when (minDepth) {
                    null -> stringResource(R.string.paymentdetails_status_unconfirmed_default, conf)
                    else -> stringResource(R.string.paymentdetails_status_unconfirmed_with_depth, conf, minDepth)
                },
                icon = when (conf) {
                    0 -> R.drawable.ic_rocket
                    else -> R.drawable.ic_chain
                },
                onClick = {
                    if (conf == 0) {
                        showBumpTxDialog = true
                    }  else {
                        openLink(context, txUrl)
                    }
                },
                backgroundColor = Color.Transparent,
                padding = PaddingValues(8.dp),
                textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp),
                iconTint = MaterialTheme.colors.primary,
                space = 6.dp,
            )
            if (conf == 0 && showBumpTxDialog) {
                BumpTransactionDialog(channelId = channelId, onSuccess = onCpfpSuccess, onDismiss = { showBumpTxDialog = false })
            }
        } ?: ProgressView(
            text = stringResource(id = R.string.paymentdetails_status_unconfirmed_fetching),
            textStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
            padding = PaddingValues(8.dp),
            progressCircleSize = 16.dp,
        )
    }
}

@Composable
private fun BumpTransactionDialog(
    channelId: ByteVector32,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismiss = onDismiss,
        title = "Accelerate my transactions",
        buttons = null,
    ) {
        CpfpView(channelId = channelId, onSuccess = onSuccess)
    }
}
