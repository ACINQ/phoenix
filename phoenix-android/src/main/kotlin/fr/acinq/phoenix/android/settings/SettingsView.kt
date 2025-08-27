/*
 * Copyright 2020 ACINQ SAS
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.Notice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.buttons.MenuButton
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.dialogs.FullScreenDialog
import fr.acinq.phoenix.android.components.inputs.CurrencyConverter
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.wallet.ActiveWalletView
import fr.acinq.phoenix.android.components.wallet.AvailableWalletsList
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.canRequestLiquidity


@Composable
fun SettingsView(
    appViewModel: AppViewModel,
    notices: List<Notice>,
    notifications: List<Pair<Set<UUID>, Notification>>,
) {
    val nc = navController

    var showCurrencyConverter by remember { mutableStateOf(false) }

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.navigate(Screen.Home.route) }) {
            Text(
                text = stringResource(id = R.string.menu_settings),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        WalletSwitcher(appViewModel)

        // -- general
        CardHeader(text = stringResource(id = R.string.settings_general_title))
        Card {
            MenuButton(text = stringResource(R.string.settings_about), icon = R.drawable.ic_help_circle, onClick = { nc.navigate(Screen.About.route) })
            MenuButton(text = stringResource(R.string.settings_display_prefs), icon = R.drawable.ic_brush, onClick = { nc.navigate(Screen.DisplayPrefs.route) })
            MenuButton(text = stringResource(R.string.settings_payment_settings), icon = R.drawable.ic_tool, onClick = { nc.navigate(Screen.PaymentSettings.route) })
            MenuButton(text = stringResource(R.string.settings_payment_history), icon = R.drawable.ic_list, onClick = { nc.navigate(Screen.PaymentsHistory.route) })
            MenuButton(text = stringResource(R.string.settings_contacts), icon = R.drawable.ic_user, onClick = { nc.navigate(Screen.Contacts.route) })
            val notifsCount = (notifications + notices).size
            MenuButton(
                text = stringResource(R.string.settings_notifications) + (notifsCount.takeIf { it > 0 }?.let { " ($it)" } ?: ""),
                icon = R.drawable.ic_notification,
                onClick = { nc.navigate(Screen.Notifications.route) },
                textStyle = if (notifsCount > 0) MaterialTheme.typography.body2 else MaterialTheme.typography.body1
            )
            MenuButton(text = stringResource(R.string.settings_converter), icon = R.drawable.ic_world, onClick = { showCurrencyConverter = true })
        }

        // -- general
        CardHeader(text = stringResource(id = R.string.settings_fees_title))
        Card {
            MenuButton(text = stringResource(R.string.settings_liquidity_policy), icon = R.drawable.ic_wand, onClick = { nc.navigate(Screen.LiquidityPolicy.route) })
            val channelsState = LocalBusiness.current?.peerManager?.channelsFlow?.collectAsState()
            if (channelsState?.value?.canRequestLiquidity() == true) {
                MenuButton(text = stringResource(R.string.settings_add_liquidity), icon = R.drawable.ic_bucket, onClick = { nc.navigate(Screen.LiquidityRequest.route) })
            }
        }

        // -- privacy & security
        CardHeader(text = stringResource(id = R.string.settings_security_title))
        Card {
            MenuButton(text = stringResource(R.string.settings_access_control), icon = R.drawable.ic_unlock, onClick = { nc.navigate(Screen.AppAccess.route) })
            MenuButton(text = stringResource(R.string.settings_display_seed), icon = R.drawable.ic_key, onClick = { nc.navigate(Screen.DisplaySeed.route) })
            MenuButton(text = stringResource(R.string.settings_electrum), icon = R.drawable.ic_chain, onClick = { nc.navigate(Screen.ElectrumServer.route) })
            MenuButton(text = stringResource(R.string.settings_tor), icon = R.drawable.ic_tor_shield, onClick = { nc.navigate(Screen.TorConfig.route) })
        }

        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_advanced_title))
        Card {
            MenuButton(text = stringResource(R.string.settings_wallet_info), icon = R.drawable.ic_box, onClick = { nc.navigate(Screen.WalletInfo.route) })
            MenuButton(text = stringResource(R.string.settings_list_channels), icon = R.drawable.ic_zap, onClick = { nc.navigate(Screen.Channels.route) })
            MenuButton(text = stringResource(R.string.experimental_title), icon = R.drawable.ic_experimental, onClick = { nc.navigate(Screen.Experimental.route) })
            MenuButton(text = stringResource(R.string.settings_logs), icon = R.drawable.ic_text, onClick = { nc.navigate(Screen.Logs.route) })
        }
        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_danger_title))
        Card {
            MenuButton(text = stringResource(R.string.settings_mutual_close), icon = R.drawable.ic_cross_circle, onClick = { nc.navigate(Screen.MutualClose.route) })
            MenuButton(text = stringResource(id = R.string.settings_reset_wallet), icon = R.drawable.ic_remove, onClick = { nc.navigate("${Screen.ResetWallet.route}?nodeId=$") })
            MenuButton(
                text = stringResource(R.string.settings_force_close),
                textStyle = MaterialTheme.typography.button.copy(color = negativeColor),
                icon = R.drawable.ic_alert_triangle,
                iconTint = negativeColor,
                onClick = { nc.navigate(Screen.ForceClose.route) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showCurrencyConverter) {
        CurrencyConverter(initialAmount = null, initialUnit = LocalBitcoinUnits.current.primary, onDone = { _, _ -> showCurrencyConverter = false })
    }
}

@Composable
private fun WalletSwitcher(appViewModel: AppViewModel) {

    val activeWalletInUI by appViewModel.activeWalletInUI.collectAsState()
    val availableWallets by appViewModel.availableWallets.collectAsState()
    val activeNodeId = activeWalletInUI?.nodeId ?: return
    var showAvailableWalletsDialog by remember { mutableStateOf(false) }

    ActiveWalletView(
        nodeId = activeNodeId,
        onClick = { showAvailableWalletsDialog = true },
    )

    if (showAvailableWalletsDialog) {
        AvailableWalletsDialog(
            onDismiss = { showAvailableWalletsDialog = false },
            activeNodeId = activeNodeId,
            availableWallets = availableWallets,
            onWalletClick = { appViewModel.switchToWallet(it.nodeId) },
            onLockWallet = { appViewModel.resetActiveWallet() },
        )
    }
}

@Composable
private fun AvailableWalletsDialog(
    onDismiss: () -> Unit,
    activeNodeId: String,
    availableWallets: Map<String, UserWallet>,
    onWalletClick: (UserWallet) -> Unit,
    onLockWallet: () -> Unit,
) {
    FullScreenDialog(onDismiss = onDismiss) {
        val metadata by globalPrefs.getAvailableWalletsMeta.collectAsState(emptyMap())
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)) {  }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 96.dp)) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.background, shape = MaterialTheme.shapes.large)
                    .padding(8.dp)
            ) {
                AvailableWalletsList(
                    wallets = availableWallets,
                    walletsMetadata = metadata,
                    activeNodeId = activeNodeId,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    onWalletClick = onWalletClick,
                    canEdit = true,
                    bottomContent = {
                        Spacer(Modifier.height(4.dp))
                        val navController = navController
                        FilledButton(
                            text = stringResource(R.string.wallet_add_new),
                            icon = R.drawable.ic_plus_circle,
                            iconTint = MaterialTheme.colors.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate(Screen.InitWallet.route) },
                        )
                    }
                )
            }
        }

        TransparentFilledButton(
            text = stringResource(R.string.wallet_lock),
            icon = R.drawable.ic_lock,
            iconTint = negativeColor,
            textStyle = MaterialTheme.typography.button.copy(color = negativeColor),
            onClick = onLockWallet,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
