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

package fr.acinq.phoenix.android.components.auth.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinFlow
import fr.acinq.phoenix.android.components.auth.pincode.PinDialogTitle
import fr.acinq.phoenix.android.globalPrefs

/**
 * @param walletId the wallet we are trying to unlock
 * @param acceptHiddenPin whether entering a hidden lock pin should be accepted or not. Should be false
 *          in most screens, and true in the startup screen.
 * @param onPinValid callback called with the id of the wallet to unlock. Note that this id may be
 *          different from [walletId], if [acceptHiddenPin] is true and the user entered a hidden pin.
 */
@Composable
fun CheckScreenLockPinFlow(
    walletId: WalletId,
    acceptHiddenPin: Boolean,
    onCancel: () -> Unit,
    onPinValid: (WalletId) -> Unit,
    prompt: @Composable () -> Unit = { PinDialogTitle(text = stringResource(id = R.string.pincode_check_screenlock_title)) }
) {
    val walletMetadataMap = globalPrefs.getAvailableWalletsMeta.collectAsState(null)
    val walletMetadata = walletMetadataMap.value?.get(walletId)

    val vm = viewModel<CheckScreenLockPinViewModel>(factory = CheckScreenLockPinViewModel.Factory(application, walletId, acceptHiddenPin = acceptHiddenPin), key = walletId.nodeIdHash)
    CheckPinFlow(onCancel = onCancel, onPinValid = onPinValid, vm = vm, prompt = prompt, walletId = walletId, walletMetadata = walletMetadata)
}
