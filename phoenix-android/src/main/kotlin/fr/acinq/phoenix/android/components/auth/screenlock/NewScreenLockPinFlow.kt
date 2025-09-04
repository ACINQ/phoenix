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

package fr.acinq.phoenix.android.components.auth.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.auth.pincode.NewPinFlow
import fr.acinq.phoenix.android.components.auth.pincode.PinDialogTitle
import fr.acinq.phoenix.android.globalPrefs

@Composable
fun NewScreenLockPinFlow(
    walletId: WalletId,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val walletMetadataMap = globalPrefs.getAvailableWalletsMeta.collectAsState(null)
    val walletMetadata = walletMetadataMap.value?.get(walletId)
    val vm = viewModel<NewScreenLockPinViewModel>(factory = NewScreenLockPinViewModel.Factory(application, walletId), key = walletId.nodeIdHash)

    NewPinFlow(onCancel = onCancel, onDone = onDone, vm = vm, walletId = walletId, walletMetadata = walletMetadata, prompt = {
        PinDialogTitle(text = stringResource(id = R.string.pincode_new_screenlock_title))
    })
}
