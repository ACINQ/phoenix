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

package fr.acinq.phoenix.android.components.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R

@Composable
fun CheckPinFlow(
    onCancel: () -> Unit,
    onPinValid: () -> Unit,
) {
    val context = LocalContext.current
    val vm = viewModel<CheckPinFlowViewModel>(factory = CheckPinFlowViewModel.Factory)

    val isUIFrozen = vm.state !is CheckPinFlowState.CanType

    BasePinDialog(
        onDismiss = {
            onCancel()
        },
        initialPin = vm.pinInput,
        onPinSubmit = {
            vm.pinInput = it
            vm.checkPinAndSaveOutcome(context, it, onPinValid)
        },
        stateLabel = {
            when(val state = vm.state) {
                is CheckPinFlowState.Init, is CheckPinFlowState.CanType -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_check_title))
                }
                is CheckPinFlowState.Locked -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_locked_label, state.timeToWait.toString()), icon = R.drawable.ic_clock)
                }
                is CheckPinFlowState.Checking -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_checking_label))
                }
                is CheckPinFlowState.MalformedInput -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_error_malformed))
                }
                is CheckPinFlowState.IncorrectPin -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_failure_label))
                }
                is CheckPinFlowState.Error -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_error_generic))
                }
            }
        },
        enabled = !isUIFrozen,
    )
}