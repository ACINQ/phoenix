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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.NodeParams
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigate
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import kotlinx.coroutines.launch


@Composable
fun SettingsView() {
    val nc = navController
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chain = business.chain

    DefaultScreenLayout {
        DefaultScreenHeader(title = stringResource(id = R.string.menu_settings), onBackClick = { nc.popBackStack() })

        // -- debug
        if (chain is NodeParams.Chain.Testnet) {
            CardHeader(text = "DEBUG")
            Card {
                Button(text = "Switch to Legacy app", icon = R.drawable.ic_user, onClick = {
                    scope.launch {
                        LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                    }
                }, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start)
            }
        }

        // -- general
        CardHeader(text = stringResource(id = R.string.settings_general_title))
        Card {
            SettingButton(text = R.string.settings_about, icon = R.drawable.ic_help_circle, onClick = { nc.navigate(Screen.About) })
            SettingButton(text = R.string.settings_display_prefs, icon = R.drawable.ic_brush, onClick = { nc.navigate(Screen.Preferences) })
            SettingButton(text = R.string.settings_payment_settings, icon = R.drawable.ic_tool, onClick = { nc.navigate(Screen.PaymentSettings)})
            SettingButton(text = R.string.settings_liquidity_policy, icon = R.drawable.ic_settings, onClick = { nc.navigate(Screen.LiquidityPolicy) })
            SettingButton(text = R.string.settings_payment_history, icon = R.drawable.ic_list, onClick = { nc.navigate(Screen.PaymentsHistory)})
            SettingButton(text = R.string.settings_notifications, icon = R.drawable.ic_notification, onClick = { nc.navigate(Screen.Notifications)})
        }

        // -- privacy & security
        CardHeader(text = stringResource(id = R.string.settings_security_title))
        Card {
            SettingButton(text = R.string.settings_access_control, icon = R.drawable.ic_unlock, onClick = { nc.navigate(Screen.AppLock) })
            SettingButton(text = R.string.settings_display_seed, icon = R.drawable.ic_key, onClick = { nc.navigate(Screen.DisplaySeed) })
            SettingButton(text = R.string.settings_electrum, icon = R.drawable.ic_chain, onClick = { nc.navigate(Screen.ElectrumServer) })
            SettingButton(text = R.string.settings_tor, icon = R.drawable.ic_tor_shield, onClick = { nc.navigate(Screen.TorConfig) })
        }

        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_advanced_title))
        Card {
            SettingButton(text = R.string.settings_wallet_info, icon = R.drawable.ic_box, onClick = { nc.navigate(Screen.WalletInfo) })
            SettingButton(text = R.string.settings_list_channels, icon = R.drawable.ic_zap, onClick = { nc.navigate(Screen.Channels) })
            SettingButton(text = R.string.settings_logs, icon = R.drawable.ic_text, onClick = { nc.navigate(Screen.Logs) })
        }
        // -- advanced
        CardHeader(text = stringResource(id = R.string.settings_danger_title))
        Card {
            SettingButton(text = R.string.settings_reset_wallet, icon = R.drawable.ic_trash, onClick = { nc.navigate(Screen.ResetWallet) })
            SettingButton(text = R.string.settings_mutual_close, icon = R.drawable.ic_cross_circle, onClick = { nc.navigate(Screen.MutualClose) })
            SettingButton(
                text = R.string.settings_force_close,
                textStyle = MaterialTheme.typography.button.copy(color = negativeColor),
                icon = R.drawable.ic_alert_triangle,
                iconTint = negativeColor,
                onClick = { nc.navigate(Screen.ForceClose) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
