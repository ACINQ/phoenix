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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.positiveColor
import kotlinx.coroutines.launch

@Composable
fun TorConfigView() {
    val scope = rememberCoroutineScope()
    val business = business
    val nc = navController
    val userPrefs = userPrefs
    val torEnabledState = userPrefs.getIsTorEnabled.collectAsState(initial = null)
    val connState = business.connectionsManager.connections.collectAsState()

    var showConfirmTorDialog by remember { mutableStateOf(false) }

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.tor_settings_title),
        )
        val isTorEnabled = torEnabledState.value
        Card {
            if (isTorEnabled == null) {
                ProgressView(text = stringResource(id = R.string.tor_settings_unknown), space = 12.dp)
            } else {
                SettingSwitch(
                    title = stringResource(id = if (isTorEnabled) R.string.tor_settings_enabled else R.string.tor_settings_disabled),
                    description = stringResource(id = R.string.tor_settings_subtitle),
                    icon = R.drawable.ic_tor_shield,
                    enabled = true,
                    isChecked = isTorEnabled,
                    onCheckChangeAttempt = { enableTor ->
                        scope.launch {
                            if (enableTor) {
                                showConfirmTorDialog = true
                            } else {
                                business.appConfigurationManager.updateTorUsage(false)
                                userPrefs.saveIsTorEnabled(false)
                            }
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
                    decoration = {
                        Row(modifier = Modifier.width(width = ButtonDefaults.IconSize), horizontalArrangement = Arrangement.Center) {
                            Surface(
                                shape = CircleShape,
                                color = when (torState) {
                                    Connection.ESTABLISHED -> positiveColor
                                    Connection.ESTABLISHING -> orange
                                    else -> negativeColor
                                },
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                    }
                )
            }
        }
    }

    if (showConfirmTorDialog) {
        var hasReadMessage by remember { mutableStateOf(false) }
        Dialog(
            onDismiss = { showConfirmTorDialog = false },
            title = stringResource(id = R.string.tor_confirm_dialog_title),
            buttons = {
                Button(text = stringResource(id = R.string.btn_cancel), onClick = { showConfirmTorDialog = false })
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    text = stringResource(id = R.string.btn_confirm),
                    icon = R.drawable.ic_check_circle,
                    onClick = {
                        scope.launch {
                            business.appConfigurationManager.updateTorUsage(true)
                            userPrefs.saveIsTorEnabled(true)
                        }
                        showConfirmTorDialog = false
                    },
                    enabled = hasReadMessage
                )
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(text = stringResource(id = R.string.tor_confirm_dialog_details_1))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = annotatedStringResource(id = R.string.tor_confirm_dialog_details_2))
                Spacer(modifier = Modifier.height(12.dp))
                Checkbox(text = stringResource(id = R.string.utils_ack), checked = hasReadMessage, onCheckedChange = { hasReadMessage = it })
            }
        }
    }
}
