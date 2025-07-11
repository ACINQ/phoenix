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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.MainActivity
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.buttons.Checkbox
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.utils.extensions.phoenixName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class ResetWalletStep {
    data object Init : ResetWalletStep()
    data object Confirm : ResetWalletStep()
    sealed class Deleting : ResetWalletStep() {
        data object Init: Deleting()
        data object Databases: Deleting()
        data object Prefs: Deleting()
        data object Seed: Deleting()
    }
    sealed class Result : ResetWalletStep() {
        data object Success : Result()
        sealed class Failure : Result() {
            data class Error(val e: Throwable) : Failure()
        }
    }
}

class ResetWalletViewModel(val application: PhoenixApplication) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<ResetWalletStep>(ResetWalletStep.Init)

    fun deleteWalletData() {
        if (state.value != ResetWalletStep.Confirm) return
        state.value = ResetWalletStep.Deleting.Init

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            state.value = ResetWalletStep.Result.Failure.Error(e)
            log.error("failed to reset wallet data: ", e)
        }) {


            delay(350)
            val (activeNodeId, activeBusiness) = BusinessRepo.activeBusiness.filterNotNull().first()
            val nodeIdHash = PublicKey.fromHex(activeNodeId).hash160().byteVector().toHex()
            val chain = activeBusiness.chain
            BusinessRepo.stopBusiness(activeNodeId)
            delay(250)

            state.value = ResetWalletStep.Deleting.Databases
            val context = application.applicationContext
            context.deleteDatabase("appdb.sqlite")
            context.deleteDatabase("payments-${chain.phoenixName}-$nodeIdHash.sqlite")
            context.deleteDatabase("channels-${chain.phoenixName}-$nodeIdHash.sqlite")
            delay(500)

            state.value = ResetWalletStep.Deleting.Prefs
            application.userPrefs.clear()
            application.internalDataRepository.clear()
            application.globalPrefs.clear()
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                if (task.isSuccessful) BusinessRepo.refreshFcmToken()
            }

            delay(400)

            state.value = ResetWalletStep.Deleting.Seed
            val datadir = SeedManager.getDatadir(context)
            datadir.listFiles()?.forEach { it.delete() }
            datadir.delete()
            delay(300)

            state.value = ResetWalletStep.Result.Success
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return ResetWalletViewModel(application) as T
        }
    }
}

@Composable
fun ResetWallet(
    onBackClick: () -> Unit,
) {
    val vm = viewModel<ResetWalletViewModel>()

    TODO("confirm the node id to delete the correct wallet and not just the active one.")

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
                ReviewReset(onConfirmClick = vm::deleteWalletData)
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
                    it.toPrettyString(LocalBitcoinUnits.current.primary, withUnit = true),
                    it.toPrettyString(LocalFiatCurrencies.current.primary, rate = primaryFiatRate, withUnit = true)
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
            icon = R.drawable.ic_remove,
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
        SuccessMessage(header = stringResource(id = R.string.reset_wallet_success))
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(
            text = stringResource(id = R.string.btn_ok),
            icon = R.drawable.ic_check,
            onClick = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
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
