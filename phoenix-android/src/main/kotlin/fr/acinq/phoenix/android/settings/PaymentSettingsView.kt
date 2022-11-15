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

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalWalletContext
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentage
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentageString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.label
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.data.PaymentOptionsConstants
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
    val prefsTrampolineMaxFee by UserPrefs.getTrampolineMaxFee(LocalContext.current).collectAsState(null)
    val trampolineFees = prefsTrampolineMaxFee ?: walletContext?.trampoline?.v2?.attempts?.last()?.export()

    val prefLnurlAuthKeyTypeState = UserPrefs.getLnurlAuthKeyType(context).collectAsState(initial = null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.paymentsettings_title),
            subtitle = stringResource(id = R.string.paymentsettings_subtitle)
        )
        Card {
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_defaultdesc_title),
                description = invoiceDefaultDesc.ifEmpty { stringResource(id = R.string.paymentsettings_defaultdesc_none) },
                onClick = { showDescriptionDialog = true }
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_expiry_title),
                description = when (invoiceDefaultExpiry) {
                    1 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_one_week)
                    2 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_two_weeks)
                    3 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_three_weeks)
                    else -> stringResource(id = R.string.paymentsettings_expiry_value, NumberFormat.getInstance().format(invoiceDefaultExpiry))
                },
                onClick = { showExpiryDialog = true }
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_trampoline_fees_title),
                description = trampolineFees?.let {
                    stringResource(id = R.string.paymentsettings_trampoline_fees_desc, trampolineFees.feeBase, trampolineFees.proportionalFeeAsPercentageString)
                } ?: stringResource(R.string.utils_unknown),
                onClick = { showTrampolineMaxFeeDialog = true }
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_paytoopen_fees_title),
                description = walletContext?.let {
                    stringResource(id = R.string.paymentsettings_paytoopen_fees_desc, String.format("%.2f", 100 * (it.payToOpen.v1.feePercent)), it.payToOpen.v1.minFeeSat)
                } ?: stringResource(id = R.string.utils_unknown),
                onClick = { showPayToOpenDialog = true }
            )
        }
        val prefLnurlAuthKeyType = prefLnurlAuthKeyTypeState.value
        if (prefLnurlAuthKeyType != null) {
            Card {
                val keyTypes = listOf<PreferenceItem<LNUrl.Auth.KeyType>>(
                    PreferenceItem(
                        item = LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE,
                        title = stringResource(id = R.string.lnurl_auth_keytype_default),
                        description = stringResource(id = R.string.lnurl_auth_keytype_default_desc)
                    ),
                    PreferenceItem(item = LNUrl.Auth.KeyType.LEGACY_KEY_TYPE, title = stringResource(id = R.string.lnurl_auth_keytype_legacy), description = stringResource(id = R.string.lnurl_auth_keytype_legacy_desc))
                )
                ListPreferenceButton(
                    title = stringResource(id = R.string.paymentsettings_lnurlauth_keytype_title),
                    subtitle = {
                        Text(
                            text = when (prefLnurlAuthKeyType) {
                                LNUrl.Auth.KeyType.LEGACY_KEY_TYPE -> stringResource(id = R.string.lnurl_auth_keytype_legacy)
                                LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE -> stringResource(id = R.string.lnurl_auth_keytype_default)
                                else -> stringResource(id = R.string.utils_unknown)
                            }
                        )
                    },
                    enabled = true,
                    selectedItem = prefLnurlAuthKeyType,
                    preferences = keyTypes,
                    onPreferenceSubmit = {
                        scope.launch { UserPrefs.saveLnurlAuthKeyType(context, it.item) }
                    }
                )
            }
        }
    }

    if (showDescriptionDialog) {
        DefaultDescriptionInvoiceDialog(
            description = invoiceDefaultDesc,
            onDismiss = { showDescriptionDialog = false },
            onConfirm = {
                scope.launch { UserPrefs.saveInvoiceDefaultDesc(context, it) }
                showDescriptionDialog = false
            }
        )
    }

    if (showExpiryDialog) {
        DefaultExpiryInvoiceDialog(
            expiry = invoiceDefaultExpiry,
            onDismiss = { showExpiryDialog = false },
            onConfirm = {
                scope.launch { UserPrefs.saveInvoiceDefaultExpiry(context, it.toLong()) }
                showExpiryDialog = false
            }
        )
    }

    if (showTrampolineMaxFeeDialog && trampolineFees != null) {
        TrampolineMaxFeesDialog(
            initialTrampolineMaxFee = trampolineFees,
            isCustom = prefsTrampolineMaxFee != null,
            onDismiss = { showTrampolineMaxFeeDialog = false },
            onConfirm = {
                scope.launch { UserPrefs.saveTrampolineMaxFee(context, it) }
                showTrampolineMaxFeeDialog = false
            }
        )
    }

    if (showPayToOpenDialog) {
        PayToOpenDialog(onDismiss = { showPayToOpenDialog = false })
    }
}

@Composable
private fun DefaultExpiryInvoiceDialog(
    expiry: (Long),
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var paymentExpiry by rememberSaveable { mutableStateOf(expiry.toFloat()) }

    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.paymentsettings_expiry_dialog_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = { onConfirm(paymentExpiry) },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_description))
            Spacer(Modifier.height(16.dp))
            Slider(
                value = paymentExpiry,
                onValueChange = { paymentExpiry = it },
                valueRange = 604800f..1814400f,
                steps = 1,
            )
            Row {
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_one_week),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_two_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_three_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DefaultDescriptionInvoiceDialog(
    description: (String),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var paymentDescription by rememberSaveable { mutableStateOf(description) }

    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = { onConfirm(paymentDescription) },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_description))
            Spacer(Modifier.height(24.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text = paymentDescription,
                label = { Text(stringResource(id = R.string.paymentsettings_defaultdesc_dialog_hint)) },
                onTextChange = { paymentDescription = it },
                maxLines = 3,
                maxChars = 180,
            )
        }
    }
}

@Composable
private fun TrampolineMaxFeesDialog(
    initialTrampolineMaxFee: TrampolineFees,
    isCustom: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TrampolineFees?) -> Unit,
) {
    var useCustomMaxFee by rememberSaveable { mutableStateOf(isCustom) }
    var feeBase by rememberSaveable { mutableStateOf<Long?>(initialTrampolineMaxFee.feeBase.toLong()) }
    var feeProportional by rememberSaveable { mutableStateOf<Double?>(initialTrampolineMaxFee.proportionalFeeAsPercentage) }

    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.paymentsettings_trampoline_fees_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    if (useCustomMaxFee) {
                        safeLet(feeBase, feeProportional) { base, prop ->
                            onConfirm(TrampolineFees(base.sat, Converter.percentageToPerMillionths(prop), initialTrampolineMaxFee.cltvExpiryDelta))
                        }
                    } else {
                        onConfirm(null)
                    }
                },
                modifier = Modifier.enableOrFade(!useCustomMaxFee || (feeBase != null && feeProportional != null)),
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Checkbox(
                text = stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_override_default_checkbox),
                checked = useCustomMaxFee,
                onCheckedChange = { useCustomMaxFee = it },
            )
            Spacer(Modifier.height(8.dp))
            NumberInput(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_label)) },
                placeholder = { Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_base_fee_hint)) },
                initialValue = feeBase?.toDouble(),
                onValueChange = { feeBase = it?.toLong() },
                enabled = useCustomMaxFee,
                minErrorMessage = stringResource(
                    R.string.paymentsettings_trampoline_fees_dialog_base_below_min,
                    PaymentOptionsConstants.minBaseFee.toMilliSatoshi().toPrettyString(BitcoinUnit.Sat, withUnit = true)
                ),
                minValue = PaymentOptionsConstants.minBaseFee.toLong().toDouble(),
                maxErrorMessage = stringResource(
                    R.string.paymentsettings_trampoline_fees_dialog_base_above_max,
                    PaymentOptionsConstants.maxBaseFee.toMilliSatoshi().toPrettyString(BitcoinUnit.Sat, withUnit = true)
                ),
                maxValue = PaymentOptionsConstants.maxBaseFee.toLong().toDouble(),
                acceptDecimal = false
            )
            Spacer(Modifier.height(8.dp))
            NumberInput(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_label)) },
                placeholder = { Text(stringResource(id = R.string.paymentsettings_trampoline_fees_dialog_proportional_fee_hint)) },
                initialValue = feeProportional,
                onValueChange = { feeProportional = it },
                enabled = useCustomMaxFee,
                minValue = PaymentOptionsConstants.minProportionalFeePercent,
                minErrorMessage = stringResource(
                    R.string.paymentsettings_trampoline_fees_dialog_proportional_below_min,
                    PaymentOptionsConstants.minProportionalFeePercent
                ),
                maxValue = PaymentOptionsConstants.maxProportionalFeePercent,
                maxErrorMessage = stringResource(
                    R.string.paymentsettings_trampoline_fees_dialog_proportional_above_max,
                    PaymentOptionsConstants.maxProportionalFeePercent
                ),
            )
        }
    }
}

@Composable
private fun PayToOpenDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        title = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_title),
        onDismiss = onDismiss,
        buttons = { },
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_message))
            Spacer(Modifier.height(8.dp))
            WebLink(
                text = stringResource(id = R.string.paymentsettings_paytoopen_fees_dialog_message_clickable),
                url = "https://phoenix.acinq.co/faq#what-are-the-fees"
            )
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentSettingsView()
}