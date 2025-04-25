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

package fr.acinq.phoenix.android.payments.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.AmountWithFiatBelow
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.MutedFilledButton
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.TransparentFilledButton
import fr.acinq.phoenix.android.components.VSeparator
import fr.acinq.phoenix.android.components.buttons.SegmentedControl
import fr.acinq.phoenix.android.components.buttons.SegmentedControlButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.internalData
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.data.availableForReceive
import fr.acinq.phoenix.data.canRequestLiquidity
import java.text.DecimalFormat


@Composable
fun ColumnScope.LightningInvoiceView(
    vm: ReceiveViewModel,
    onFeeManagementClick: () -> Unit,
    defaultDescription: String,
    defaultExpiry: Long,
    columnWidth: Dp,
    isPageActive: Boolean,
) {
    val context = LocalContext.current

    var customDesc by remember { mutableStateOf(defaultDescription) }
    var customAmount by remember { mutableStateOf<MilliSatoshi?>(null) }
    var isReusable by remember { mutableStateOf(false) }
    var feeWarningDialogShownTimestamp by remember { mutableLongStateOf(0L) }

    val bip353AddressState = internalData.getBip353Address.collectAsState(initial = null)

    var showEditInvoiceDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showBip353InfoDialog by remember { mutableStateOf(false) }

    val state = vm.lightningInvoiceState
    val invoiceData = remember(state) { if (state is LightningInvoiceState.Done) state.paymentData else null }

    // refresh LN invoice when it has been paid
    val paymentsManager = business.paymentsManager
    LaunchedEffect(key1 = Unit) {
        paymentsManager.lastCompletedPayment.collect { completedPayment ->
            if (state is LightningInvoiceState.Done.Bolt11 && completedPayment is Bolt11IncomingPayment && state.invoice.paymentHash == completedPayment.paymentHash) {
                vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = defaultExpiry, isReusable)
                feeWarningDialogShownTimestamp = 0 // reset the dialog tracker to show it asap for a new invoice
            }
        }
    }

    LaunchedEffect(key1 = isReusable) {
        vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = defaultExpiry, isReusable = isReusable)
    }

    InvoiceHeader(
        text = stringResource(if (isReusable) R.string.receive_label_bolt12 else R.string.receive_label_bolt11),
        icon = R.drawable.ic_zap,
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        QRCodeView(bitmap = vm.lightningQRBitmap, data = invoiceData, width = columnWidth, loadingLabel = stringResource(id = R.string.receive_lightning_generating))

        Spacer(modifier = Modifier.height(16.dp))
        SegmentedControl(modifier = Modifier.width(columnWidth)) {
            SegmentedControlButton(onClick = { isReusable = false }, text = stringResource(R.string.receive_lightning_switch_bolt11), selected = !isReusable)
            SegmentedControlButton(onClick = { isReusable = true }, text = stringResource(R.string.receive_lightning_switch_bolt12), selected = isReusable)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Clickable(
            modifier = Modifier.width(columnWidth),
            shape = RoundedCornerShape(16.dp),
            onClick = { showEditInvoiceDialog = true },
            enabled = state is LightningInvoiceState.Done
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhoenixIcon(R.drawable.ic_edit, tint = MaterialTheme.colors.primary)
                    VSeparator(color = mutedTextColor.copy(alpha = .4f))
                    Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state is LightningInvoiceState.Done) {
                            if (state.description.isNullOrBlank() && state.amount == null) {
                                Text(text = "Add amount and description", style = MaterialTheme.typography.subtitle2)
                            }
                            state.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                QRCodeLabel(label = stringResource(R.string.receive_lightning_desc_label)) {
                                    Text(
                                        text = desc,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                                    )
                                }
                            }
                            state.amount?.let { amount ->
                                QRCodeLabel(label = stringResource(R.string.receive_lightning_amount_label)) {
                                    AmountWithFiatBelow(
                                        amount = amount,
                                        amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                                        fiatTextStyle = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (state) {
            is LightningInvoiceState.Init, is LightningInvoiceState.Generating -> {}
            is LightningInvoiceState.Done -> {
                EvaluateLiquidityIssuesForPayment(
                    amount = state.amount,
                    onFeeManagementClick = onFeeManagementClick,
                    showDialogImmediately = isPageActive && currentTimestampMillis() - feeWarningDialogShownTimestamp > 30_000,
                    onDialogShown = { feeWarningDialogShownTimestamp = currentTimestampMillis() },
                )
                Spacer(modifier = Modifier.height(32.dp))
                TorWarning()
                Spacer(modifier = Modifier.height(32.dp))
            }
            is LightningInvoiceState.Error -> {
                ErrorMessage(
                    header = stringResource(id = R.string.receive_lightning_error),
                    details = state.e.localizedMessage
                )
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        bip353AddressState.value?.let {
            Clickable(onClick = { showBip353InfoDialog = true }, shape = RoundedCornerShape(16.dp)) {
                Text(text = stringResource(id = R.string.utils_bip353_with_prefix, it), style = MaterialTheme.typography.body2, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        CopyShareButtons(
            onCopy = { showCopyDialog = true },
            onShare = { showShareDialog = true }
        )
        Spacer(Modifier.height(32.dp))
    }

    if (showEditInvoiceDialog) {
        EditInvoiceView(
            isReusable = isReusable,
            amount = customAmount,
            description = customDesc,
            onAmountChange = { customAmount = it },
            onDescriptionChange = { customDesc = it },
            onDismiss = { showEditInvoiceDialog = false },
            onSubmit = {
                vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = defaultExpiry, isReusable = isReusable)
                showEditInvoiceDialog = false
            },
        )
    }

    if (showCopyDialog) {
        CopyLightningDialog(
            bip353Address = bip353AddressState.value,
            offer = if (state is LightningInvoiceState.Done.Bolt12) state.offer else null,
            invoice = if (state is LightningInvoiceState.Done.Bolt11) state.invoice else null,
            onDismiss = { showCopyDialog = false }
        )
    }

    if (showShareDialog) {
        ShareLightningDialog(
            offer = if (state is LightningInvoiceState.Done.Bolt12) state.offer else null,
            invoice = if (state is LightningInvoiceState.Done.Bolt11) state.invoice else null,
            onDismiss = { showShareDialog = false }
        )
    }

    if (showBip353InfoDialog) {
        ModalBottomSheet(onDismiss = { showBip353InfoDialog = false }) {
            Text(text = stringResource(R.string.receive_bip353_title), style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.receive_bip353_info))
            Spacer(Modifier.height(16.dp))
            MutedFilledButton(
                text = stringResource(R.string.receive_bip353_link),
                icon = R.drawable.ic_external_link,
                iconTint = MaterialTheme.colors.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = { openLink(context, "https://bolt12.org") }
            )
        }
    }
}

@Composable
private fun CopyLightningDialog(
    bip353Address: String?,
    invoice: Bolt11Invoice?,
    offer: OfferTypes.Offer?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        internalPadding = PaddingValues(bottom = 20.dp)
    ) {
        Text(text = stringResource(R.string.btn_copy), style = MaterialTheme.typography.h4, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        invoice?.let {
            CopyButtonDialog(label = stringResource(id = R.string.receive_label_bolt11), value = it.write(), icon = R.drawable.ic_zap)
        }
        offer?.encode()?.let {
            CopyButtonDialog(label = stringResource(id = R.string.receive_label_bolt12), value = it, icon = R.drawable.ic_zap)
            CopyButtonDialog(label = stringResource(id = R.string.receive_label_bip21), value = "bitcoin:?lno=$it", icon = R.drawable.ic_zap)
        }
        if (!bip353Address.isNullOrBlank()) {
            CopyButtonDialog(label = stringResource(id = R.string.receive_label_bip353), value = stringResource(id = R.string.utils_bip353_with_prefix, bip353Address), icon = R.drawable.ic_arobase)
        }
    }
}

@Composable
private fun ShareLightningDialog(
    invoice: Bolt11Invoice?,
    offer: OfferTypes.Offer?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        internalPadding = PaddingValues(bottom = 20.dp)
    ) {
        Text(text = stringResource(R.string.btn_share), style = MaterialTheme.typography.h4, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        offer?.encode()?.let {
            ShareButtonDialog(label = stringResource(id = R.string.receive_label_bolt12), value = it, icon = R.drawable.ic_zap)
            ShareButtonDialog(label = stringResource(id = R.string.receive_label_bip21), value = "bitcoin:?lno=$it", icon = R.drawable.ic_zap)
        }
        invoice?.let {
            ShareButtonDialog(label = stringResource(id = R.string.receive_label_bolt11), value = it.write(), icon = R.drawable.ic_zap)
        }
    }
}

@Composable
private fun EditInvoiceView(
    isReusable: Boolean,
    amount: MilliSatoshi?,
    description: String?,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (MilliSatoshi?) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        isContentScrollable = true,
        internalPadding = PaddingValues(0.dp),
        containerColor = MaterialTheme.colors.background,
    ) {
        Text(
            text = stringResource(if (isReusable) R.string.receive_lightning_edit_title_bolt12 else R.string.receive_lightning_edit_title_bolt11),
            style = MaterialTheme.typography.h4,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colors.surface)
                .padding(16.dp)
        ) {
            TextInput(
                text = description ?: "",
                onTextChange = onDescriptionChange,
                staticLabel = stringResource(id = R.string.receive_lightning_edit_desc_label),
                placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_desc_placeholder), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                maxChars = 140,
                minLines = 2,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            AmountInput(
                amount = amount,
                staticLabel = stringResource(id = R.string.receive_lightning_edit_amount_label),
                placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_amount_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onAmountChange = { complexAmount -> onAmountChange(complexAmount?.amount) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledButton(
                text = stringResource(id = R.string.receive_lightning_edit_generate_button),
                icon = R.drawable.ic_qrcode,
                shape = RoundedCornerShape(16.dp),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TransparentFilledButton(
            text = stringResource(id = R.string.btn_cancel),
            textStyle = MaterialTheme.typography.caption,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EvaluateLiquidityIssuesForPayment(
    amount: MilliSatoshi?,
    onFeeManagementClick: () -> Unit,
    showDialogImmediately: Boolean,
    onDialogShown: () -> Unit,
) {
    val channelsMap by business.peerManager.channelsFlow.collectAsState()
    val canRequestLiquidity = remember(channelsMap) { channelsMap.canRequestLiquidity() }
    val availableForReceive = remember(channelsMap) { channelsMap.availableForReceive() }

    val areChannelsUnusable = remember(channelsMap) { channelsMap?.values?.none { it.isUsable } ?: false }
    if (areChannelsUnusable) return

    // TODO: add a delay before evaluating liquidity to make sure we have all data necessary before displaying a warning, in order to avoid the dialog being displayed too eagerly with some flickering

    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    val swapFee = remember(mempoolFeerate, amount) { mempoolFeerate?.payToOpenEstimationFee(amount = amount ?: 0.msat, hasNoChannels = channelsMap?.values?.filterNot { it.isTerminated }.isNullOrEmpty()) }

    val liquidityPolicyPrefs = userPrefs.getLiquidityPolicy.collectAsState(null)

    when (val liquidityPolicy = liquidityPolicyPrefs.value) {
        null -> {}
        // when fee policy is disabled, we are more aggressive with the warning (dialog is shown immediately)
        is LiquidityPolicy.Disable -> {
            when {
                // no channels or no liquidity => the payment WILL fail
                availableForReceive == 0.msat || (availableForReceive != null && amount != null && amount >= availableForReceive) -> {
                    IncomingLiquidityWarning(
                        header = stringResource(id = R.string.receive_lightning_warning_title_surefail),
                        message = stringResource(id = R.string.receive_lightning_warning_fee_policy_disabled_insufficient_liquidity),
                        onFeeManagementClick = onFeeManagementClick,
                        showDialogImmediately = showDialogImmediately,
                        onDialogShown = onDialogShown,
                        isSevere = true,
                        useEnablePolicyWording = true,
                    )
                }
                else -> {
                    // amountless invoice and/or unknown liquidity => pertinent warning impossible
                    // or amount is below liquidity => no warning needed
                }
            }
        }
        is LiquidityPolicy.Auto -> {
            val btcUnit = LocalBitcoinUnit.current
            when {

                // ====================================
                // ==== check for the absolute fee ====
                // ====================================

                // fee > limit and there's no liquidity
                // => warning that limit should be raised, but not shown immediately
                swapFee != null && !liquidityPolicy.skipAbsoluteFeeCheck && swapFee > liquidityPolicy.maxAbsoluteFee
                        && (availableForReceive == 0.msat || (amount != null && availableForReceive != null && amount > availableForReceive)) -> {
                    IncomingLiquidityWarning(
                        header = stringResource(id = R.string.receive_lightning_warning_title_surefail),
                        message = if (canRequestLiquidity) {
                            stringResource(id = R.string.receive_lightning_warning_message_above_limit_or_liquidity, swapFee.toPrettyString(btcUnit, withUnit = true), liquidityPolicy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true))
                        } else {
                            stringResource(id = R.string.receive_lightning_warning_message_above_limit, swapFee.toPrettyString(btcUnit, withUnit = true), liquidityPolicy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true))
                        },
                        onFeeManagementClick = onFeeManagementClick,
                        showDialogImmediately = showDialogImmediately,
                        onDialogShown = onDialogShown,
                        isSevere = true,
                        useEnablePolicyWording = false,
                    )
                }

                // ====================================
                // ==== check for the relative fee ====
                // ====================================

                // fee > limit in percent and the amount is above available liquidity
                // => strong warning that limit should be raised
                swapFee != null && amount != null && availableForReceive != null
                        && amount > 0.msat && amount > availableForReceive && swapFee.toMilliSatoshi() > amount * liquidityPolicy.maxRelativeFeeBasisPoints / 10_000 -> {
                    val prettyPercent = DecimalFormat("0.##").format(liquidityPolicy.maxRelativeFeeBasisPoints.toDouble() / 100)
                    IncomingLiquidityWarning(
                        header = stringResource(id = R.string.receive_lightning_warning_title_surefail),
                        message = if (canRequestLiquidity) {
                            stringResource(id = R.string.receive_lightning_warning_message_above_limit_percent_or_liquidity, swapFee.toPrettyString(btcUnit, withUnit = true), prettyPercent)
                        } else {
                            stringResource(id = R.string.receive_lightning_warning_message_above_limit_percent, swapFee.toPrettyString(btcUnit, withUnit = true), prettyPercent)
                        },
                        onFeeManagementClick = onFeeManagementClick,
                        showDialogImmediately = showDialogImmediately,
                        onDialogShown = onDialogShown,
                        isSevere = true,
                        useEnablePolicyWording = false,
                    )
                }

                // ========================================================
                // ==== fair warning when fee is acceptable or unknown ====
                // ====  also applies is max fee policy is overridden  ====
                // ========================================================

                // fee is unknown or within limits, and there's no liquidity
                // => hint that a fee is expected
                availableForReceive == 0.msat -> {
                    IncomingLiquidityWarning(
                        header = stringResource(id = R.string.receive_lightning_warning_title_fee_expected),
                        message = if (swapFee == null) {
                            stringResource(id = R.string.receive_lightning_warning_message_fee_expected_unknown)
                        } else {
                            stringResource(id = R.string.receive_lightning_warning_message_fee_expected, swapFee.toPrettyString(btcUnit, withUnit = true))
                        },
                        onFeeManagementClick = onFeeManagementClick,
                        showDialogImmediately = showDialogImmediately,
                        onDialogShown = onDialogShown,
                        isSevere = false,
                        useEnablePolicyWording = false,
                    )
                }
                // fee is unknown or within limits, and the amount is above available liquidity
                // => hint that a fee is expected
                amount != null && availableForReceive != null && amount > availableForReceive -> {
                    IncomingLiquidityWarning(
                        header = stringResource(id = R.string.receive_lightning_warning_title_fee_expected),
                        message = if (swapFee == null) {
                            stringResource(id = R.string.receive_lightning_warning_message_fee_expected_unknown)
                        } else {
                            stringResource(id = R.string.receive_lightning_warning_message_fee_expected, swapFee.toPrettyString(btcUnit, withUnit = true))
                        },
                        onFeeManagementClick = onFeeManagementClick,
                        showDialogImmediately = showDialogImmediately,
                        onDialogShown = onDialogShown,
                        isSevere = false,
                        useEnablePolicyWording = false,
                    )
                }
                // not enough data about liquidity/network, or the invoice is amountless
                else -> {}
            }
        }
    }
}

@Composable
private fun IncomingLiquidityWarning(
    header: String,
    message: String,
    onFeeManagementClick: () -> Unit,
    showDialogImmediately: Boolean,
    onDialogShown: () -> Unit,
    isSevere: Boolean,
    useEnablePolicyWording: Boolean,
) {
    var showSheet by remember { mutableStateOf(showDialogImmediately) }
    if (showSheet) {
        ModalBottomSheet(
            onDismiss = { showSheet = false },
            internalPadding = PaddingValues(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 50.dp)
        ) {
            LaunchedEffect(key1 = Unit) { onDialogShown() }
            Text(
                text = header,
                style = MaterialTheme.typography.h4
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message)
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                BorderButton(
                    text = stringResource(
                        id = if (useEnablePolicyWording) {
                            R.string.receive_lightning_sheet_button_enable
                        } else {
                            R.string.receive_lightning_sheet_button_configure
                        }
                    ),
                    icon = if (useEnablePolicyWording) R.drawable.ic_tool else R.drawable.ic_plus,
                    onClick = onFeeManagementClick,
                )
                Spacer(modifier = Modifier.height(16.dp))
                BorderButton(
                    text = stringResource(id = R.string.receive_lightning_sheet_dismiss),
                    icon = R.drawable.ic_cross,
                    borderColor = Color.Transparent,
                    onClick = { showSheet = false }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Clickable(onClick = { showSheet = true }, shape = RoundedCornerShape(12.dp)) {
        if (isSevere) {
            WarningMessage(
                header = header,
                details = stringResource(id = R.string.receive_lightning_warning_hint_show_details),
                headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                alignment = Alignment.CenterHorizontally,
                padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            InfoMessage(
                header = header,
                details = stringResource(id = R.string.receive_lightning_warning_hint_show_details),
                headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                alignment = Alignment.CenterHorizontally,
                padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TorWarning() {
    val isTorEnabled by userPrefs.getIsTorEnabled.collectAsState(initial = null)

    if (isTorEnabled == true) {

        var showTorWarningDialog by remember { mutableStateOf(false) }

        Clickable(onClick = { showTorWarningDialog = true }, shape = RoundedCornerShape(12.dp)) {
            WarningMessage(
                header = stringResource(id = R.string.receive_tor_warning_title),
                details = null,
                alignment = Alignment.CenterHorizontally,
            )
        }

        if (showTorWarningDialog) {
            ModalBottomSheet(
                onDismiss = { showTorWarningDialog = false },
            ) {
                Text(text = stringResource(id = R.string.receive_tor_warning_title), style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.receive_tor_warning_dialog_content_1))
            }
        }
    }
}
