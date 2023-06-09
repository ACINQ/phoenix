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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.data.BitcoinUnit
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LiquidityPolicyView(
    onBackClick: () -> Unit,
    onAdvancedClick: () -> Unit,
) {
    val log = logger("LiquidityPolicyView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liquidityPreferredFees by UserPrefs.getLiquidityPreferredFee(context).collectAsState(null)
    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    val electrumFeerate by business.peerManager.electrumFeerate.collectAsState()

    DefaultScreenLayout {
        DefaultScreenHeader(
            content = {
                Text(text = stringResource(id = R.string.liquiditypolicy_title))
                Spacer(Modifier.weight(1f))
                Button(
                    text = "Advanced",
                    padding = PaddingValues(8.dp),
                    textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp),
                    shape = CircleShape,
                    onClick = onAdvancedClick
                )
            },
            onBackClick = onBackClick
        )

        Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.liquiditypolicy_instructions))
        }

        safeLet(liquidityPreferredFees, liquidityPolicyInPrefs) { defaultFee, policyInPrefs ->

            var isPolicyDisabled by remember { mutableStateOf(policyInPrefs is LiquidityPolicy.Disable) }
            var maxRelativeFeeBasisPoints by remember { mutableStateOf(defaultFee.maxRelativeFeeBasisPoints) }
            var maxAbsoluteFee by remember { mutableStateOf(defaultFee.maxAbsoluteFee) }

            CardHeader(text = stringResource(id = R.string.liquiditypolicy_fees_header))
            Card {
                SettingSwitch(
                    title = "Automated fee policy",
                    isChecked = !isPolicyDisabled,
                    onCheckChangeAttempt = { isPolicyDisabled = !it },
                    enabled = true,
                    description = if (isPolicyDisabled) {
                        "All incoming payments that require additional liquidity will fail."
                    } else {
                        null
                    }
                )

                if (!isPolicyDisabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var isError by remember { mutableStateOf(false) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.liquiditypolicy_fees_base_label),
                                    style = MaterialTheme.typography.body2
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                when {
                                    maxAbsoluteFee < 200.sat -> {
                                        Text(
                                            text = "This value is too low, incoming payments will almost certainly fail.",
                                            style = MaterialTheme.typography.subtitle2.copy(color = negativeColor),
                                        )
                                    }
                                    maxAbsoluteFee < (electrumFeerate?.swapEstimationFee ?: 0.sat) -> {
                                        Text(
                                            text = "Incoming payments that need additional liquidity are expected to fail or remain on-chain",
                                            style = MaterialTheme.typography.subtitle2.copy(color = negativeColor),
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
                            InlineNumberInput(
                                value = maxAbsoluteFee.sat.toDouble(),
                                onValueChange = {
                                    when {
                                        it == null -> isError = true
                                        it < 0 -> isError = true
                                        it > 100_000 -> isError = true
                                        else -> {
                                            isError = false
                                            it.toLong().sat.let { maxAbsoluteFee = it }
                                        }
                                    }
                                },
                                isError = isError,
                                acceptDecimal = false,
                                trailingIcon = { Text(text = "sat") },
                                modifier = Modifier.width(130.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HSeparator(width = 50.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        when (val feerate = electrumFeerate) {
                            null -> ProgressView(text = stringResource(id = R.string.liquiditypolicy_fees_estimation_loading), progressCircleSize = 16.dp, padding = PaddingValues(0.dp))
                            else -> {
                                val fiatCurrency = LocalFiatCurrency.current
                                Row {
                                    PhoenixIcon(resourceId = R.drawable.ic_idea, tint = orange, modifier = Modifier.offset(y = 2.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = annotatedStringResource(
                                            id = R.string.liquiditypolicy_fees_estimation,
                                            feerate.swapEstimationFee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
                                            feerate.swapEstimationFee.toPrettyString(fiatCurrency, fiatRate, withUnit = true)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card {
                val peerManager = business.peerManager
                val notificationsManager = business.notificationsManager
                val newPolicy = when {
                    isPolicyDisabled -> LiquidityPolicy.Disable
                    else -> LiquidityPolicy.Auto(maxRelativeFeeBasisPoints = maxRelativeFeeBasisPoints, maxAbsoluteFee = maxAbsoluteFee)
                }
                val isEnabled = policyInPrefs != newPolicy
                Button(
                    text = stringResource(id = R.string.liquiditypolicy_save_button),
                    icon = R.drawable.ic_check,
                    modifier = Modifier
                        .fillMaxWidth()
                        .enableOrFade(isEnabled),
                    enabled = isEnabled,
                    onClick = {
                        scope.launch {
                            UserPrefs.saveLiquidityPolicy(context, newPolicy)
                            peerManager.updatePeerLiquidityPolicy(newPolicy)
                            notificationsManager.dismissAllNotifications()
                        }
                    },
                )
            }

        } ?: Card {
            ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
        }
    }
}
