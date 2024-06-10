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

package fr.acinq.phoenix.android.payments.liquidity

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.ChannelCommand
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.AmountWithFiatBelow
import fr.acinq.phoenix.android.components.BackButtonWithBalance
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Checkbox
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.IconPopup
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SatoshiSlider
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.SplashLayout
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.payments.spliceout.spliceFailureDetails
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.orange

object LiquidityLimits {
    val liquidityOptions = arrayOf(
        100_000.sat,
        250_000.sat,
        500_000.sat,
        1_000_000.sat,
        2_000_000.sat,
        10_000_000.sat,
    )
}

@Composable
fun RequestLiquidityView(
    onBackClick: () -> Unit,
) {
    val balance by business.balanceManager.balance.collectAsState(null)
    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val currentInbound = channelsState?.values?.mapNotNull { it.availableForReceive }?.sum()

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = { RequestLiquidityTopSection(currentInbound) },
        bottomContent = {
            if (channelsState.isNullOrEmpty()) {
                InfoMessage(
                    header = stringResource(id = R.string.liquidityads_no_channels_header),
                    details = stringResource(id = R.string.liquidityads_no_channels_details)
                )
            } else {
                balance?.let {
                    RequestLiquidityBottomSection(it)
                } ?: ProgressView(text = stringResource(id = R.string.utils_loading_data))
            }
        },
    )
}

@Composable
private fun RequestLiquidityTopSection(inboundLiquidity: MilliSatoshi?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.bucket_noto),
            contentDescription = null,
            modifier = Modifier.size(82.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row {
            Text(
                text = stringResource(id = R.string.liquidityads_header),
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center,
            )
            IconPopup(
                popupMessage = stringResource(id = R.string.liquidityads_instructions),
                popupLink = stringResource(id = R.string.liquidityads_faq_link) to "https://phoenix.acinq.co/faq#what-is-inbound-liquidity",
                colorAtRest = MaterialTheme.colors.primary,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        inboundLiquidity?.let {
            Row {
                Text(
                    text = stringResource(id = R.string.liquidityads_current_liquidity),
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(3.dp))
                AmountView(
                    amount = it,
                    forceUnit = LocalBitcoinUnit.current,
                    amountTextStyle = MaterialTheme.typography.subtitle2,
                    unitTextStyle = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@Composable
private fun RequestLiquidityBottomSection(
    balance: MilliSatoshi
) {

    val peerManager = business.peerManager
    val appConfigManager = business.appConfigurationManager
    val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()

    val vm = viewModel<RequestLiquidityViewModel>(factory = RequestLiquidityViewModel.Factory(peerManager, appConfigManager))
    var amount by remember { mutableStateOf(LiquidityLimits.liquidityOptions.first()) }
    var isAmountError by remember { mutableStateOf(false) }

    if (vm.state.value !is RequestLiquidityState.Complete.Success) {
        SplashLabelRow(label = stringResource(id = R.string.liquidityads_amount_label)) {
            AmountWithFiatBelow(
                amount = amount.toMilliSatoshi(),
                amountTextStyle = MaterialTheme.typography.body2,
                fiatTextStyle = MaterialTheme.typography.subtitle2,
            )
            SatoshiSlider(
                modifier = Modifier
                    .widthIn(max = 165.dp)
                    .offset(x = (-5).dp, y = (-8).dp),
                possibleValues = LiquidityLimits.liquidityOptions,
                onAmountChange = { newAmount ->
                    if (vm.state.value !is RequestLiquidityState.Init && amount != newAmount) {
                        vm.state.value = RequestLiquidityState.Init
                    }
                    amount = newAmount
                },
                onErrorStateChange = { isAmountError = it },
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (val state = vm.state.value) {
        is RequestLiquidityState.Init, is RequestLiquidityState.Complete.Failed -> {
            if (state is RequestLiquidityState.Complete.Failed) {
                ErrorMessage(
                    header = stringResource(id = R.string.liquidityads_error_header),
                    details = spliceFailureDetails(spliceFailure = state.response),
                    alignment = Alignment.CenterHorizontally
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            BorderButton(
                text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.liquidityads_estimate_button),
                icon = R.drawable.ic_inspect,
                enabled = mayDoPayments && !isAmountError,
                onClick = { vm.estimateFeeForInboundLiquidity(amount) },
            )
        }
        is RequestLiquidityState.Estimating -> {
            ProgressView(text = stringResource(id = R.string.liquidityads_estimating_spinner))
        }
        is RequestLiquidityState.Estimation -> {
            SplashLabelRow(label = "") {
                HSeparator(width = 60.dp)
                Spacer(modifier = Modifier.height(12.dp))
            }
            LeaseEstimationView(amountRequested = amount, leaseFees = state.fees, actualFeerate = state.actualFeerate)
            Spacer(modifier = Modifier.height(24.dp))
            if (state.fees.serviceFee + state.fees.miningFee.toMilliSatoshi() > balance) {
                ErrorMessage(header = stringResource(id = R.string.liquidityads_over_balance))
            } else if (isAmountError) {
                ErrorMessage(header = stringResource(id = R.string.validation_invalid_amount))
            } else {
                ReviewLiquidityRequest(
                    onConfirm = { vm.requestInboundLiquidity(amount = state.amount, feerate = state.actualFeerate) }
                )
            }
        }
        is RequestLiquidityState.Requesting -> {
            ProgressView(text = stringResource(id = R.string.liquidityads_requesting_spinner))
        }
        is RequestLiquidityState.Complete.Success -> {
            LeaseSuccessDetails(liquidityDetails = state.response)
        }
        is RequestLiquidityState.Error.NoChannelsAvailable -> {
            ErrorMessage(
                header = stringResource(id = R.string.liquidityads_error_header),
                details = stringResource(id = R.string.liquidityads_error_channels_unavailable)
            )
        }
        is RequestLiquidityState.Error.Thrown -> {
            ErrorMessage(
                header = stringResource(id = R.string.liquidityads_error_header),
                details = state.cause.localizedMessage
            )
        }
    }
}

@Composable
private fun LeaseEstimationView(
    amountRequested: Satoshi,
    leaseFees: ChannelCommand.Commitment.Splice.Fees,
    actualFeerate: FeeratePerKw
) {
    SplashLabelRow(
        label = stringResource(id = R.string.liquidityads_estimate_details_miner_fees),
        helpMessage = stringResource(id = R.string.liquidityads_estimate_details_miner_fees_help,)
    ) {
        AmountWithFiatBelow(amount = leaseFees.miningFee.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2)
    }
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(
        label = stringResource(id = R.string.liquidityads_estimate_details_service_fees),
        helpMessage = stringResource(id = R.string.liquidityads_estimate_details_service_fees_help)
    ) {
        AmountWithFiatBelow(amount = leaseFees.serviceFee, amountTextStyle = MaterialTheme.typography.body2)
    }
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(
        label = stringResource(id = R.string.liquidityads_estimate_details_duration),
        helpMessage = stringResource(id = R.string.liquidityads_estimate_details_duration_help),
        helpLink = stringResource(id = R.string.liquidityads_faq_link) to "https://phoenix.acinq.co/faq#what-happens-after-a-year-of-reserving-liquidity",
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.liquidityads_estimate_details_duration_value),
                style = MaterialTheme.typography.body2,
            )
        }
    }

    val totalFees = leaseFees.miningFee.toMilliSatoshi() + leaseFees.serviceFee
    SplashLabelRow(label = "") {
        Spacer(modifier = Modifier.height(8.dp))
        HSeparator(width = 60.dp)
        Spacer(modifier = Modifier.height(8.dp))
    }
    SplashLabelRow(
        label = stringResource(id = R.string.send_spliceout_complete_recap_total),
    ) {
        AmountWithFiatBelow(amount = totalFees, amountTextStyle = MaterialTheme.typography.body2)
    }

    if (totalFees >= amountRequested.toMilliSatoshi() * 0.25) {
        SplashLabelRow(label = "", icon = R.drawable.ic_alert_triangle, iconTint = orange) {
            Text(
                text = stringResource(id = R.string.liquidityads_estimate_above_25),
                modifier = Modifier.widthIn(max = 250.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewLiquidityRequest(
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    var confirmLiquidity by remember { mutableStateOf(false) }
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
                Text(text = annotatedStringResource(id = R.string.liquidityads_disclaimer_body1))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.liquidityads_disclaimer_body2))
                Spacer(modifier = Modifier.height(8.dp))
                Checkbox(text = stringResource(id = R.string.utils_ack), checked = confirmLiquidity, onCheckedChange = { confirmLiquidity = it })

                Spacer(modifier = Modifier.height(24.dp))
                FilledButton(
                    text = stringResource(id = R.string.btn_confirm),
                    icon = R.drawable.ic_check,
                    onClick = onConfirm,
                    enabled = confirmLiquidity,
                    modifier = Modifier.align(Alignment.End),
                )
                Button(
                    text = stringResource(id = R.string.btn_cancel),
                    onClick = { showSheet = false },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }

    BorderButton(
        text = stringResource(id = R.string.liquidityads_review_button),
        icon = R.drawable.ic_text,
        onClick = { showSheet = true },
        modifier = Modifier.enableOrFade(!showSheet)
    )
}

@Composable
private fun LeaseSuccessDetails(liquidityDetails: ChannelCommand.Commitment.Splice.Response.Created) {
    SuccessMessage(
        header = stringResource(id = R.string.liquidityads_success),
        details = liquidityDetails.liquidityLease?.amount?.let {
            stringResource(id = R.string.liquidityads_success_amount, it.toPrettyString(unit = LocalBitcoinUnit.current, withUnit = true))
        },
        alignment = Alignment.CenterHorizontally,
    )
}