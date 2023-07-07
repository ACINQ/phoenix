/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.settings.fees

import androidx.compose.foundation.layout.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.MempoolFeerate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiquidityPolicyView(
    onBackClick: () -> Unit,
    onAdvancedClick: () -> Unit,
) {
    val log = logger("LiquidityPolicyView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val maxSatFeePrefsFlow = UserPrefs.getIncomingMaxSatFeeInternal(context).collectAsState(null)
    val maxPropFeePrefsFlow = UserPrefs.getIncomingMaxPropFeeInternal(context).collectAsState(null)
    val liquidityPolicyPrefsFlow = UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    val peerManager = business.peerManager
    val notificationsManager = business.notificationsManager
    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    val channels by business.peerManager.channelsFlow.collectAsState()

    val maxSatFeePrefs = maxSatFeePrefsFlow.value
    val maxPropFeePrefs = maxPropFeePrefsFlow.value
    val liquidityPolicyPrefs = liquidityPolicyPrefsFlow.value

    DefaultScreenLayout {
        var showAdvancedMenuPopIn by remember { mutableStateOf(false) }
        DefaultScreenHeader(
            content = {
                Text(text = stringResource(id = R.string.liquiditypolicy_title))
                IconPopup(popupMessage = stringResource(id = R.string.liquiditypolicy_help))
                if (liquidityPolicyPrefs is LiquidityPolicy.Auto) {
                    Spacer(Modifier.weight(1f))
                    Box(contentAlignment = Alignment.TopEnd) {
                        DropdownMenu(expanded = showAdvancedMenuPopIn, onDismissRequest = { showAdvancedMenuPopIn = false }) {
                            DropdownMenuItem(onClick = onAdvancedClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text(stringResource(R.string.liquiditypolicy_advanced_menu), style = MaterialTheme.typography.body1)
                            }
                        }
                        Button(
                            icon = R.drawable.ic_menu_dots,
                            iconTint = MaterialTheme.colors.onSurface,
                            padding = PaddingValues(12.dp),
                            onClick = { showAdvancedMenuPopIn = true }
                        )
                    }
                }
            },
            onBackClick = onBackClick
        )

        Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.liquiditypolicy_instructions))
        }

        if (maxSatFeePrefs != null && maxPropFeePrefs != null && liquidityPolicyPrefs != null) {
            var isPolicyDisabled by remember { mutableStateOf(liquidityPolicyPrefs is LiquidityPolicy.Disable) }
            var maxAbsoluteFee by remember { mutableStateOf<Satoshi?>(maxSatFeePrefs) }

            CardHeader(text = stringResource(id = R.string.liquiditypolicy_fees_header))
            Card {
                SettingSwitch(
                    title = stringResource(R.string.liquiditypolicy_disable_label),
                    isChecked = !isPolicyDisabled,
                    onCheckChangeAttempt = { isPolicyDisabled = !it },
                    enabled = true,
                    description = if (isPolicyDisabled) {
                        stringResource(R.string.liquiditypolicy_disable_desc)
                    } else {
                        null
                    }
                )
                if (!isPolicyDisabled) {
                    EditMaxFee(maxAbsoluteFee = maxAbsoluteFee, onMaxFeeChange = { maxAbsoluteFee = it }, mempoolFeerate = mempoolFeerate, hasNoChannels = channels.isNullOrEmpty())
                }
            }

            var showSuccessFeedback by remember { mutableStateOf(false) }
            Card {
                val keyboardManager = LocalSoftwareKeyboardController.current
                val skipAbsoluteFeeCheck = if (liquidityPolicyPrefs is LiquidityPolicy.Auto) liquidityPolicyPrefs.skipAbsoluteFeeCheck else false
                val newPolicy = when {
                    isPolicyDisabled -> LiquidityPolicy.Disable
                    else -> maxAbsoluteFee?.let { LiquidityPolicy.Auto(maxRelativeFeeBasisPoints = maxPropFeePrefs, maxAbsoluteFee = it, skipAbsoluteFeeCheck = skipAbsoluteFeeCheck) }
                }
                val isEnabled = newPolicy != null && liquidityPolicyPrefs != newPolicy
                Button(
                    text = stringResource(id = R.string.liquiditypolicy_save_button),
                    icon = R.drawable.ic_check,
                    textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .enableOrFade(isEnabled),
                    enabled = isEnabled,
                    onClick = {
                        keyboardManager?.hide()
                        scope.launch {
                            if (newPolicy != null) {
                                UserPrefs.saveLiquidityPolicy(context, newPolicy)
                                if (newPolicy is LiquidityPolicy.Auto) showSuccessFeedback = true
                                peerManager.updatePeerLiquidityPolicy(newPolicy)
                                notificationsManager.dismissAllNotifications()
                                delay(5000)
                                showSuccessFeedback = false
                            }
                        }
                    },
                )
            }

            if (showSuccessFeedback) {
                Text(
                    text = stringResource(id = R.string.liquiditypolicy_save_done),
                    style = MaterialTheme.typography.body1.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
            }
        }
    }
}

@Composable
private fun EditMaxFee(
    hasNoChannels: Boolean,
    maxAbsoluteFee: Satoshi?,
    onMaxFeeChange: (Satoshi?) -> Unit,
    mempoolFeerate: MempoolFeerate?,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            var isError by remember { mutableStateOf(false) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.liquiditypolicy_fees_base_label),
                    style = MaterialTheme.typography.body2
                )
                Spacer(modifier = Modifier.height(2.dp))
                when {
                    maxAbsoluteFee == null -> {
                        Text(
                            text = stringResource(id = R.string.validation_invalid_amount),
                            style = MaterialTheme.typography.subtitle2.copy(color = negativeColor),
                        )
                    }
                    maxAbsoluteFee < 150.sat -> {
                        Text(
                            text = stringResource(id = R.string.liquiditypolicy_fees_base_too_low),
                            style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface),
                        )
                    }
                    maxAbsoluteFee < (mempoolFeerate?.swapEstimationFee(hasNoChannels) ?: 0.sat) -> {
                        Text(
                            text = stringResource(id = R.string.liquiditypolicy_fees_base_below_estimation),
                            style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface),
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(id = R.string.liquiditypolicy_fees_base_help),
                            style = MaterialTheme.typography.subtitle2
                        )
                    }
                }

            }
            Spacer(Modifier.width(12.dp))
            InlineSatoshiInput(
                amount = maxAbsoluteFee,
                onAmountChange = {
                    when {
                        it == null || it < 0.sat || it > 500_000.sat -> {
                            isError = true
                            onMaxFeeChange(null)
                        }
                        else -> {
                            isError = false
                            onMaxFeeChange(it.toLong().sat)
                        }
                    }
                },
                isError = isError,
                trailingIcon = { Text(text = "sat") },
                modifier = Modifier.width(150.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HSeparator(width = 50.dp)
        Spacer(modifier = Modifier.height(12.dp))
        when (val feerate = mempoolFeerate) {
            null -> ProgressView(text = stringResource(id = R.string.liquiditypolicy_fees_estimation_loading), progressCircleSize = 16.dp, padding = PaddingValues(0.dp))
            else -> {
                val fiatCurrency = LocalFiatCurrency.current
                Row {
                    PhoenixIcon(resourceId = R.drawable.ic_info, modifier = Modifier.offset(y = 2.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = annotatedStringResource(
                            id = R.string.liquiditypolicy_fees_estimation,
                            feerate.swapEstimationFee(hasNoChannels).toPrettyString(BitcoinUnit.Sat, withUnit = true),
                            feerate.swapEstimationFee(hasNoChannels).toPrettyString(fiatCurrency, fiatRate, withUnit = true)
                        )
                    )
                }
            }
        }
    }
}