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

package fr.acinq.phoenix.android.initwallet.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.initwallet.WritingSeedState


@Composable
fun CreateWalletView(
    onSeedWritten: (WalletId) -> Unit
) {
    val vm = viewModel<CreateWalletViewModel>(factory = CreateWalletViewModel.Factory(application = application))

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LaunchedEffect(Unit) {
            vm.createNewWallet(onSeedWritten = onSeedWritten)
        }
        when (val writingState = vm.writingState) {
            is WritingSeedState.Init, is WritingSeedState.Writing, is WritingSeedState.WrittenToDisk -> {
                Text(stringResource(id = R.string.autocreate_generating))
            }
            is WritingSeedState.Error -> {
                ErrorMessage(
                    header = stringResource(id = R.string.autocreate_error),
                    details = when (writingState) {
                        is WritingSeedState.Error.Generic -> writingState.cause.localizedMessage ?: writingState.cause::class.java.simpleName
                        is WritingSeedState.Error.SeedAlreadyExists -> stringResource(R.string.autocreate_error_already_exists)
                        is WritingSeedState.Error.CannotLoadSeedMap -> stringResource(R.string.autocreate_error_cannot_load_existing)
                    },
                    alignment = Alignment.CenterHorizontally,
                )
            }
        }
    }
}
