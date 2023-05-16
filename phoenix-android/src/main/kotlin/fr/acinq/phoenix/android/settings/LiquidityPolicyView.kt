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

package fr.acinq.phoenix.android.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class LiquidityPolicyOptions {
    AUTO, TRUSTLESS, DISABLED
}

@Composable
fun LiquidityPolicyView(
    onBackClick: () -> Unit,
) {
    val log = logger("LiquidityPolicyView")
    val context = LocalContext.current
    val btcUnit = LocalBitcoinUnit.current
    val scope = rememberCoroutineScope()
    val liquidityPreferredFees by UserPrefs.getLiquidityPreferredFee(context).collectAsState(null)
    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.liquiditypolicy_title),
            helpMessage = stringResource(id = R.string.liquiditypolicy_help),
            helpMessageLink = stringResource(id = R.string.liquiditypolicy_help_link) to "https://phoenix.acinq.co/faq",
        )

        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = annotatedStringResource(id = R.string.liquiditypolicy_instructions))
        }

        safeLet(liquidityPreferredFees, liquidityPolicyInPrefs) { defaultFee, policyInPrefs ->

            val business = business

            var isPayToOpenDisabled by remember { mutableStateOf(false) }
            var maxRelativeFeeBasisPoints by remember { mutableStateOf(defaultFee.maxRelativeFeeBasisPoints) }
            var maxAbsoluteFee by remember { mutableStateOf(defaultFee.maxAbsoluteFee) }

            CardHeader(text = stringResource(id = R.string.liquiditypolicy_fees_header))
            Card(
                internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isError by remember { mutableStateOf(false) }
                    Text(
                        text = stringResource(id = R.string.liquiditypolicy_fees_base_label),
                        modifier = Modifier.alignByBaseline()
                    )
                    IconPopup(popupMessage = stringResource(id = R.string.liquiditypolicy_fees_base_help), spaceLeft = 8.dp, spaceRight = 0.dp)
                    Spacer(Modifier.weight(1f))
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
                        modifier = Modifier.width(150.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isError by remember { mutableStateOf(false) }
                    Text(
                        text = stringResource(id = R.string.liquiditypolicy_fees_prop_label),
                        modifier = Modifier.alignByBaseline()
                    )
                    IconPopup(popupMessage = stringResource(id = R.string.liquiditypolicy_fees_prop_help), spaceLeft = 8.dp, spaceRight = 0.dp)
                    Spacer(Modifier.weight(1f))
                    InlineNumberInput(
                        value = maxRelativeFeeBasisPoints.toDouble() / 100,
                        onValueChange = {
                            when {
                                it == null -> isError = true
                                it < 0 -> isError = true
                                it > 100 -> isError = true
                                else -> {
                                    isError = false
                                    it.let { maxRelativeFeeBasisPoints = (it * 100).roundToInt() }
                                }
                            }
                        },
                        acceptDecimal = false,
                        isError = isError,
                        trailingIcon = { Text(text = "%") },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }

            CardHeader(text = stringResource(id = R.string.liquiditypolicy_modes_header))
            Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                SwitchView(
                    text = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_label),
                    description = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_description),
                    checked = isPayToOpenDisabled,
                    onCheckedChange = { isPayToOpenDisabled = it }
                )
            }

            Card {
                val newPolicy = when {
                    maxAbsoluteFee == 0.sat && maxRelativeFeeBasisPoints == 0 -> LiquidityPolicy.Disable
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
                        }
                    },
                )
            }
        } ?: Card {
            ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
        }
    }
}
