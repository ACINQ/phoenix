/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.details.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.components.SplashClickableContent
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.SplashLayout
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.state

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
        // hide the total if this is a liquidity purchase that's not paid from the balance
        if (payment is InboundLiquidityOutgoingPayment && payment.feePaidFromChannelBalance.total == 0.sat) {
            Unit
        } else {
            AmountView(
                amount = when (payment) {
                    is InboundLiquidityOutgoingPayment -> payment.feePaidFromChannelBalance.total.toMilliSatoshi()
                    is OutgoingPayment -> payment.amount - payment.fees
                    is IncomingPayment -> payment.amount
                },
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
        }
        Spacer(modifier = Modifier.height(36.dp))

        when (payment) {
            is IncomingPayment -> SplashIncoming(payment = payment, metadata = data.metadata, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
            is LightningOutgoingPayment -> SplashLightningOutgoing(payment = payment, metadata = data.metadata, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
            is ChannelCloseOutgoingPayment -> SplashChannelClose(payment = payment, metadata = data.metadata, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
            is SpliceCpfpOutgoingPayment -> SplashSpliceOutCpfp(payment = payment, metadata = data.metadata, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
            is SpliceOutgoingPayment -> SplashSpliceOut(payment = payment, metadata = data.metadata, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
            is InboundLiquidityOutgoingPayment -> SplashLiquidityPurchase(payment = payment)
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
fun SplashDescription(
    description: String?,
    userDescription: String?,
    paymentId: WalletPaymentId,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))
    if (!(description.isNullOrBlank() && !userDescription.isNullOrBlank())) {
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_desc_label)) {
            if (description.isNullOrBlank()) {
                Text(
                    text = stringResource(id = R.string.paymentdetails_no_description),
                    style = MaterialTheme.typography.caption.copy(fontStyle = FontStyle.Italic)
                )
            } else {
                Text(text = description)
            }
        }
    }
    SplashLabelRow(label = if (userDescription.isNullOrBlank()) "" else stringResource(id = R.string.paymentdetails_note_label)) {
        SplashClickableContent(onClick = { showEditDescriptionDialog = true }) {
            if (!userDescription.isNullOrBlank()) {
                Text(text = userDescription)
                Spacer(modifier = Modifier.height(8.dp))
            }
            TextWithIcon(
                text = stringResource(
                    id = when (userDescription) {
                        null -> R.string.paymentdetails_attach_desc_button
                        else -> R.string.paymentdetails_edit_desc_button
                    }
                ),
                textStyle = MaterialTheme.typography.subtitle2,
                icon = R.drawable.ic_edit,
                iconTint = MaterialTheme.typography.subtitle2.color,
                space = 6.dp,
            )
        }
    }

    if (showEditDescriptionDialog) {
        CustomNoteDialog(
            initialDescription = userDescription,
            onConfirm = {
                onMetadataDescriptionUpdate(paymentId, it?.trim()?.takeIf { it.isNotBlank() })
                showEditDescriptionDialog = false
            },
            onDismiss = { showEditDescriptionDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomNoteDialog(
    initialDescription: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var description by rememberSaveable { mutableStateOf(initialDescription) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 70.dp),
        ) {
            Text(text = stringResource(id = R.string.paymentdetails_edit_dialog_title), style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(16.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text = description ?: "",
                onTextChange = { description = it.takeIf { it.isNotBlank() } },
                minLines = 2,
                maxLines = 6,
                maxChars = 280,
                staticLabel = stringResource(id = R.string.paymentdetails_edit_dialog_input_label)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel), shape = CircleShape)
                Button(
                    onClick = { onConfirm(description) },
                    text = stringResource(id = R.string.btn_save),
                    icon = R.drawable.ic_check,
                    enabled = description != initialDescription,
                    space = 8.dp,
                    shape = CircleShape
                )
            }
        }
    }
}
