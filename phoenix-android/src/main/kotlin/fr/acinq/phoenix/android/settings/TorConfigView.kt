/*
 * Copyright 2022 ACINQ SAS
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

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.SettingSwitch
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.launch

@Composable
fun TorConfigView(

) {
    val log = logger("TorSettingView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val business = business
    val nc = navController
    val torPreference by UserPrefs.getIsTorEnabled(context).collectAsState(initial = null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.tor_settings_title),
            subtitle = stringResource(id = R.string.tor_settings_subtitle)
        )
        Card {
            val isTorEnabled = torPreference
            if (isTorEnabled == null) {
                Text(text = stringResource(id = R.string.tor_settings_unknown))
            } else {
                SettingSwitch(
                    title = stringResource(id = R.string.tor_settings_enabled),
                    description = stringResource(id = R.string.accessctrl_screen_lock_switch_desc),
                    icon = R.drawable.ic_lock,
                    enabled = true,
                    isChecked = isTorEnabled,
                    onCheckChangeAttempt = {
                        scope.launch {
                            business.appConfigurationManager.updateTorUsage(it)
                            UserPrefs.saveIsTorEnabled(context, it)
                        }
                    }
                )
            }
        }
    }
}