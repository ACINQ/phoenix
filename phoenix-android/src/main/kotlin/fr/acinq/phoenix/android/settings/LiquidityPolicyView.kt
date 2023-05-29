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
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.data.BitcoinUnit
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed class LiquidityViewMode {
    object Basic : LiquidityViewMode()
    object Advanced : LiquidityViewMode()
}

private class LiquidityPolicyViewModel() : ViewModel() {
    val viewMode = mutableStateOf<LiquidityViewMode>(LiquidityViewMode.Basic)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LiquidityPolicyView(
    onBackClick: () -> Unit,
) {
    val log = logger("LiquidityPolicyView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liquidityPreferredFees by UserPrefs.getLiquidityPreferredFee(context).collectAsState(null)
    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    val vm = viewModel<LiquidityPolicyViewModel>()
    val electrumFeerate by business.peerManager.electrumFeerate.collectAsState()

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.liquiditypolicy_title),
            helpMessage = stringResource(id = R.string.lipsum_short),
            helpMessageLink = stringResource(id = R.string.liquiditypolicy_help_link) to "https://phoenix.acinq.co/faq",
        )

        Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.liquiditypolicy_instructions))
            Spacer(modifier = Modifier.height(16.dp))
            when (val feerate = electrumFeerate) {
                null -> ProgressView(text = "Retrieving feerate...", padding = PaddingValues(0.dp))
                else -> {
                    val fiatCurrency = LocalFiatCurrency.current
                    Text(
                        text = annotatedStringResource(
                            id = R.string.liquiditypolicy_fees_estimation,
                            feerate.swapEstimationFee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
                            feerate.swapEstimationFee.toPrettyString(fiatCurrency, fiatRate, withUnit = true)
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.liquiditypolicy_fees_estimation_feerate, feerate.nextBlock),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
            }
        }

        safeLet(liquidityPreferredFees, liquidityPolicyInPrefs) { defaultFee, policyInPrefs ->

            var isPayToOpenDisabled by remember { mutableStateOf(false) }
            var maxRelativeFeeBasisPoints by remember { mutableStateOf(defaultFee.maxRelativeFeeBasisPoints) }
            var maxAbsoluteFee by remember { mutableStateOf(defaultFee.maxAbsoluteFee) }

            CardHeader(text = stringResource(id = R.string.liquiditypolicy_fees_header))
            Card(
                internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isError by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.liquiditypolicy_fees_base_label),
                        )
                        Text(
                            text = stringResource(id = R.string.liquiditypolicy_fees_base_help),
                            style = MaterialTheme.typography.subtitle2
                        )
                    }
                    Spacer(Modifier.width(10.dp))
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
                electrumFeerate?.let { feerate ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (maxAbsoluteFee < 100.sat) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextWithIcon(
                                text = "This fee is very low ; many incoming payments will fail.",
                                textStyle = MaterialTheme.typography.subtitle2.copy(fontSize = 14.sp),
                                icon = R.drawable.ic_info,
                                iconTint = MaterialTheme.typography.subtitle2.color,
                                space = 10.dp,
                            )
                        } else if (maxAbsoluteFee < feerate.swapEstimationFee) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextWithIcon(
                                text = "Incoming payments that need additional liquidity are expected to fail or remain on-chain",
                                textStyle = MaterialTheme.typography.subtitle2.copy(fontSize = 14.sp),
                                icon = R.drawable.ic_info,
                                iconTint = MaterialTheme.typography.subtitle2.color,
                                space = 10.dp,
                            )
                        } else if (maxAbsoluteFee > (feerate.swapEstimationFee + 1000.sat).times(3)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextWithIcon(
                                text = "It's a bit high! Lorem ipsum dolor sit amet.",
                                textStyle = MaterialTheme.typography.subtitle2.copy(fontSize = 14.sp),
                                icon = R.drawable.ic_info,
                                iconTint = MaterialTheme.typography.subtitle2.color,
                                space = 10.dp,
                            )
                        }
                    }
                }
            }

            Card {
                val peerManager = business.peerManager
                val notificationsManager = business.notificationsManager
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
                            peerManager.updatePeerLiquidityPolicy(newPolicy)
                            notificationsManager.dismissAllNotifications()
                        }
                    },
                )
            }

            when (vm.viewMode.value) {
                LiquidityViewMode.Basic -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            text = stringResource(id = R.string.liquiditypolicy_modes_advanced),
                            icon = R.drawable.ic_chevron_down,
                            textStyle = MaterialTheme.typography.caption,
                            iconTint = MaterialTheme.typography.caption.color,
                            padding = PaddingValues(8.dp),
                            space = 8.dp,
                            onClick = { vm.viewMode.value = LiquidityViewMode.Advanced }
                        )
                    }
                }
                LiquidityViewMode.Advanced -> {
                    val dismissState = rememberDismissState(
                        confirmStateChange = {
                            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                                vm.viewMode.value = LiquidityViewMode.Basic
                            }
                            true
                        }
                    )
                    SwipeToDismiss(
                        state = dismissState,
                        background = {},
                        dismissThresholds = { FractionalThreshold(0.8f) }
                    ) {
                        Column {
                            CardHeader(text = stringResource(id = R.string.liquiditypolicy_modes_advanced))
                            Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    var isError by remember { mutableStateOf(false) }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(id = R.string.liquiditypolicy_fees_prop_label),
                                        )
                                        Text(
                                            text = stringResource(id = R.string.liquiditypolicy_fees_prop_help),
                                            style = MaterialTheme.typography.subtitle2
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
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
                                        modifier = Modifier.width(130.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                SwitchView(
                                    text = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_label),
                                    description = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_description),
                                    checked = isPayToOpenDisabled,
                                    onCheckedChange = { isPayToOpenDisabled = it }
                                )
                            }
                        }
                    }
                }
            }

        } ?: Card {
            ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
        }
    }
}
