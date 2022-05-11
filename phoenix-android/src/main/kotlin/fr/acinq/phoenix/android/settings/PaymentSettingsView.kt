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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.utils.Prefs
import kotlinx.coroutines.launch
import java.text.NumberFormat


@Composable
fun PaymentSettingsView() {
    val log = logger("PaymentSettingsView")
    val nc = navController
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDescriptionDialog by rememberSaveable { mutableStateOf(false) }
    var showExpiryDialog by rememberSaveable { mutableStateOf(false) }

    val invoiceDefaultDesc by UserPrefs.getInvoiceDefaultDesc(LocalContext.current).collectAsState(initial = "")
    val invoiceDefaultExpiry by UserPrefs.getInvoiceDefaultExpiry(LocalContext.current).collectAsState(initial = -1L)

    if (showDescriptionDialog) {
        DefaultDescriptionInvoiceDialog(
            description = invoiceDefaultDesc,
            onDismiss = {
                scope.launch {
                    showDescriptionDialog = false
                }
            },
            onConfirm = {
                scope.launch {
                    UserPrefs.saveInvoiceDefaultDesc(context, it)
                }
                showDescriptionDialog = false
            }
        )
    }

    if (showExpiryDialog) {
        DefaultExpiryInvoiceDialog(
            expiry = invoiceDefaultExpiry,
            onDismiss = {
                scope.launch {
                    showExpiryDialog = false
                }
            },
            onConfirm = {
                scope.launch {
                    UserPrefs.saveInvoiceDefaultExpiry(context, it.toLongOrNull() ?: 60 * 60 * 24 * 7)
                }
                showExpiryDialog = false
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
                description = invoiceDefaultDesc.ifEmpty { stringResource(id = R.string.paymentsettings_defaultdesc_none) },
                onClick = { showDescriptionDialog = true}
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_expiry_title),
                description = stringResource(id = R.string.paymentsettings_expiry_value, NumberFormat.getInstance().format(invoiceDefaultExpiry)),
                onClick = { showExpiryDialog = true}
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_trampoline_fees_title),
                description = "",
                onClick = {}
            )

            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_paytoopen_fees_title),
                description = "",
                onClick = {showDescriptionDialog = true}
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DefaultExpiryInvoiceDialog(
    expiry: (Long),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var paymentExpiry by rememberSaveable { mutableStateOf(expiry.toString()) }

    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    onConfirm(paymentExpiry)
                },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .enableOrFade(enabled = true)
                    .padding(horizontal = 24.dp)
            ) {
                Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_title), style = MaterialTheme.typography.h5)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_description))
                Spacer(Modifier.height(8.dp))

                NumberInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = paymentExpiry,
                    placeholder = {
                        Text(stringResource(id = R.string.paymentsettings_expiry_dialog_hint)) },
                    onTextChange = {

                        val maxChar = 7
                        if (it.length <= maxChar) {
                            paymentExpiry = if (it.isEmpty()) {
                                ""
                            } else {
                                when (it.toLongOrNull()) {
                                    null -> paymentExpiry //old value
                                    else -> it   //new value
                                }
                            }
                        }
                    },
                    enabled = true
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DefaultDescriptionInvoiceDialog(
    description: (String),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var paymentDescription by rememberSaveable { mutableStateOf(description) }

    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    onConfirm(paymentDescription)
                },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            // -- checkbox
            // -- input
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .enableOrFade(enabled = true)
                    .padding(horizontal = 24.dp)
            ) {
                Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_title), style = MaterialTheme.typography.h5)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_description))
                Spacer(Modifier.height(8.dp))
                TextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = paymentDescription,
                    placeholder = {
                        Text(stringResource(id = R.string.paymentsettings_defaultdesc_dialog_hint)) },
                    onTextChange = {
                        paymentDescription = it
                    },
                    enabled = true
                )
            }
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentSettingsView()
}