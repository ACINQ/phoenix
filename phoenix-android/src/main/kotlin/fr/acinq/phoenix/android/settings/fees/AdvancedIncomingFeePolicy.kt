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
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.InlineNumberInput
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SettingSwitch
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.safeLet
import kotlin.math.roundToInt

@Composable
fun AdvancedIncomingFeePolicy(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val liquidityPreferredFees by UserPrefs.getLiquidityPreferredFee(context).collectAsState(null)
    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = "Advanced fee options",
        )

        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            TextWithIcon(
                text = "Attention! Do not use this screen if you don\'t understand what these options do.",
                icon = R.drawable.ic_alert_triangle,
                iconTint = orange,
                space = 12.dp,
                verticalAlignment = Alignment.Top
            )
        }

        safeLet(liquidityPreferredFees, liquidityPolicyInPrefs) { defaultFee, policyInPrefs ->
            var alwaysAcceptLN by remember { mutableStateOf(false) }
            var maxRelativeFeeBasisPoints by remember { mutableStateOf(defaultFee.maxRelativeFeeBasisPoints) }
            var maxAbsoluteFee by remember { mutableStateOf(defaultFee.maxAbsoluteFee) }

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
            }
            Card {
                SettingSwitch(
                    title = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_label),
                    description = stringResource(id = R.string.liquiditypolicy_modes_pay_to_open_description),
                    isChecked = alwaysAcceptLN,
                    onCheckChangeAttempt = { alwaysAcceptLN = it },
                    enabled = true
                )
            }


            Card {
                // TODO save to preferences & update node params
                Button(
                    text = stringResource(id = R.string.liquiditypolicy_save_button),
                    icon = R.drawable.ic_check,
                    modifier = Modifier
                        .fillMaxWidth()
                        .enableOrFade(false),
                    enabled = false,
                    onClick = {},
                )
            }

        } ?: Card {
            ProgressView(text = stringResource(id = R.string.liquiditypolicy_loading))
        }
    }
}
