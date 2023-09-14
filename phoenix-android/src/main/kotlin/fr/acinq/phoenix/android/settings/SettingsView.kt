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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.SettingButton
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigate
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import kotlinx.coroutines.launch


@Composable
fun SettingsView(
    noticesViewModel: NoticesViewModel
) {
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugClickCount by remember { mutableStateOf(0) }
    val notices = noticesViewModel.notices.values
    val notifications = business.notificationsManager.notifications.collectAsState()

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }) {
            Text(
                text = stringResource(id = R.string.menu_settings),
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { debugClickCount += 1 }
                    )
            )
        }

        // -- general
        CardHeader(text = stringResource(id = R.string.settings_general_title))
        Card {
            SettingButton(text = stringResource(R.string.settings_about), icon = R.drawable.ic_help_circle, onClick = { nc.navigate(Screen.About) })
            SettingButton(text = stringResource(R.string.settings_display_prefs), icon = R.drawable.ic_brush, onClick = { nc.navigate(Screen.Preferences) })
            SettingButton(text = stringResource(R.string.settings_payment_settings), icon = R.drawable.ic_tool, onClick = { nc.navigate(Screen.PaymentSettings) })
            SettingButton(text = stringResource(R.string.settings_liquidity_policy), icon = R.drawable.ic_settings, onClick = { nc.navigate(Screen.LiquidityPolicy) })
            SettingButton(text = stringResource(R.string.settings_payment_history), icon = R.drawable.ic_list, onClick = { nc.navigate(Screen.PaymentsHistory) })
            SettingButton(
                text = stringResource(R.string.settings_notifications) + ((notices.size + notifications.value.size).takeIf { it > 0 }?.let { " ($it)"} ?: ""),
                icon = R.drawable.ic_notification,
                onClick = { nc.navigate(Screen.Notifications) }
            )
        }

        // -- privacy & security
        CardHeader(text = stringResource(id = R.string.settings_security_title))
        Card {
            SettingButton(text = stringResource(R.string.settings_access_control), icon = R.drawable.ic_unlock, onClick = { nc.navigate(Screen.AppLock) })
            SettingButton(text = stringResource(R.string.settings_display_seed), icon = R.drawable.ic_key, onClick = { nc.navigate(Screen.DisplaySeed) })
            SettingButton(text = stringResource(R.string.settings_electrum), icon = R.drawable.ic_chain, onClick = { nc.navigate(Screen.ElectrumServer) })
            SettingButton(text = stringResource(R.string.settings_tor), icon = R.drawable.ic_tor_shield, onClick = { nc.navigate(Screen.TorConfig) })
        }

        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_advanced_title))
        Card {
            SettingButton(text = stringResource(R.string.settings_wallet_info), icon = R.drawable.ic_box, onClick = { nc.navigate(Screen.WalletInfo) })
            SettingButton(text = stringResource(R.string.settings_list_channels), icon = R.drawable.ic_zap, onClick = { nc.navigate(Screen.Channels) })
            SettingButton(text = stringResource(R.string.settings_logs), icon = R.drawable.ic_text, onClick = { nc.navigate(Screen.Logs) })
        }
        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_danger_title))
        Card {
//            SettingButton(text = R.string.settings_reset_wallet, icon = R.drawable.ic_trash, onClick = { nc.navigate(Screen.ResetWallet) })
            SettingButton(text = stringResource(R.string.settings_mutual_close), icon = R.drawable.ic_cross_circle, onClick = { nc.navigate(Screen.MutualClose) })
            SettingButton(
                text = stringResource(R.string.settings_force_close),
                textStyle = MaterialTheme.typography.button.copy(color = negativeColor),
                icon = R.drawable.ic_alert_triangle,
                iconTint = negativeColor,
                onClick = { nc.navigate(Screen.ForceClose) },
            )
        }

        if (debugClickCount > 10) {
            // -- debug
            CardHeader(text = "DEBUG")
            Card {
                Button(
                    text = "Switch to legacy app (DEBUG)",
                    icon = R.drawable.ic_user,
                    onClick = {
                        scope.launch {
                            LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
