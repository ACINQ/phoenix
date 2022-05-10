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


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.launch


@Composable
fun PaymentSettingsView() {
    val log = logger("PaymentSettingsView")
    val nc = navController
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        MyDialog(
            onDismiss = {
                scope.launch {
                    showDialog = false
                }
            }
        )
    }

    SettingScreen {
        SettingHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.paymentsettings_title),
            subtitle = stringResource(id = R.string.paymentsettings_subtitle))

        Card {
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_defaultdesc_title),
                description = "Hello World",
                onClick = { showDialog = true}
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_expiry_title),
                description = "Hello World",
                onClick = { showDialog = true}
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_trampoline_fees_title),
                description = "",
                onClick = {}
            )

            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_paytoopen_fees_title),
                description = "",
                onClick = {showDialog = true}
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentSettingsView()
}