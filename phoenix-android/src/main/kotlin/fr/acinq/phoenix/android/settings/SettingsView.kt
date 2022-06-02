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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigate


@Composable
fun SettingsView() {
    val nc = navController
    ColumnScreen {
        RowHeader(title = stringResource(id = R.string.menu_settings), onBackClick = { nc.popBackStack() })
        // -- general
        SettingCategory(R.string.settings_general_title)
        Card {
            SettingButton(text = R.string.settings_about, icon = R.drawable.ic_help_circle, onClick = { nc.navigate(Screen.About) })
            SettingButton(text = R.string.settings_display_prefs, icon = R.drawable.ic_brush, onClick = { nc.navigate(Screen.Preferences) })
            SettingButton(text = R.string.settings_electrum, icon = R.drawable.ic_chain, onClick = { nc.navigate(Screen.ElectrumServer) })
            SettingButton(text = R.string.settings_tor, icon = R.drawable.ic_tor_shield, onClick = { })
            SettingButton(text = R.string.settings_payment_settings, icon = R.drawable.ic_tool, onClick = { })
        }

        // -- security
        SettingCategory(R.string.settings_security_title)
        Card {
            SettingButton(text = R.string.settings_access_control, icon = R.drawable.ic_unlock, onClick = { nc.navigate(Screen.AppLock) })
            SettingButton(text = R.string.settings_display_seed, icon = R.drawable.ic_key, onClick = { nc.navigate(Screen.DisplaySeed) })
        }

        // -- advanced
        SettingCategory(R.string.settings_advanced_title)
        Card {
            SettingButton(text = R.string.settings_list_channels, icon = R.drawable.ic_zap, onClick = { nc.navigate(Screen.Channels) })
            SettingButton(text = R.string.settings_logs, icon = R.drawable.ic_text, onClick = { nc.navigate(Screen.Logs)})
            SettingButton(text = R.string.settings_mutual_close, icon = R.drawable.ic_cross_circle, onClick = { nc.navigate(Screen.MutualClose) })
            SettingButton(text = R.string.settings_force_close, icon = R.drawable.ic_alert_triangle, onClick = { })
        }
        Spacer(Modifier.height(32.dp))
    }
}
