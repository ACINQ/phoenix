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
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.LocalWalletContext
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.utils.Converter
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

    val walletContext = LocalWalletContext.current
    val trampolineMaxFees by UserPrefs.getTrampolineMaxFee(LocalContext.current).collectAsState(null)
    val trampolineFees = trampolineMaxFees?.let { customTrampolineMaxFees ->
        if (customTrampolineMaxFees.feeBase.toLong() < 0L) {
            walletContext?.let {
                val trampolineFees = it.trampoline.v2.attempts.last()
                TrampolineFees(
                    Satoshi(trampolineFees.feeBaseSat),
                    trampolineFees.feePerMillionths,
                    CltvExpiryDelta(trampolineFees.cltvExpiry)
                )
            }
        } else {
            TrampolineFees(
                customTrampolineMaxFees.feeBase,
                customTrampolineMaxFees.feeProportional,
                customTrampolineMaxFees.cltvExpiryDelta
            )
        }
    }


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
                    UserPrefs.saveInvoiceDefaultExpiry(context, it.toLong())
                }
                showExpiryDialog = false
            }
        )
    }

    if (showTrampolineMaxFeeDialog) {
        TrampolineMaxFeesDialog(
            trampolineMaxFees = trampolineFees,
            onDismiss = {
                scope.launch {
                    showTrampolineMaxFeeDialog = false
                }
            },
            onConfirm = { feeBaseParam, feeProportionalParam, expiryDeltaParam ->

                val feeProportional = feeProportionalParam?.toDoubleOrNull()
                if (feeBaseParam == null || feeProportional == null)
                {
                    Toast.makeText(context, R.string.paymentsettings_trampoline_fees_dialog_invalid, Toast.LENGTH_SHORT).show()
                }
                else
                {
                    val feeProportionalPercent = Converter.percentageToPerMillionths(feeProportionalParam.toString())

                    if (feeBaseParam.sat > 50_000.sat)
                    {
                        Toast.makeText(context, R.string.paymentsettings_trampoline_fees_dialog_base_too_high, Toast.LENGTH_SHORT).show()
                    }
                    else if (feeProportionalPercent > 1000000)
                    {
                        Toast.makeText(context, R.string.paymentsettings_trampoline_fees_dialog_proportional_too_high, Toast.LENGTH_SHORT).show()
                    }
                    else {
                        scope.launch {
                            val trampolineMax = TrampolineFees(Satoshi(feeBaseParam), Converter.percentageToPerMillionths(feeProportional.toString()), CltvExpiryDelta(expiryDeltaParam))
                            UserPrefs.saveTrampolineMaxFee(context, trampolineMax)

                            showTrampolineMaxFeeDialog = false
                        }
                    }
                }
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
                description = trampolineFees?.let {  stringResource(id = R.string.paymentsettings_trampoline_fees_desc, trampolineFees.feeBase, Converter.perMillionthsToPercentageString(trampolineFees.feeProportional)) },
                onClick = { showTrampolineMaxFeeDialog = true }
            )

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
    onConfirm: (Float) -> Unit,
) {
    var paymentExpiry by rememberSaveable { mutableStateOf(expiry.toFloat()) }

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

        Column(
            Modifier
                .fillMaxWidth()
                .enableOrFade(enabled = true)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_title), style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_description))
            Spacer(Modifier.height(8.dp))

            Row {
                Slider(
                    value = paymentExpiry,
                    onValueChange = {
                        //sliderPosition.value = it
                        paymentExpiry = it
                    },
                    valueRange = 604800f..1814400f,
                    steps = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }
            Row {
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_dialog_one_week),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.8f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_dialog_two_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.8f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_dialog_three_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.8f)
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

        Column(
            Modifier
                .fillMaxWidth()
                .enableOrFade(enabled = true)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_title), style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_description))
            Spacer(Modifier.height(8.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text= paymentDescription,
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


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrampolineMaxFeesDialog(
    trampolineMaxFees: (TrampolineFees?),
    onDismiss: () -> Unit,
    onConfirm: (Long?, String?, Int) -> Unit,
) {
    var feeProportional by rememberSaveable { mutableStateOf( trampolineMaxFees?.let { Converter.perMillionthsToPercentageString(it.feeProportional)}) }

    var feeBase by rememberSaveable { mutableStateOf(trampolineMaxFees?.let { it.feeBase.toLong() }) }

    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    onConfirm(feeBase, feeProportional, 144)
                },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .enableOrFade(enabled = true)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_override_default_checkbox), style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(16.dp))

            Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_label), style = MaterialTheme.typography.subtitle2)

            NumberInput(
                modifier = Modifier.fillMaxWidth(),
                initialValue = feeBase ?: -1L,
                placeholder = {
                    Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_hint)) },
                enabled = true,
                onTextChange = {
                    feeBase = it
                },
            )

            Spacer(Modifier.height(16.dp))

            Text(text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_label), style = MaterialTheme.typography.subtitle2)
            // max fee proportional
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                maxChar = 10,
                text= feeProportional ?: "",
                placeholder = {
                    Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_hint)) },
                onTextChange = {
                    feeProportional = it
                },
                enabled = true
            )
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
        Column(
            Modifier
                .fillMaxWidth()
                .enableOrFade(enabled = true)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(text = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_title), style = MaterialTheme.typography.h4)
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

private fun openLink(context: Context, link: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentSettingsView()
}