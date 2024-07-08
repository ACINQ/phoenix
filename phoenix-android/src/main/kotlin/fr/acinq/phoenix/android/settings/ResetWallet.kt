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
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.MainActivity
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Checkbox
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
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
    sealed class Deleting : ResetWalletStep() {
        object Init: Deleting()
        object Databases: Deleting()
        object Prefs: Deleting()
        object Seed: Deleting()
    }
    sealed class Result : ResetWalletStep() {
        object Success : Result()
        sealed class Failure : Result() {
            data class Error(val e: Throwable) : Failure()
        }
    }
}

class ResetWalletViewModel : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<ResetWalletStep>(ResetWalletStep.Init)

    fun deleteWalletData(
        context: Context,
        chain: Chain,
        nodeIdHash: String,
        onShutdownBusiness: () -> Unit,
        onShutdownService: () -> Unit,
        onPrefsClear: suspend () -> Unit,
        onBusinessReset: () -> Unit,
    ) {
        if (state.value != ResetWalletStep.Confirm) return
        state.value = ResetWalletStep.Deleting.Init
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            state.value = ResetWalletStep.Result.Failure.Error(e)
            log.error("failed to reset wallet data: ", e)
        }) {
            delay(350)
            onShutdownService()
            onShutdownBusiness()
            delay(250)

            state.value = ResetWalletStep.Deleting.Databases
            context.deleteDatabase("appdb.sqlite")
            context.deleteDatabase("payments-${chain.name.lowercase()}-$nodeIdHash.sqlite")
            context.deleteDatabase("channels-${chain.name.lowercase()}-$nodeIdHash.sqlite")
            delay(500)

            state.value = ResetWalletStep.Deleting.Prefs
            onPrefsClear()
            delay(400)

            state.value = ResetWalletStep.Deleting.Seed
            val datadir = SeedManager.getDatadir(context)
            datadir.listFiles()?.forEach { it.delete() }
            datadir.delete()
            delay(300)

            onBusinessReset()
            state.value = ResetWalletStep.Result.Success
        }
    }
}

@Composable
fun ResetWallet(
    onShutdownBusiness: () -> Unit,
    onShutdownService: () -> Unit,
    onPrefsClear: suspend () -> Unit,
    onBusinessReset: () -> Unit,
    onBackClick: () -> Unit,
) {
    val vm = viewModel<ResetWalletViewModel>()

    DefaultScreenLayout {
        when (vm.state.value) {
            is ResetWalletStep.Deleting, is ResetWalletStep.Result.Success -> {
                BackHandler {}
            }
            else -> {
                DefaultScreenHeader(
                    onBackClick = onBackClick,
                    title = when (vm.state.value) {
                        ResetWalletStep.Confirm -> stringResource(id = R.string.reset_wallet_confirm_title)
                        else -> stringResource(id = R.string.reset_wallet_title)
                    }
                )
            }
        }

        when (val state = vm.state.value) {
            ResetWalletStep.Init -> {
                InitReset(onReviewClick = { vm.state.value = ResetWalletStep.Confirm })
            }
            ResetWalletStep.Confirm -> {
                val context = LocalContext.current
                val business = business
                val nodeIdHash = business.nodeParamsManager.nodeParams.value!!.nodeId.hash160().byteVector().toHex()
                ReviewReset(
                    onConfirmClick = {
                        vm.deleteWalletData(
                            context = context,
                            chain = business.chain,
                            nodeIdHash = nodeIdHash,
                            onShutdownBusiness = onShutdownBusiness,
                            onShutdownService = onShutdownService,
                            onPrefsClear = onPrefsClear,
                            onBusinessReset = onBusinessReset,
                        )
                    }
                )
            }
            is ResetWalletStep.Deleting -> {
                DeletingWallet(state)
            }
            ResetWalletStep.Result.Success -> {
                WalletDeleted()
            }
            is ResetWalletStep.Result.Failure -> {
                DeletionFailed(state)
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
            icon = R.drawable.ic_trash,
            iconTint = negativeColor,
            onClick = onConfirmClick,
            enabled = backupChecked && disclaimerChecked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeletingWallet(state: ResetWalletStep.Deleting) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.reset_wallet_shutting_down),
            style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
        )
        Text(
            text = stringResource(id = R.string.reset_wallet_deleting_db),
            style = when (state) {
                ResetWalletStep.Deleting.Init -> MaterialTheme.typography.subtitle2
                else -> MaterialTheme.typography.body1.copy(fontSize = 14.sp)
            }
        )
        Text(
            text = stringResource(id = R.string.reset_wallet_deleting_prefs),
            style = when (state) {
                ResetWalletStep.Deleting.Init, ResetWalletStep.Deleting.Databases -> MaterialTheme.typography.subtitle2
                else -> MaterialTheme.typography.body1.copy(fontSize = 14.sp)
            }
        )
        Text(
            text = stringResource(id = R.string.reset_wallet_deleting_seed),
            style = when (state) {
                ResetWalletStep.Deleting.Init, ResetWalletStep.Deleting.Databases, ResetWalletStep.Deleting.Prefs -> MaterialTheme.typography.subtitle2
                else -> MaterialTheme.typography.body1.copy(fontSize = 14.sp)
            }
        )
    }
}

@Composable
private fun WalletDeleted() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val application = application
        SuccessMessage(header = stringResource(id = R.string.reset_wallet_success))
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(
            text = stringResource(id = R.string.btn_ok),
            icon = R.drawable.ic_check,
            onClick = {
                context.startActivity(
                    Intent(context, application.mainActivityClass).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        )
    }
}

@Composable
private fun DeletionFailed(
    failure: ResetWalletStep.Result.Failure
) {
    ErrorMessage(
        header = stringResource(id = R.string.reset_wallet_failure_title),
        details = when (failure) {
            is ResetWalletStep.Result.Failure.Error -> failure.e.localizedMessage
        },
    )
}
