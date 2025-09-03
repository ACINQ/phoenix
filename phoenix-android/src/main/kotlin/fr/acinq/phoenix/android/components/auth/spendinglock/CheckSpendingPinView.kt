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

package fr.acinq.phoenix.android.components.auth.spendinglock

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinFlow

@Composable
fun CheckSpendingPinFlow(
    onCancel: () -> Unit,
    onPinValid: () -> Unit,
    prompt: @Composable () -> Unit,
) {
    val userPrefs = LocalUserPrefs.current!!
    val vm = viewModel<CheckSpendingPinViewModel>(factory = CheckSpendingPinViewModel.Factory(userPrefs = userPrefs))
    CheckPinFlow(onCancel = onCancel, onPinValid = onPinValid, vm = vm, prompt = prompt)
}