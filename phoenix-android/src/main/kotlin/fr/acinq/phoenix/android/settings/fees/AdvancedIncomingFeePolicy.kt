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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.InlineNumberInput
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SettingSwitch
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.BitcoinUnit
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AdvancedIncomingFeePolicy(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peerManager = business.peerManager
    val notificationsManager = business.notificationsManager

    val maxSatFeePrefsFlow = UserPrefs.getIncomingMaxSatFeeInternal(context).collectAsState(null)
    val maxPropFeePrefsFlow = UserPrefs.getIncomingMaxPropFeeInternal(context).collectAsState(null)
    val liquidityPolicyInPrefsFlow = UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.liquiditypolicy_advanced_title),
        )

        val maxSatFeePrefs = maxSatFeePrefsFlow.value
        val maxPropFeePrefs = maxPropFeePrefsFlow.value
        val liquidityPolicyPrefs = liquidityPolicyInPrefsFlow.value

        if (maxSatFeePrefs != null && maxPropFeePrefs != null && liquidityPolicyPrefs != null) {
            if (liquidityPolicyPrefs is LiquidityPolicy.Disable) {
                Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = stringResource(id = R.string.liquiditypolicy_advanced_disable))
                }
            } else {
                WarningMessage(
                    header = stringResource(id = R.string.liquiditypolicy_advanced_disclaimer_header),
                    details = stringResource(id = R.string.liquiditypolicy_advanced_disclaimer_message),
                )

                val maxAbsoluteFee by remember { mutableStateOf(maxSatFeePrefs) }
                var maxRelativeFeeBasisPoints by remember { mutableStateOf<Int?>(maxPropFeePrefs) }
                var skipAbsoluteFeeCheck by remember { mutableStateOf(if (liquidityPolicyPrefs is LiquidityPolicy.Auto) liquidityPolicyPrefs.skipAbsoluteFeeCheck else false ) }

                CardHeader(text = stringResource(id = R.string.liquiditypolicy_advanced_verifications_title))
                Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    EditMaxPropFee(
                        maxPropFee = maxRelativeFeeBasisPoints,
                        onMaxPropFeeChange = { maxRelativeFeeBasisPoints = it }
                    )
                }

                CardHeader(text = stringResource(id = R.string.liquiditypolicy_advanced_overrides_title))
                Card {
                    SettingSwitch(
                        title = stringResource(id = R.string.liquiditypolicy_advanced_pay_to_open_label),
                        description = stringResource(id = R.string.liquiditypolicy_advanced_pay_to_open_help),
                        isChecked = skipAbsoluteFeeCheck,
                        onCheckChangeAttempt = { skipAbsoluteFeeCheck = it },
                        enabled = true
                    )
                }

                Card {
                    val newPolicy = maxRelativeFeeBasisPoints?.let { LiquidityPolicy.Auto(maxRelativeFeeBasisPoints = it, maxAbsoluteFee = maxAbsoluteFee, skipAbsoluteFeeCheck = skipAbsoluteFeeCheck) }
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
                            scope.launch {
                                newPolicy?.let {
                                    UserPrefs.saveLiquidityPolicy(context, newPolicy)
                                    peerManager.updatePeerLiquidityPolicy(newPolicy)
                                    notificationsManager.dismissAllNotifications()
                                }
                            }
                        },
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
            }
        }
    }
}

@Composable
private fun EditMaxPropFee(
    maxPropFee: Int?,
    onMaxPropFeeChange: (Int?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        var isError by remember { mutableStateOf(false) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.liquiditypolicy_advanced_fees_prop_label),
                style = MaterialTheme.typography.body2,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.liquiditypolicy_advanced_fees_prop_help),
                style = MaterialTheme.typography.subtitle2
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        InlineNumberInput(
            value = maxPropFee?.let { it.toDouble() / 100 },
            onValueChange = {
                when {
                    it == null || it < 0 || it > 100 -> {
                        isError = true
                        onMaxPropFeeChange(null)
                    }
                    else -> {
                        isError = false
                        onMaxPropFeeChange((it * 100).roundToInt())
                    }
                }
            },
            acceptDecimal = true,
            isError = isError,
            trailingIcon = { Text(text = "%") },
            modifier = Modifier.width(130.dp)
        )
    }
}

