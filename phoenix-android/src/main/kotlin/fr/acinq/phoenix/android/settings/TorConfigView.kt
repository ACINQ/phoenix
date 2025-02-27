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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Checkbox
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.settings.SettingSwitch
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedBgColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TorConfigView(
    appViewModel: AppViewModel,
    onBackClick: () -> Unit,
    onBusinessTeardown: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val business = business
    val userPrefs = userPrefs
    val torEnabledState = userPrefs.getIsTorEnabled.collectAsState(initial = null)

    var showConfirmTorDialog by remember { mutableStateOf(false) }

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.tor_settings_title),
        )
        val isTorEnabled = torEnabledState.value
        Card {
            if (isTorEnabled == null) {
                ProgressView(text = stringResource(id = R.string.utils_loading_prefs), space = 12.dp)
            } else {
                SettingSwitch(
                    title = stringResource(id = R.string.tor_settings_switch_label),
                    icon = R.drawable.ic_tor_shield,
                    enabled = true,
                    isChecked = isTorEnabled,
                    onCheckChangeAttempt = { showConfirmTorDialog = true }
                )
            }
        }

        if (isTorEnabled == true) {
            Card(internalPadding = PaddingValues(16.dp)) {
                val context = LocalContext.current
                Text(text = stringResource(id = R.string.tor_settings_instructions_title), style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.tor_dialog_enable_details_1))
                Spacer(Modifier.height(16.dp))
                BorderButton(text = stringResource(id = R.string.tor_settings_instructions_help_button), icon = R.drawable.ic_external_link, onClick = { openLink(context, "https://phoenix.acinq.co/faq#how-to-use-tor-on-phoenix") })
            }
        }
    }

    if (showConfirmTorDialog) {
        var hasReadMessage by remember { mutableStateOf(false) }
        val isTorEnabled = torEnabledState.value == true
        val application = application
        Dialog(
            onDismiss = { showConfirmTorDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false, usePlatformDefaultWidth = false),
            buttons = null
        ) {
            var isTearingDownBusiness by remember { mutableStateOf(false) }
            if (isTearingDownBusiness) {
                ProgressView(text = stringResource(id = R.string.tor_dialog_processing), modifier = Modifier.fillMaxWidth(), padding = PaddingValues(32.dp), horizontalArrangement = Arrangement.Center)
            } else {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isTorEnabled) {
                        Text(text = annotatedStringResource(id = R.string.tor_dialog_disable_title), style = MaterialTheme.typography.h4)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = annotatedStringResource(id = R.string.tor_dialog_disable_details_1))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = annotatedStringResource(id = R.string.tor_dialog_disable_details_2))
                    } else {
                        Text(text = stringResource(id = R.string.tor_dialog_enable_title_1), style = MaterialTheme.typography.h4)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = stringResource(id = R.string.tor_dialog_enable_details_1))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = annotatedStringResource(id = R.string.tor_dialog_enable_details_2))
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(color = mutedBgColor, shape = RoundedCornerShape(16.dp)) {
                            Checkbox(text = stringResource(id = R.string.utils_ack), checked = hasReadMessage, onCheckedChange = { hasReadMessage = it }, padding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Spacer(modifier = Modifier.width(32.dp))
                Row(
                    modifier = Modifier.padding(8.dp).align(Alignment.End)
                ) {
                    Button(text = stringResource(id = R.string.btn_cancel), onClick = { showConfirmTorDialog = false }, shape = RoundedCornerShape(16.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    val log = logger("TorConfigView")
                    Button(
                        text = stringResource(id = R.string.btn_confirm),
                        icon = R.drawable.ic_check_circle,
                        onClick = {
                            log.info("shutting down app")
                            val service = appViewModel.service ?: return@Button
                            scope.launch {
                                isTearingDownBusiness = true
                                service.shutdown()
                                application.shutdownBusiness()
                                business.appConfigurationManager.updateTorUsage(!isTorEnabled)
                                userPrefs.saveIsTorEnabled(!isTorEnabled)
                                application.resetBusiness()
                                delay(500)
                                showConfirmTorDialog = false
                                onBusinessTeardown()
                            }
                        },
                        enabled = isTorEnabled || hasReadMessage,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}
