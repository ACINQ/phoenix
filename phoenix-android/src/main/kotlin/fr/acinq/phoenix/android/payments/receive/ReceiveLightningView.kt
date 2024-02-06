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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.AmountWithFiatBelow
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.data.availableForReceive
import fr.acinq.phoenix.data.canRequestLiquidity
import kotlinx.coroutines.launch
import java.text.DecimalFormat


@Composable
fun LightningInvoiceView(
    vm: ReceiveViewModel,
    onFeeManagementClick: () -> Unit,
    onScanDataClick: () -> Unit,
    defaultDescription: String,
    expiry: Long,
    maxWidth: Dp,
) {
    val paymentsManager = business.paymentsManager
    var customDesc by remember { mutableStateOf(defaultDescription) }
    var customAmount by remember { mutableStateOf<MilliSatoshi?>(null) }

    // refresh LN invoice when it has been paid
    LaunchedEffect(key1 = Unit) {
        paymentsManager.lastCompletedPayment.collect {
            val state = vm.lightningInvoiceState
            if (state is LightningInvoiceState.Show && it is IncomingPayment && state.paymentRequest.paymentHash == it.paymentHash) {
                vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry)
            }
        }
    }

    val onEdit = { vm.isEditingLightningInvoice = true }

    InvoiceHeader(
        icon = R.drawable.ic_zap,
        helpMessage = stringResource(id = R.string.receive_lightning_help),
        content = { Text(text = stringResource(id = R.string.receive_lightning_title)) },
    )

    val navController = navController
    val state = vm.lightningInvoiceState
    val isEditing = vm.isEditingLightningInvoice

    when {
        isEditing -> {
            EditInvoiceView(
                amount = customAmount,
                description = customDesc,
                onAmountChange = { customAmount = it },
                onDescriptionChange = { customDesc = it },
                onCancel = { vm.isEditingLightningInvoice = false },
                onSubmit = { vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry) },
                onFeeManagementClick = onFeeManagementClick,
            )
        }
        state is LightningInvoiceState.Init || state is LightningInvoiceState.Generating -> {
            if (state is LightningInvoiceState.Init) {
                LaunchedEffect(key1 = Unit) {
                    vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry)
                }
            }
            Box(contentAlignment = Alignment.Center) {
                QRCodeView(bitmap = vm.lightningQRBitmap, data = null, maxWidth = maxWidth)
                Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = stringResource(id = R.string.receive_lightning_generating)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = onEdit, maxWidth = maxWidth)
        }
        state is LightningInvoiceState.Show -> {
            DisplayLightningInvoice(
                paymentRequest = state.paymentRequest,
                bitmap = vm.lightningQRBitmap,
                onFeeManagementClick = onFeeManagementClick,
                onEdit = onEdit,
                maxWidth = maxWidth,
            )
        }
        state is LightningInvoiceState.Error -> {
            ErrorMessage(
                header = stringResource(id = R.string.receive_lightning_error),
                details = state.e.localizedMessage
            )
        }
    }

    if ((state is LightningInvoiceState.Init || state is LightningInvoiceState.Show) && !isEditing) {
        Spacer(modifier = Modifier.height(24.dp))
        HSeparator(width = 50.dp)
        Spacer(modifier = Modifier.height(24.dp))
        BorderButton(
            text = stringResource(id = R.string.receive_lnurl_button),
            icon = R.drawable.ic_scan,
            onClick = onScanDataClick,
        )
    }
}

@Composable
private fun DisplayLightningInvoice(
    paymentRequest: PaymentRequest,
    bitmap: ImageBitmap?,
    onEdit: () -> Unit,
    onFeeManagementClick: () -> Unit,
    maxWidth: Dp,
) {
    val context = LocalContext.current
    val prString = remember(paymentRequest) { paymentRequest.write() }
    val amount = paymentRequest.amount
    val description = paymentRequest.description.takeUnless { it.isNullOrBlank() }

    QRCodeView(data = prString, bitmap = bitmap, maxWidth = maxWidth)

    EvaluateLiquidityIssuesForPayment(
        amount = amount,
        onFeeManagementClick = onFeeManagementClick,
        isEditing = false,
    )

    if (amount != null || description != null) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onEdit, role = Role.Button, onClickLabel = stringResource(id = R.string.receive_lightning_edit_title))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!description.isNullOrBlank()) {
                QRCodeDetail(
                    label = stringResource(id = R.string.receive_lightning_desc_label),
                    value = description,
                    maxLines = 2
                )
            }
            if (amount != null) {
                QRCodeDetail(label = stringResource(id = R.string.receive_lightning_amount_label)) {
                    AmountWithFiatBelow(
                        amount = amount,
                        amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                        fiatTextStyle = MaterialTheme.typography.subtitle2,
                    )
                }
            }
        }
    } else {
        Spacer(modifier = Modifier.height(16.dp))
    }
    Spacer(modifier = Modifier.height(16.dp))
    CopyShareEditButtons(
        onCopy = { copyToClipboard(context, data = prString) },
        onShare = { share(context, "lightning:$prString", context.getString(R.string.receive_lightning_share_subject), context.getString(R.string.receive_lightning_share_title)) },
        onEdit = onEdit,
        maxWidth = maxWidth
    )
}

@Composable
private fun EditInvoiceView(
    amount: MilliSatoshi?,
    description: String?,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (MilliSatoshi?) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onFeeManagementClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colors.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TextInput(
            text = description ?: "",
            onTextChange = onDescriptionChange,
            staticLabel = stringResource(id = R.string.receive_lightning_edit_desc_label),
            placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_desc_placeholder), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            maxChars = 140,
            minLines = 2,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(16.dp))

        AmountInput(
            amount = amount,
            staticLabel = stringResource(id = R.string.receive_lightning_edit_amount_label),
            placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_amount_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onAmountChange = { complexAmount -> onAmountChange(complexAmount?.amount) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Button(
                icon = R.drawable.ic_arrow_back,
                onClick = onCancel,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                text = stringResource(id = R.string.receive_lightning_edit_generate_button),
                icon = R.drawable.ic_qrcode,
                onClick = onSubmit,
                horizontalArrangement = Arrangement.End,
            )
        }
    }

    EvaluateLiquidityIssuesForPayment(
        amount = amount,
        onFeeManagementClick = onFeeManagementClick,
        isEditing = true
    )
}

@Composable
private fun EvaluateLiquidityIssuesForPayment(
    amount: MilliSatoshi?,
    onFeeManagementClick: () -> Unit,
    isEditing: Boolean, // do not show the dialog immediately when editing the invoice, it's annoying
) {
    val log = logger("EvaluateLiquidity")
    val context = LocalContext.current

    val channelsMap by business.peerManager.channelsFlow.collectAsState()
    val canRequestLiquidity = remember(channelsMap) { channelsMap.canRequestLiquidity() }
    val availableForReceive = remember(channelsMap) { channelsMap.availableForReceive() }

    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    val swapFee = remember(mempoolFeerate) { mempoolFeerate?.swapEstimationFee(hasNoChannels = channelsMap?.values?.filterNot { it.isTerminated }.isNullOrEmpty()) }

    val liquidityPolicyPrefs = UserPrefs.getLiquidityPolicy(context).collectAsState(null)

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
                        showDialogImmediately = !isEditing,
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
                        showDialogImmediately = !isEditing,
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
                        showDialogImmediately = !isEditing,
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
                        showDialogImmediately = !isEditing,
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
                        showDialogImmediately = !isEditing,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
private fun IncomingLiquidityWarning(
    header: String,
    message: String,
    onFeeManagementClick: () -> Unit,
    showDialogImmediately: Boolean,
    isSevere: Boolean,
    useEnablePolicyWording: Boolean,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(showDialogImmediately) }
    if (showSheet) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                // executed when user click outside the sheet, and after sheet has been hidden thru state.
                showSheet = false
            },
            modifier = Modifier.heightIn(max = 700.dp),
            containerColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
            scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 50.dp)
            ) {
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
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) showSheet = false
                            }
                        },
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
