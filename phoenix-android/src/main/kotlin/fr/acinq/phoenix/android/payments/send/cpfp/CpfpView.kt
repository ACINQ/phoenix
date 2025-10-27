/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.cpfp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.inputs.FeerateSlider
import fr.acinq.phoenix.android.components.layouts.SplashLabelRow
import fr.acinq.phoenix.android.payments.send.spliceout.spliceFailureDetails
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.BitcoinUnit


@Composable
fun CpfpView(
    walletId: WalletId,
    business: PhoenixBusiness,
    channelId: ByteVector32,
    onSuccess: () -> Unit,
) {
    val peerManager = business.peerManager
    val vm = viewModel<CpfpViewModel>(factory = CpfpViewModel.Factory(peerManager))
    val mempoolFeerate by application.phoenixGlobal.feerateManager.mempoolFeerate.collectAsState()

    val recommendedFeerate by peerManager.recommendedFeerateFlow.collectAsState()
    var feerate by remember { mutableStateOf(recommendedFeerate.feerate) }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.cpfp_instructions))
        Spacer(modifier = Modifier.height(24.dp))
        SplashLabelRow(label = stringResource(id = R.string.cpfp_feerate_label)) {
            FeerateSlider(
                feerate = feerate,
                onFeerateChange = {
                    if (feerate != it) vm.state = CpfpState.Init
                    feerate = it
                },
                mempoolFeerate = mempoolFeerate,
                enabled = true
            )
        }
    }

    val mayDoPayments by peerManager.mayDoPayments.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val state = vm.state) {
            is CpfpState.Init -> {
                BorderButton(
                    text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.cpfp_prepare_button),
                    icon = R.drawable.ic_build,
                    enabled = mayDoPayments,
                    onClick = { vm.estimateFee(channelId, feerate) },
                    backgroundColor = Color.Transparent,
                )
            }
            is CpfpState.EstimatingFee -> {
                ProgressView(text = stringResource(id = R.string.cpfp_estimating))
            }
            is CpfpState.ReadyToExecute -> {
                Text(text = annotatedStringResource(id = R.string.cpfp_estimation, state.fee.toPrettyString(BitcoinUnit.Sat, withUnit = true)), textAlign = TextAlign.Center)
                Text("Effective feerate: ${state.actualFeerate}", style = MaterialTheme.typography.body1.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                SmartSpendButton(
                    walletId = walletId,
                    text = stringResource(id = R.string.cpfp_execute_button),
                    icon = R.drawable.ic_check,
                    enabled = mayDoPayments,
                    onSpend = { vm.executeCpfp(channelId, state.actualFeerate) }
                )
            }
            is CpfpState.Executing -> {
                ProgressView(text = stringResource(id = R.string.cpfp_executing))
            }
            is CpfpState.Complete.Success -> {
                TextWithIcon(text = stringResource(id = R.string.cpfp_success), icon = R.drawable.ic_check, iconTint = positiveColor)
                LaunchedEffect(key1 = true) { onSuccess() }
            }
            is CpfpState.Complete.Failed -> {
                ErrorMessage(
                    header = stringResource(id = R.string.cpfp_failure_title),
                    details = spliceFailureDetails(spliceFailure = state.failure),
                    alignment = Alignment.CenterHorizontally,
                )
            }
            is CpfpState.Error.NoChannels -> {
                ErrorMessage(
                    header = stringResource(id = R.string.cpfp_error_title),
                    details = stringResource(id = R.string.splice_error_nochannels),
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
            is CpfpState.Error.Thrown -> {
                ErrorMessage(
                    header = stringResource(id = R.string.cpfp_error_title),
                    details = state.e.localizedMessage,
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
            is CpfpState.Error.FeerateTooLow -> {
                ErrorMessage(
                    header = stringResource(id = R.string.cpfp_error_title),
                    details = stringResource(id = R.string.cpfp_splice_error_actual_below_user),
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
        }
    }
}
