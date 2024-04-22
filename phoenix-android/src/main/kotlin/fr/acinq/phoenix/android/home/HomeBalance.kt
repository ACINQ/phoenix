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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.managers.WalletBalance
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun HomeBalance(
    modifier: Modifier = Modifier,
    balance: MilliSatoshi?,
    swapInBalance: WalletBalance,
    unconfirmedChannelsBalance: MilliSatoshi,
    onShowSwapInWallet: () -> Unit,
    balanceDisplayMode: HomeAmountDisplayMode,
) {
    if (balance == null) {
        ProgressView(modifier = modifier, text = stringResource(id = R.string.home__balance_loading))
    } else {
        val isAmountRedacted = balanceDisplayMode == HomeAmountDisplayMode.REDACTED
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmountView(
                amount = balance,
                amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 40.sp),
                unitTextStyle = MaterialTheme.typography.h3.copy(fontWeight = FontWeight.Light, color = MaterialTheme.colors.primary),
                isRedacted = isAmountRedacted,
                onClick = { userPrefs, inFiat ->
                    val mode = userPrefs.getHomeAmountDisplayMode.firstOrNull()
                    when {
                        inFiat && mode == HomeAmountDisplayMode.BTC -> userPrefs.saveHomeAmountDisplayMode(HomeAmountDisplayMode.REDACTED)
                        mode == HomeAmountDisplayMode.BTC -> userPrefs.saveHomeAmountDisplayMode(HomeAmountDisplayMode.FIAT)
                        mode == HomeAmountDisplayMode.FIAT -> userPrefs.saveHomeAmountDisplayMode(HomeAmountDisplayMode.REDACTED)
                        mode == HomeAmountDisplayMode.REDACTED -> userPrefs.saveHomeAmountDisplayMode(HomeAmountDisplayMode.BTC)
                        else -> Unit
                    }
                }
            )
            IncomingBalance(swapInBalance, unconfirmedChannelsBalance, onShowSwapInWallet, balanceDisplayMode)
        }
    }
}

@Composable
private fun IncomingBalance(
    swapInBalance: WalletBalance,
    pendingChannelsBalance: MilliSatoshi,
    onShowSwapInWallet: () -> Unit,
    balanceDisplayMode: HomeAmountDisplayMode,
) {
    var showSwapInHelp by remember { mutableStateOf(false) }
    val balance = swapInBalance.total.toMilliSatoshi() + pendingChannelsBalance
    if (balance > 0.msat) {
        val nextSwapTimeout by business.peerManager.swapInNextTimeout.collectAsState(initial = null)
        val pendingSwapBalance = swapInBalance.unconfirmed + swapInBalance.weaklyConfirmed
        Clickable(
            modifier = Modifier.clip(CircleShape),
            onClick = { showSwapInHelp = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextWithIcon(
                    text = if (balanceDisplayMode == HomeAmountDisplayMode.REDACTED) "****" else {
                        stringResource(id = R.string.home__onchain_incoming, balance.toPrettyString(preferredAmountUnit, fiatRate, withUnit = true))
                    },
                    textStyle = MaterialTheme.typography.caption,
                    icon = when {
                        nextSwapTimeout?.let { it.second < 144 } ?: false -> R.drawable.ic_alert_triangle
                        pendingSwapBalance == 0.sat && pendingChannelsBalance == 0.msat -> R.drawable.ic_sleep
                        else -> R.drawable.ic_clock
                    },
                    iconTint = MaterialTheme.typography.caption.color,
                    space = 4.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                PhoenixIcon(
                    resourceId = R.drawable.ic_help_circle,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .then(if (showSwapInHelp) Modifier.background(MaterialTheme.colors.primary) else Modifier),
                    tint = if (showSwapInHelp) MaterialTheme.colors.onPrimary else MaterialTheme.colors.primary,
                )

                if (showSwapInHelp) {
                    val liquidityPolicyInPrefs by userPrefs.getLiquidityPolicy.collectAsState(null)
                    val bitcoinUnit = LocalBitcoinUnit.current
                    PopupDialog(
                        onDismiss = { showSwapInHelp = false },
                        message = when {
                            swapInBalance.deeplyConfirmed > 0.sat -> {
                                when (val policy = liquidityPolicyInPrefs) {
                                    is LiquidityPolicy.Disable -> {
                                        stringResource(id = R.string.home_swapin_help_ready_disabled)
                                    }
                                    is LiquidityPolicy.Auto -> {
                                        stringResource(id = R.string.home_swapin_help_ready_fee, policy.maxAbsoluteFee.toPrettyString(bitcoinUnit, withUnit = true))
                                    }
                                    else -> {
                                        stringResource(id = R.string.home_swapin_help_generic)
                                    }
                                }
                            }
                            pendingSwapBalance > 0.sat && swapInBalance.deeplyConfirmed == 0.sat -> {
                                stringResource(id = R.string.home_swapin_help_pending)
                            }
                            else -> {
                                stringResource(id = R.string.home_swapin_help_generic)
                            }
                        },
                        button = {
                            Button(
                                text = stringResource(id = R.string.home_swapin_help_button),
                                onClick = onShowSwapInWallet,
                                textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.primary, fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
