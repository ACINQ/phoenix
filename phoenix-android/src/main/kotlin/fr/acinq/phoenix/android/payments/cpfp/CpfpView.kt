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

package fr.acinq.phoenix.android.payments.cpfp

import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.channel.Command
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.horizon
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.MempoolFeerate


@Composable
fun CpfpView(
    channelId: ByteVector32,
    onSuccess: () -> Unit,
) {
    val logger = logger("CpfpView")

    val peerManager = business.peerManager
    val vm = viewModel<CpfpViewModel>(factory = CpfpViewModel.Factory(peerManager))
    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()

    var feerate by remember { mutableStateOf(mempoolFeerate?.halfHour?.feerate ?: 10.sat) }

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
                mempoolFeerate = mempoolFeerate
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val state = vm.state) {
            is CpfpState.Init -> {
                BorderButton(
                    text = stringResource(id = R.string.cpfp_prepare_button),
                    icon = R.drawable.ic_build,
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
                FilledButton(
                    text = stringResource(id = R.string.cpfp_execute_button),
                    icon = R.drawable.ic_check,
                    onClick = { vm.executeCpfp(channelId, state.actualFeerate) }
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
                    errorHeader = stringResource(id = R.string.cpfp_failure_title),
                    errorDetails = when (state.failure) {
                        is Command.Splice.Response.Failure.AbortedByPeer -> stringResource(id = R.string.splice_error_aborted_by_peer, state.failure.reason)
                        is Command.Splice.Response.Failure.CannotCreateCommitTx -> stringResource(id = R.string.splice_error_cannot_create_commit)
                        is Command.Splice.Response.Failure.ChannelNotIdle -> stringResource(id = R.string.splice_error_channel_not_idle)
                        is Command.Splice.Response.Failure.Disconnected -> stringResource(id = R.string.splice_error_disconnected)
                        is Command.Splice.Response.Failure.FundingFailure -> stringResource(id = R.string.splice_error_funding_error, state.failure.reason.javaClass.simpleName)
                        is Command.Splice.Response.Failure.InsufficientFunds -> stringResource(id = R.string.splice_error_insufficient_funds)
                        is Command.Splice.Response.Failure.CannotStartSession -> stringResource(id = R.string.splice_error_cannot_start_session)
                        is Command.Splice.Response.Failure.InteractiveTxSessionFailed -> stringResource(id = R.string.splice_error_interactive_session, state.failure.reason.javaClass.simpleName)
                        is Command.Splice.Response.Failure.InvalidSpliceOutPubKeyScript -> stringResource(id = R.string.splice_error_invalid_pubkey)
                        is Command.Splice.Response.Failure.SpliceAlreadyInProgress -> stringResource(id = R.string.splice_error_splice_in_progress)
                    },
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
            is CpfpState.Error.NoChannels -> {
                ErrorMessage(
                    errorHeader = stringResource(id = R.string.cpfp_error_title),
                    errorDetails = stringResource(id = R.string.splice_error_nochannels),
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
            is CpfpState.Error.Thrown -> {
                ErrorMessage(
                    errorHeader = stringResource(id = R.string.cpfp_error_title),
                    errorDetails = state.e.localizedMessage,
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
            is CpfpState.Error.FeerateTooLow -> {
                ErrorMessage(
                    errorHeader = stringResource(id = R.string.cpfp_error_title),
                    errorDetails = stringResource(id = R.string.splice_error_actual_below_user),
                    alignment = Alignment.CenterHorizontally,
                    padding = PaddingValues(0.dp)
                )
            }
        }
    }
}
