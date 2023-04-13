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
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import kotlinx.coroutines.launch

private enum class LiquidityPolicyOptions {
    AUTO, TRUSTLESS, DISABLED
}

@Composable
fun LiquidityPolicyView(
    onBackClick: () -> Unit,
) {
    val log = logger("ChannelsPolicyView")
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

        safeLet(liquidityPreferredFees, liquidityPolicyInPrefs) { defaultFee, policyInPrefs ->

            var visiblePolicy by remember {
                mutableStateOf(
                    when (policyInPrefs) {
                        is LiquidityPolicy.Auto -> LiquidityPolicyOptions.AUTO
                        is LiquidityPolicy.Disable -> LiquidityPolicyOptions.DISABLED
                    }
                )
            }

            var maxFeeBasisPoints by remember { mutableStateOf(defaultFee.maxFeeBasisPoints) }
            var maxFeeFloor by remember { mutableStateOf(defaultFee.maxFeeFloor) }
            val feeEditor: @Composable () -> Unit = {
                FeePolicyEditor(
                    maxFeeBasisPoints = maxFeeBasisPoints, maxFeeFloor = maxFeeFloor,
                    onFeeBasisChange = { maxFeeBasisPoints = it }, onFeeFloorChange = { maxFeeFloor = it }
                )
            }

            Card {
                Clickable(onClick = { visiblePolicy = LiquidityPolicyOptions.AUTO }) {
                    Column {
                        PolicyRow(
                            title = stringResource(id = R.string.liquiditypolicy_auto_title),
                            description = stringResource(id = R.string.liquiditypolicy_auto_description, maxFeeFloor.toPrettyString(btcUnit, withUnit = true)),
                            isActive = policyInPrefs is LiquidityPolicy.Auto,
                            isSelected = visiblePolicy == LiquidityPolicyOptions.AUTO,
                        )
                        if (visiblePolicy == LiquidityPolicyOptions.AUTO) { feeEditor() }
                    }
                }
                Clickable(onClick = { visiblePolicy = LiquidityPolicyOptions.TRUSTLESS }) {
                    Column {
                        PolicyRow(
                            title = stringResource(id = R.string.liquiditypolicy_trustless_title),
                            description = stringResource(id = R.string.liquiditypolicy_trustless_description),
                            isActive = false, // TODO: add trustless policy
                            isSelected = visiblePolicy == LiquidityPolicyOptions.TRUSTLESS,
                        )
                        if (visiblePolicy == LiquidityPolicyOptions.TRUSTLESS) { feeEditor() }
                    }
                }
                Clickable(onClick = { visiblePolicy = LiquidityPolicyOptions.DISABLED }) {
                    PolicyRow(
                        title = stringResource(id = R.string.liquiditypolicy_disabled),
                        description = stringResource(id = R.string.liquiditypolicy_disabled_description),
                        isActive = policyInPrefs is LiquidityPolicy.Disable,
                        isSelected = visiblePolicy == LiquidityPolicyOptions.DISABLED,
                    )
                }
            }

            Card {
                val newPolicy = when (visiblePolicy) {
                    LiquidityPolicyOptions.AUTO -> LiquidityPolicy.Auto(maxFeeBasisPoints = maxFeeBasisPoints, maxFeeFloor = maxFeeFloor)
                    LiquidityPolicyOptions.TRUSTLESS -> LiquidityPolicy.Auto(maxFeeBasisPoints = maxFeeBasisPoints, maxFeeFloor = maxFeeFloor)
                    LiquidityPolicyOptions.DISABLED -> LiquidityPolicy.Disable
                }
                val isEnabled = policyInPrefs != newPolicy
                Button(
                    text = "Save policy",
                    icon = R.drawable.ic_check,
                    modifier = Modifier
                        .fillMaxWidth()
                        .enableOrFade(isEnabled),
                    enabled = isEnabled,
                    onClick = {
                        scope.launch { UserPrefs.saveLiquidityPolicy(context, newPolicy) }
                    },
                )
            }
        } ?: Card {
            ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
        }
    }
}

@Composable
private fun FeePolicyEditor(
    maxFeeBasisPoints: Int,
    maxFeeFloor: Satoshi,
    onFeeBasisChange: (Int) -> Unit,
    onFeeFloorChange: (Satoshi) -> Unit,
) {
    val log = logger("FeePolicyEditor")
    val btcUnit = LocalBitcoinUnit.current
    Column(
        modifier = Modifier.padding(start = 50.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.liquiditypolicy_fee_max, maxFeeFloor.toPrettyString(btcUnit, withUnit = true)),
                modifier = Modifier.width(120.dp),
                style = MaterialTheme.typography.body2.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.width(16.dp))
            Slider(
                value = maxFeeFloor.sat.toFloat(),
                onValueChange = { onFeeFloorChange(it.toLong().sat) },
                valueRange = 300f..10_000f, // max 10k sat
                modifier = Modifier.weight(1f),
            )
        }
        val isSanityCheckEnabled = maxFeeBasisPoints == 10_00
        SwitchView(
            text = stringResource(id = R.string.liquiditypolicy_fee_sanity_check),
            textStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
            description = if (isSanityCheckEnabled) {
                stringResource(id = R.string.liquiditypolicy_fee_sanity_check_enabled, maxFeeBasisPoints / 100)
            } else {
                stringResource(id = R.string.liquiditypolicy_fee_sanity_check_disabled)
            },
            checked = isSanityCheckEnabled,
            onCheckedChange = { onFeeBasisChange(if (it) 10_00 else 100_00) }
        )
    }
}

@Composable
private fun PolicyRow(
    title: String,
    description: String,
    isActive: Boolean,
    isSelected: Boolean,
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Row {
                Text(text = title, style = MaterialTheme.typography.h4, modifier = Modifier.alignByBaseline())
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "[Current]", color = MaterialTheme.colors.primary, modifier = Modifier.alignByBaseline())
                }
            }
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.subtitle2)
            }
        }
    }
}
