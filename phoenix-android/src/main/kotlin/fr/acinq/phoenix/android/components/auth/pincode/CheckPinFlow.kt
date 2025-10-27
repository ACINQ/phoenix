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
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata


@Composable
fun CheckPinFlow(
    onCancel: () -> Unit,
    onPinValid: () -> Unit,
    vm: CheckPinViewModel,
    walletId: WalletId,
    walletMetadata: UserWalletMetadata?,
    prompt: @Composable () -> Unit,
) {
    val isUIFrozen = vm.state !is CheckPinState.CanType

    BasePinDialog(
        onDismiss = onCancel,
        initialPin = vm.pinInput,
        onPinSubmit = {
            vm.pinInput = it
            vm.checkPinAndSaveOutcome(it, onPinValid)
        },
        prompt = prompt,
        walletId = walletId,
        walletMetadata = walletMetadata,
        stateLabel = when (val state = vm.state) {
            is CheckPinState.Init, is CheckPinState.CanType -> null
            is CheckPinState.Locked -> {
                { PinStateMessage(text = stringResource(id = R.string.pincode_locked_label, state.timeToWait.toString()), icon = R.drawable.ic_clock) }
            }
            is CheckPinState.Checking -> {
                { PinStateMessage(text = stringResource(id = R.string.pincode_checking_label)) }
            }
            is CheckPinState.MalformedInput -> {
                { PinStateError(text = stringResource(id = R.string.pincode_error_malformed)) }
            }
            is CheckPinState.IncorrectPin -> {
                { PinStateError(text = stringResource(id = R.string.pincode_failure_label)) }
            }
            is CheckPinState.Error -> {
                { PinStateError(text = stringResource(id = R.string.pincode_error_generic)) }
            }
        },
        enabled = !isUIFrozen,
    )
}