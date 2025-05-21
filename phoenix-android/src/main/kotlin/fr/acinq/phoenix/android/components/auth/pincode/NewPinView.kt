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

package fr.acinq.phoenix.android.components.auth.pincode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R

@Composable
fun NewPinFlow(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    vm: NewPinViewModel,
    prompt: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val onDismiss = {
        vm.reset()
        onCancel()
    }
    when (val state = vm.state) {
        is NewPinState.EnterNewPin -> {
            EnterNewPinDialog(
                state = state,
                onDismiss = onDismiss,
                onPinEntered = { vm.moveToConfirmPin(it) },
                prompt = prompt,
            )
        }
        is NewPinState.ConfirmNewPin -> {
            ConfirmNewPinDialog(
                state = state,
                onDismiss = onDismiss,
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
    state: NewPinState.EnterNewPin,
    onDismiss: () -> Unit,
    onPinEntered: (String) -> Unit,
    prompt: @Composable () -> Unit,
) {
    BasePinDialog(
        onDismiss = onDismiss,
        onPinSubmit = onPinEntered,
        prompt = prompt,
        stateLabel = when (state) {
            is NewPinState.EnterNewPin.Init, is NewPinState.EnterNewPin.Frozen.Validating -> null
            is NewPinState.EnterNewPin.Frozen.InvalidPin -> {
                { PinStateError(text = stringResource(id = R.string.pincode_error_malformed)) }
            }
        },
        enabled = state is NewPinState.EnterNewPin.Init
    )
}

@Composable
private fun ConfirmNewPinDialog(
    state: NewPinState.ConfirmNewPin,
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    if (state is NewPinState.ConfirmNewPin.Init) {
        LaunchedEffect(key1 = Unit) {
            pin = ""
        }
    }

    BasePinDialog(
        onDismiss = onDismiss,
        onPinSubmit = {
            pin = it
            onPinConfirmed(it)
        },
        prompt = { PinDialogTitle(text = stringResource(id = R.string.pincode_confirm_title)) },
        stateLabel = when (state) {
            is NewPinState.ConfirmNewPin.Init -> null
            is NewPinState.ConfirmNewPin.Frozen.PinsDoNoMatch -> {
                { PinStateError(text = stringResource(id = R.string.pincode_error_confirm_do_not_match)) }
            }
            is NewPinState.ConfirmNewPin.Frozen.Writing -> {
                { PinStateMessage(text = stringResource(id = R.string.pincode_checking_label)) }
            }
            is NewPinState.ConfirmNewPin.Frozen.CannotWriteToDisk -> {
                { PinStateError(text = stringResource(id = R.string.pincode_error_write)) }
            }
        },
        initialPin = pin,
        enabled = state is NewPinState.ConfirmNewPin.Init
    )
}