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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.components.ScreenHeader
import fr.acinq.phoenix.android.components.SettingButton
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigate


@Composable
fun SettingsView() {
    val nc = navController
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .verticalScroll(scrollState)
    ) {
        ScreenHeader(title = stringResource(id = R.string.menu_settings), onBackClick = { nc.popBackStack() })
        // -- general
        SettingCategory(R.string.settings_general_title)
        SettingButton(text = R.string.settings_about, icon = R.drawable.ic_help_circle, onClick = { })
        SettingButton(text = R.string.settings_display_prefs, icon = R.drawable.ic_brush, onClick = { nc.navigate(Screen.Preferences) })
        SettingButton(text = R.string.settings_electrum, icon = R.drawable.ic_chain, onClick = { nc.navigate(Screen.ElectrumServer) })
        SettingButton(text = R.string.settings_tor, icon = R.drawable.ic_tor_shield, onClick = { })
        SettingButton(text = R.string.settings_payment_settings, icon = R.drawable.ic_tool, onClick = { })

        // -- security
        SettingCategory(R.string.settings_security_title)
        SettingButton(text = R.string.settings_access_control, icon = R.drawable.ic_unlock, onClick = { })
        SettingButton(text = R.string.settings_display_seed, icon = R.drawable.ic_key, onClick = { nc.navigate(Screen.DisplaySeed) })

        // -- advanced
        SettingCategory(R.string.settings_advanced_title)
        SettingButton(text = R.string.settings_list_channels, icon = R.drawable.ic_zap, onClick = { nc.navigate(Screen.Channels) })
        SettingButton(text = R.string.settings_logs, icon = R.drawable.ic_text, onClick = { })
        SettingButton(text = R.string.settings_mutual_close, icon = R.drawable.ic_cross_circle, onClick = { nc.navigate(Screen.MutualClose) })
        SettingButton(text = R.string.settings_force_close, icon = R.drawable.ic_alert_triangle, onClick = { })
    }
}

@Composable
fun SettingCategory(textResId: Int) {
    Text(
        text = stringResource(id = textResId),
        style = MaterialTheme.typography.subtitle1.copy(fontSize = 14.sp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, top = 24.dp, end = 0.dp, bottom = 4.dp)
    )
}

@Composable
fun Setting(modifier: Modifier = Modifier, title: String, description: String?, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(modifier)
            .padding(start = 50.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.subtitle2)
        Text(description ?: "", style = MaterialTheme.typography.caption)
    }
}
