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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.userPrefs

@Composable
fun NewPinFlow(
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val vm = viewModel<NewPinFlowViewModel>(factory = NewPinFlowViewModel.Factory)

    when (val state = vm.state) {
        is NewPinFlowState.EnterNewPin -> {
            EnterNewPinDialog(
                state = state,
                onDismiss = onCancel,
                onPinEntered = { vm.moveToConfirmPin(it) }
            )
        }
        is NewPinFlowState.ConfirmNewPin -> {
            ConfirmNewPinDialog(
                state = state,
                onDismiss = {
                    vm.reset()
                    onCancel()
                },
                onPinConfirmed = {
                    vm.checkAndSavePin(
                        context = context,
                        expectedPin =  state.expectedPin,
                        confirmedPin = it,
                        onPinWritten = {
                            vm.reset()
                            onDone()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun EnterNewPinDialog(
    state: NewPinFlowState.EnterNewPin,
    onDismiss: () -> Unit,
    onPinEntered: (String) -> Unit,
) {
    BasePinDialog(
        onDismiss = onDismiss,
        onPinSubmit = onPinEntered,
        stateLabel = {
            when (state) {
                is NewPinFlowState.EnterNewPin.Init, is NewPinFlowState.EnterNewPin.Frozen.Validating -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_new_title))
                }
                is NewPinFlowState.EnterNewPin.Frozen.InvalidPin -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_error_malformed))
                }
            }
        },
        enabled = state is NewPinFlowState.EnterNewPin.Init
    )
}

@Composable
private fun ConfirmNewPinDialog(
    state: NewPinFlowState.ConfirmNewPin,
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    BasePinDialog(
        onDismiss = onDismiss,
        onPinSubmit = {
            pin = it
            onPinConfirmed(it)
        },
        stateLabel = {
            when (state) {
                is NewPinFlowState.ConfirmNewPin.Init -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_confirm_title))
                    LaunchedEffect(key1 = Unit) {
                        pin = ""
                    }
                }
                is NewPinFlowState.ConfirmNewPin.Frozen.PinsDoNoMatch -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_error_confirm_do_not_match))
                }
                is NewPinFlowState.ConfirmNewPin.Frozen.Writing -> {
                    PinDialogTitle(text = stringResource(id = R.string.pincode_checking_label))
                }
                is NewPinFlowState.ConfirmNewPin.Frozen.CannotWriteToDisk -> {
                    PinDialogError(text = stringResource(id = R.string.pincode_error_write))
                }
            }
        },
        initialPin = pin,
        enabled = state is NewPinFlowState.ConfirmNewPin.Init
    )
}