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

package fr.acinq.phoenix.android.settings

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.negativeColor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class ResetWalletStep {
    object Init : ResetWalletStep()
    object Confirm : ResetWalletStep()
    object Deleting : ResetWalletStep()
    sealed class Result : ResetWalletStep() {
        object Success : Result()
        data class Failure(val e: Throwable) : Result()
    }
}

class ResetWalletViewModel : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<ResetWalletStep>(ResetWalletStep.Init)

    fun deleteWalletData(context: Context) {
        if (state.value != ResetWalletStep.Confirm) return
        state.value = ResetWalletStep.Deleting
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            state.value = ResetWalletStep.Result.Failure(e)
            log.error("failed to reset wallet data: ", e)
        }) {
            delay(500)
            if (!SeedManager.getDatadir(context).delete()) {
                throw IllegalArgumentException()
            } else {
                state.value = ResetWalletStep.Result.Success
            }
        }
    }
}

@Composable
fun ResetWallet(
    onBackClick: () -> Unit,
) {
    val vm = viewModel<ResetWalletViewModel>()

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = when (vm.state.value) {
                ResetWalletStep.Deleting -> {
                    {}
                }
                else -> onBackClick
            },
            title = when (vm.state.value) {
                ResetWalletStep.Confirm -> stringResource(id = R.string.reset_wallet_confirm_title)
                else -> stringResource(id = R.string.reset_wallet_title)
            }
        )

        when (val state = vm.state.value) {
            ResetWalletStep.Init -> {
                InitReset(onReviewClick = { vm.state.value = ResetWalletStep.Confirm })
            }
            ResetWalletStep.Confirm -> {
                ReviewReset(onConfirmClick = { vm.state.value = ResetWalletStep.Deleting })
            }
            ResetWalletStep.Deleting -> {
                DeletingWallet()
            }
            ResetWalletStep.Result.Success -> {
                WalletDeleted()
            }
            is ResetWalletStep.Result.Failure -> {
                DeletionFailed(state.e)
            }
        }
    }
}

@Composable
private fun InitReset(
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = stringResource(id = R.string.reset_wallet_instructions_header))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.reset_wallet_instructions_details),
            style = MaterialTheme.typography.subtitle2
        )
    }

    Card {
        Button(
            text = stringResource(id = R.string.reset_wallet_review_button),
            icon = R.drawable.ic_arrow_next,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReviewClick,
        )
    }
}

@Composable
private fun ReviewReset(
    onConfirmClick: () -> Unit
) {
    var backupChecked by remember { mutableStateOf(false) }
    var disclaimerChecked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = stringResource(id = R.string.reset_wallet_confirm_header))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.reset_wallet_confirm_details),
            style = MaterialTheme.typography.subtitle2
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val balance by business.balanceManager.balance.collectAsState()
        balance?.let {
            WarningMessage(
                header = stringResource(
                    id = R.string.reset_wallet_confirm_seed_balance,
                    it.toPrettyString(LocalBitcoinUnit.current, withUnit = true),
                    it.toPrettyString(LocalFiatCurrency.current, rate = fiatRate, withUnit = true)
                ),
                details = stringResource(id = R.string.reset_wallet_confirm_seed_responsibility),
                padding = PaddingValues(start = 2.dp),
                space = 14.dp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Checkbox(
                text = stringResource(id = R.string.displayseed_backup_checkbox),
                padding = PaddingValues(0.dp),
                checked = backupChecked,
                onCheckedChange = { backupChecked = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Checkbox(
                text = stringResource(id = R.string.displayseed_loss_disclaimer_checkbox),
                padding = PaddingValues(0.dp),
                checked = disclaimerChecked,
                onCheckedChange = { disclaimerChecked = it }
            )
        } ?: ProgressView(text = stringResource(id = R.string.utils_loading_data))
    }

    Card {
        Button(
            text = stringResource(id = R.string.reset_wallet_confirm_button),
            icon = R.drawable.ic_alert_triangle,
            iconTint = negativeColor,
            onClick = onConfirmClick,
            enabled = /* backupChecked && disclaimerChecked */ false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeletingWallet() {
    Card {
        ProgressView(
            text = stringResource(id = R.string.reset_wallet_deleting),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WalletDeleted() {
    SuccessMessage(header = stringResource(id = R.string.reset_wallet_success))
}

@Composable
private fun DeletionFailed(
    e: Throwable
) {
    ErrorMessage(header = stringResource(id = R.string.reset_wallet_failure_title), details = e.localizedMessage)
}
