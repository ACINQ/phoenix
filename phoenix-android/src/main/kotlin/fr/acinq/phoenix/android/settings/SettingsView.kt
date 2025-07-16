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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.Notice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.buttons.MenuButton
import fr.acinq.phoenix.android.components.buttons.SurfaceFilledButton
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.inputs.CurrencyConverter
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.wallet.AvailableWalletView
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.canRequestLiquidity
import kotlinx.coroutines.launch


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
            MenuButton(text = stringResource(id = R.string.settings_reset_wallet), icon = R.drawable.ic_remove, onClick = { nc.navigate(Screen.ResetWallet.route) })
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

    val activeNodeId = activeWalletInUI?.first ?: return

    var showNewWalletDialog by remember { mutableStateOf(false) }
    val metadata by globalPrefs.getAvailableWalletsMeta.collectAsState(emptyMap())

    Row(
        modifier = Modifier
            .fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        ActiveWalletView(nodeId = activeWalletInUI!!.first, metadata = metadata[activeNodeId])
        Spacer(Modifier.width(32.dp))
        Button(
            icon = R.drawable.ic_menu_dots,
            shape = CircleShape,
            padding = PaddingValues(8.dp, 12.dp),
            backgroundColor = MaterialTheme.colors.surface,
            onClick = { showNewWalletDialog = true },
        )
    }

    if (showNewWalletDialog) {
        AvailableWalletsDialog(
            onDismiss = { showNewWalletDialog = false },
            activeNodeId = activeNodeId,
            availableWallets = availableWallets,
            onWalletClick = { appViewModel.updateDesiredNodeId(it) },
            availableWalletsMetadata = metadata
        )
    }
}

@Composable
private fun AvailableWalletsDialog(onDismiss: () -> Unit, activeNodeId: String, availableWallets: Map<String, UserWallet>, availableWalletsMetadata: Map<String, UserWalletMetadata>, onWalletClick: (String) -> Unit) {
    Dialog(onDismiss = onDismiss, buttons = null) {
        Column(
            modifier = Modifier
                .background(mutedBgColor)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val businessMap by BusinessRepo.businessFlow.collectAsState()
            availableWallets.map { (nodeId, _) ->
                AvailableWalletView(nodeId = nodeId, walletMetadata = availableWalletsMetadata[nodeId], isCurrent = activeNodeId == nodeId, isActive = businessMap[nodeId] != null, onClick = onWalletClick)
            }

            HSeparator(width = 100.dp)

            val navController = navController
            SurfaceFilledButton(
                text = "Add new wallet",
                icon = R.drawable.ic_plus_circle,
                iconTint = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.InitWallet.route) },
            )
        }
    }
}

@Composable
fun RowScope.ActiveWalletView(nodeId: String, metadata: UserWalletMetadata?) {

    var showEditWalletDialog by remember { mutableStateOf(false) }

    Clickable(
        onClick = { showEditWalletDialog = true },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Surface(
                color = metadata?.color ?: mutedBgColor,
                shape = CircleShape,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wallet),
                    contentDescription = "wallet",
                    modifier = Modifier.padding(8.dp).size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = metadata?.name?.takeIf { it.isNotBlank() } ?: "Unnamed Wallet", modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(2.dp))
                Text(text = nodeId, modifier = Modifier.widthIn(max = 200.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2)
            }
        }
    }

    if (showEditWalletDialog) {
        ModalBottomSheet(
            onDismiss = { showEditWalletDialog = false },
            containerColor = MaterialTheme.colors.background,
            internalPadding = PaddingValues(0.dp),
            skipPartiallyExpanded = true,
        ) {
            val scope = rememberCoroutineScope()
            val globalPrefs = globalPrefs
            var nameInput by remember { mutableStateOf(metadata?.name ?: "") }

            Card(internalPadding = PaddingValues(16.dp)) {
                Text(text = "Wallet Details", style = MaterialTheme.typography.h4, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TextInput(
                    text = nameInput,
                    onTextChange = { nameInput = it },
                    maxLines = 1,
                    singleLine = true,
                    placeholder = { Text(text = "Enter a name for your wallet", style = MaterialTheme.typography.caption) },
                    staticLabel = "Name"
                )
                Spacer(Modifier.height(16.dp))
                TextInput(
                    text = nodeId,
                    onTextChange = { },
                    maxLines = 4,
                    enabled = false,
                    enabledEffect = false,
                    staticLabel = "NodeId",
                    textStyle = MaterialTheme.typography.caption,
                    showResetButton = false
                )
                Spacer(Modifier.height(24.dp))
                FilledButton(
                    text = stringResource(R.string.btn_save),
                    icon = R.drawable.ic_check,
                    onClick = {
                        scope.launch {
                            globalPrefs.saveAvailableWalletMeta(name = nameInput.takeIf { it.isNotBlank() }, nodeId = nodeId)
                            showEditWalletDialog = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                text = stringResource(R.string.btn_cancel),
                textStyle = MaterialTheme.typography.caption,
                onClick = { showEditWalletDialog = false },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
