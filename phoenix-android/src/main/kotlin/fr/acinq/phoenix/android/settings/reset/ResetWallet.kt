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

package fr.acinq.phoenix.android.settings.reset

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.MainActivity
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.AmountWithFiatBeside
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Checkbox
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.buttons.SurfaceFilledButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.wallet.WalletView
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor


@Composable
fun ResetWallet(
    walletId: WalletId,
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
    onExportPaymentsClick: () -> Unit,
    onLightningBalanceClick: () -> Unit,
    onSwapInBalanceClick: () -> Unit,
    onFinalBalanceClick: () -> Unit,
) {
    val vm = viewModel<ResetWalletViewModel>(factory = ResetWalletViewModel.Factory(application = application, walletId = walletId))

    DefaultScreenLayout {
        when (vm.state.value) {
            is ResetWalletStep.Deleting, is ResetWalletStep.Result.Success -> {
                BackHandler {}
            }
            is ResetWalletStep.Confirm -> {
                DefaultScreenHeader(onBackClick = { vm.state.value = ResetWalletStep.Init }, title = stringResource(id = R.string.reset_wallet_confirm_title))
            }
            else -> {
                DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.reset_wallet_title))
            }
        }

        when (val state = vm.state.value) {
            ResetWalletStep.Init -> {
                InitReset(walletId = walletId, onReviewClick = { vm.state.value = ResetWalletStep.Confirm }, onExportPaymentsClick = onExportPaymentsClick)
            }
            ResetWalletStep.Confirm -> {
                ReviewWalletBeforeDeletion(
                    business = business,
                    onConfirmClick = vm::deleteWalletData,
                    onLightningBalanceClick = onLightningBalanceClick, onSwapInBalanceClick = onSwapInBalanceClick, onFinalBalanceClick = onFinalBalanceClick
                )
            }
            is ResetWalletStep.Deleting -> {
                DeletingWallet(state)
            }
            ResetWalletStep.Result.Success -> {
                WalletDeleted(walletId)
            }
            is ResetWalletStep.Result.Failure -> {
                DeletionFailed(state)
            }
        }
    }
}

@Composable
private fun InitReset(
    walletId: WalletId,
    onReviewClick: () -> Unit,
    onExportPaymentsClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = stringResource(R.string.reset_wallet_instructions_header))
        Spacer(modifier = Modifier.height(16.dp))
        WalletView(walletId, avatarBackgroundColor = mutedBgColor, internalPadding = PaddingValues(0.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.reset_wallet_instructions_details))
    }

    Card {
        SurfaceFilledButton(
            text = stringResource(R.string.reset_wallet_payments_history_button),
            icon = R.drawable.ic_arrow_down_circle,
            onClick = onExportPaymentsClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Card {
        FilledButton(
            text = stringResource(id = R.string.reset_wallet_review_button),
            icon = R.drawable.ic_arrow_next,
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReviewClick,
        )
    }
}

@Composable
private fun ReviewWalletBeforeDeletion(
    business: PhoenixBusiness,
    onConfirmClick: () -> Unit,
    onLightningBalanceClick: () -> Unit,
    onSwapInBalanceClick: () -> Unit,
    onFinalBalanceClick: () -> Unit,
) {
    var backupChecked by remember { mutableStateOf(false) }
    var disclaimerChecked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = stringResource(id = R.string.reset_wallet_confirm_header))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.reset_wallet_confirm_details),
            style = MaterialTheme.typography.subtitle2
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.reset_wallet_confirm_details2),
            style = MaterialTheme.typography.subtitle2
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(12.dp),
    ) {
        WarningMessage(
            header = stringResource(id = R.string.reset_wallet_confirm_seed_balance),
            details = null,
            padding = PaddingValues(start = 2.dp),
            space = 14.dp,
            modifier = Modifier.fillMaxWidth(),
            alignment = Alignment.CenterHorizontally
        )
        Spacer(Modifier.height(8.dp))

        val balance by business.balanceManager.balance.collectAsState()
        DeleteWalletBalanceView(text = stringResource(R.string.walletinfo_lightning), icon = R.drawable.ic_zap, amount = balance, onClick = onLightningBalanceClick)

        val swapInBalance by business.balanceManager.swapInWalletBalance.collectAsState()
        DeleteWalletBalanceView(text = stringResource(R.string.walletinfo_onchain_swapin), icon = R.drawable.ic_chain, amount = swapInBalance.total.toMilliSatoshi(), onClick = onSwapInBalanceClick)

        val finalWallet by business.peerManager.finalWallet.collectAsState()
        DeleteWalletBalanceView(text = stringResource(R.string.walletinfo_onchain_final), icon = R.drawable.ic_chain, amount = finalWallet?.all?.balance?.toMilliSatoshi(), onClick = onFinalBalanceClick)

        Spacer(modifier = Modifier.height(24.dp))
        Checkbox(
            text = stringResource(id = R.string.displayseed_backup_checkbox),
            padding = PaddingValues(0.dp),
            checked = backupChecked,
            onCheckedChange = { backupChecked = it },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Checkbox(
            text = stringResource(id = R.string.reset_wallet_confirm_seed_disclaimer),
            padding = PaddingValues(0.dp),
            checked = disclaimerChecked,
            onCheckedChange = { disclaimerChecked = it }
        )
    }

    Card {
        FilledButton(
            text = stringResource(id = R.string.reset_wallet_confirm_button),
            icon = R.drawable.ic_remove,
            backgroundColor = negativeColor,
            shape = RectangleShape,
            onClick = onConfirmClick,
            enabled = backupChecked && disclaimerChecked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeleteWalletBalanceView(text: String, icon: Int, amount: MilliSatoshi?, onClick: () -> Unit) {
    Clickable(
        backgroundColor = mutedBgColor,
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextWithIcon(text = text, icon = icon, iconSize = 16.dp, textStyle = MaterialTheme.typography.subtitle2)
            Spacer(Modifier.height(4.dp))
            amount?.let { AmountWithFiatBeside(amount = it, amountTextStyle = MaterialTheme.typography.body2) } ?: ProgressView(text = stringResource(R.string.utils_loading_data), padding = PaddingValues(0.dp))
        }
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
private fun WalletDeleted(walletId: WalletId) {
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
                BusinessManager.stopBusiness(walletId)
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
            is ResetWalletStep.Result.Failure.SeedFileAccess -> "Could not access seed file, try again"
            is ResetWalletStep.Result.Failure.WriteNewSeed -> "Could not delete seed, try again"
        },
    )
}
