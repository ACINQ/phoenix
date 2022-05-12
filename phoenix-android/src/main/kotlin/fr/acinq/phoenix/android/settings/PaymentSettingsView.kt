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


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.LocalWalletContext
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
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
    var showTrampolineMaxFeeDialog by rememberSaveable { mutableStateOf(false) }
    var showPayToOpenDialog by rememberSaveable { mutableStateOf(false) }

    val invoiceDefaultDesc by UserPrefs.getInvoiceDefaultDesc(LocalContext.current).collectAsState(initial = "")
    val invoiceDefaultExpiry by UserPrefs.getInvoiceDefaultExpiry(LocalContext.current).collectAsState(initial = -1L)

    val trampolineMaxFees by UserPrefs.getTrampolineMaxFee(LocalContext.current).collectAsState(
        TrampolineFees(Satoshi(1L), 2L, CltvExpiryDelta(144))
    )

    val walletContext = LocalWalletContext.current

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

    if (showTrampolineMaxFeeDialog) {
        TrampolineMaxFeesDialog(
            trampolineMaxFees = trampolineMaxFees,
            onDismiss = {
                scope.launch {
                    showTrampolineMaxFeeDialog = false
                }
            },
            onConfirm = {
                scope.launch {
                    UserPrefs.saveTrampolineMaxFee(context, it)
                }
                showTrampolineMaxFeeDialog = false
            }
        )
    }


    if (showPayToOpenDialog) {
        PayToOpenDialog(
            onDismiss = {
                showPayToOpenDialog = false
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
                description = trampolineMaxFees?.let { it.feeBase.toString() + " " + it.feeProportional.toString() },
                onClick = {showTrampolineMaxFeeDialog = true}
            )

            //val walletContext = business?.appConfigurationManager?.chainContext?.collectAsState(null)?.value
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_paytoopen_fees_title),
                description = walletContext?.let {
                    stringResource(id = R.string.paymentsettings_paytoopen_fees_desc, String.format("%.2f", 100 * (it.payToOpen.v1.feePercent)),it.payToOpen.v1.minFeeSat )
                },
                onClick = {showPayToOpenDialog = true}
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


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrampolineMaxFeesDialog(
    trampolineMaxFees: (TrampolineFees),
    onDismiss: () -> Unit,
    onConfirm: (TrampolineFees) -> Unit,
) {
    var feeBase by rememberSaveable { mutableStateOf(trampolineMaxFees.feeBase.toLong().toString()) }
    var feeProportional by rememberSaveable { mutableStateOf(trampolineMaxFees.feeProportional.toString()) }

    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    onConfirm(TrampolineFees(Satoshi(feeBase.toLong()), feeProportional.toLong(), CltvExpiryDelta(144)))
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
                Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_override_default_checkbox), style = MaterialTheme.typography.h5)
                Spacer(Modifier.height(16.dp))
                //Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_description))
                //Spacer(Modifier.height(8.dp))

                Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_label), style = MaterialTheme.typography.subtitle2)
                // max fee
                NumberInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = feeBase,
                    placeholder = {
                        Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_hint)) },
                    onTextChange = {
                        val maxChar = 5
                        if (it.length <= maxChar) {
                            feeBase = if (it.isEmpty()) {
                                ""
                            } else {
                                when (it.toLongOrNull()) {
                                    null -> feeBase //old value
                                    else -> it   //new value
                                }
                            }
                        }
                    },
                    enabled = true
                )
                Spacer(Modifier.height(16.dp))

                Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_label), style = MaterialTheme.typography.subtitle2)
                // max fee proportional
                NumberInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = feeProportional,
                    placeholder = {
                        Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_hint)) },
                    onTextChange = {
                        val maxChar = 3
                        if (it.length <= maxChar) {
                            feeProportional = if (it.isEmpty()) {
                                ""
                            } else {
                                when (it.toLongOrNull()) {
                                    null -> feeProportional //old value
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
private fun PayToOpenDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismiss = onDismiss,
        buttons = { Spacer(Modifier.height(24.dp))},
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                Text(text = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_title), style = MaterialTheme.typography.h5)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_message))
                Spacer(Modifier.height(8.dp))
                val apiString = AnnotatedString.Builder()
                apiString.pushStyle(
                    style = SpanStyle(
                        color = Blue,
                        textDecoration = TextDecoration.Underline
                    )
                )
                apiString.append(stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_message_clickable))
                val context = LocalContext.current
                Text(
                    modifier = Modifier.clickable(enabled = true) {
                         openLink(context, "https://phoenix.acinq.co/faq#what-are-the-fees")
                    },
                    text = apiString.toAnnotatedString(),
                )
            }
        }
    }
}

private fun openLink(context: Context, link: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentSettingsView()
}