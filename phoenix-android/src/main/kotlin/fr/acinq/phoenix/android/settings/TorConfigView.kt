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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.positiveColor
import kotlinx.coroutines.launch

@Composable
fun TorConfigView(

) {
    val log = logger("TorSettingView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val business = business
    val nc = navController
    val torEnabledState = UserPrefs.getIsTorEnabled(context).collectAsState(initial = null)
    val connState = business.connectionsManager.connections.collectAsState()

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.tor_settings_title),
            subtitle = stringResource(id = R.string.tor_settings_subtitle)
        )
        val isTorEnabled = torEnabledState.value
        Card {
            if (isTorEnabled == null) {
                ProgressView(text = stringResource(id = R.string.tor_settings_unknown), space = 12.dp)
            } else {
                SettingSwitch(
                    title = stringResource(id = if (isTorEnabled) R.string.tor_settings_enabled else R.string.tor_settings_disabled),
                    icon = R.drawable.ic_tor_shield,
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

        if (isTorEnabled == true) {
            Card {
                val torState = connState.value.tor
                SettingWithDecoration(
                    title = when (torState) {
                        is Connection.CLOSED -> stringResource(R.string.tor_settings_state_closed)
                        Connection.ESTABLISHING -> stringResource(R.string.tor_settings_state_starting)
                        Connection.ESTABLISHED -> stringResource(R.string.tor_settings_state_started)
                        else -> stringResource(R.string.tor_settings_state_unknown)
                    },
                    description = { Text(text = stringResource(id = R.string.tor_settings_state_about_perfs)) },
                    decoration = {
                        Row(modifier = Modifier.width(width = ButtonDefaults.IconSize), horizontalArrangement = Arrangement.Center) {
                            Surface(
                                shape = CircleShape,
                                color = when (torState) {
                                    Connection.ESTABLISHED -> positiveColor()
                                    Connection.ESTABLISHING -> orange
                                    else -> negativeColor()
                                },
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                    }
                )
            }
        }
    }
}
