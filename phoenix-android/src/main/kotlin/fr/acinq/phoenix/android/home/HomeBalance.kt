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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.managers.WalletBalance
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun HomeBalance(
    modifier: Modifier = Modifier,
    balance: MilliSatoshi?,
    swapInBalance: WalletBalance,
    finalWalletBalance: Satoshi,
    onNavigateToSwapInWallet: () -> Unit,
    onNavigateToFinalWallet: () -> Unit,
    balanceDisplayMode: HomeAmountDisplayMode,
) {
    if (balance == null) {
        ProgressView(modifier = modifier, text = stringResource(id = R.string.home_balance_loading))
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
            OnChainBalance(swapInBalance, finalWalletBalance, onNavigateToSwapInWallet, onNavigateToFinalWallet, balanceDisplayMode)
        }
    }
}

@Composable
private fun OnChainBalance(
    swapInBalance: WalletBalance,
    finalWalletBalance: Satoshi,
    onNavigateToSwapInWallet: () -> Unit,
    onNavigateToFinalWallet: () -> Unit,
    balanceDisplayMode: HomeAmountDisplayMode,
) {
    var showOnchainDialog by remember { mutableStateOf(false) }
    val availableOnchainBalance = swapInBalance.total.toMilliSatoshi() + finalWalletBalance.toMilliSatoshi()

    if (availableOnchainBalance > 0.msat) {
        val nextSwapTimeout by business.peerManager.swapInNextTimeout.collectAsState(initial = null)

        Clickable(
            modifier = Modifier.clip(CircleShape),
            onClick = { showOnchainDialog = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextWithIcon(
                    text = if (balanceDisplayMode == HomeAmountDisplayMode.REDACTED) "****" else {
                        stringResource(id = R.string.home_onchain_incoming, availableOnchainBalance.toPrettyString(preferredAmountUnit, primaryFiatRate, withUnit = true))
                    },
                    textStyle = MaterialTheme.typography.caption,
                    icon = R.drawable.ic_chain,
                    iconTint = MaterialTheme.typography.caption.color,
                    space = 4.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                PhoenixIcon(
                    resourceId = R.drawable.ic_help_circle,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .then(if (showOnchainDialog) Modifier.background(MaterialTheme.colors.primary) else Modifier),
                    tint = if (showOnchainDialog) MaterialTheme.colors.onPrimary else MaterialTheme.colors.primary,
                )

                if (showOnchainDialog) {
                    val liquidityPolicyInPrefs by userPrefs.getLiquidityPolicy.collectAsState(null)
                    val bitcoinUnit = LocalBitcoinUnits.current.primary
                    Dialog(
                        onDismiss = { showOnchainDialog = false },
                        buttons = null
                    ) {
                        Column(
                            modifier = Modifier
                                .background(mutedBgColor)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 1) funds being confirmed (swap deposits or LN channels waiting for confirmation
                            val fundsBeingConfirmed = (swapInBalance.unconfirmed + swapInBalance.weaklyConfirmed).toMilliSatoshi()
                            if (fundsBeingConfirmed > 0.msat) {
                                OnChainBalanceEntry(
                                    label = stringResource(id = R.string.home_swapin_confirming_title),
                                    icon = R.drawable.ic_clock,
                                    amount = fundsBeingConfirmed,
                                    description = { Text(text = stringResource(id = R.string.home_swapin_confirming_desc), style = MaterialTheme.typography.subtitle2) },
                                    onClick = onNavigateToSwapInWallet,
                                )
                            }

                            // 2) confirmed swaps that are not yet swapped
                            val fundsConfirmedNotLocked = swapInBalance.deeplyConfirmed
                            if (fundsConfirmedNotLocked > 0.sat) {
                                val expiringSoon = nextSwapTimeout?.let { it.second < 7 * 144 } ?: false // expiring in less than a week
                                OnChainBalanceEntry(
                                    label = stringResource(id = R.string.home_swapin_ready_title),
                                    icon = if (expiringSoon) R.drawable.ic_alert_triangle else R.drawable.ic_sleep,
                                    amount = fundsConfirmedNotLocked.toMilliSatoshi(),
                                    description = {
                                        Column {
                                            Text(
                                                text = when (val policy = liquidityPolicyInPrefs) {
                                                    is LiquidityPolicy.Disable -> stringResource(id = R.string.home_swapin_ready_desc_disabled)
                                                    is LiquidityPolicy.Auto -> stringResource(id = R.string.home_swapin_ready_desc_auto, policy.maxAbsoluteFee.toPrettyString(bitcoinUnit, withUnit = true))
                                                    else -> stringResource(id = R.string.home_swapin_ready_desc_generic)
                                                },
                                                style = MaterialTheme.typography.subtitle2,
                                            )

                                            if (expiringSoon) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(id = R.string.home_swapin_ready_expiring),
                                                    style = MaterialTheme.typography.subtitle2.copy(color = negativeColor),
                                                )
                                            }
                                        }
                                    },
                                    onClick = onNavigateToSwapInWallet,
                                )
                            }

                            // 3) confirmed swaps that have expired
                            val fundsConfirmedExpired = swapInBalance.locked + swapInBalance.readyForRefund
                            if (fundsConfirmedExpired > 0.sat) {
                                OnChainBalanceEntry(
                                    label = stringResource(id = R.string.home_swapin_expired_title),
                                    icon = R.drawable.ic_cross_circle,
                                    amount = fundsConfirmedExpired.toMilliSatoshi(),
                                    description = { Text(text = stringResource(id = R.string.home_swapin_expired_desc), style = MaterialTheme.typography.subtitle2) },
                                    onClick = onNavigateToSwapInWallet,
                                )
                            }

                            // 4) final wallet
                            if (finalWalletBalance > 0.sat) {
                                OnChainBalanceEntry(
                                    label = stringResource(id = R.string.home_final_title),
                                    icon = R.drawable.ic_chain,
                                    amount = finalWalletBalance.toMilliSatoshi(),
                                    description = { Text(text = stringResource(id = R.string.home_final_desc), style = MaterialTheme.typography.subtitle2) },
                                    onClick = onNavigateToFinalWallet,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnChainBalanceEntry(label: String, icon: Int, amount: MilliSatoshi, description: @Composable () -> Unit, onClick: () -> Unit) {
    Clickable(
        onClick = onClick,
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TextWithIcon(
                text = "$label: +${amount.toPrettyString(LocalBitcoinUnits.current.primary, withUnit = true)}",
                textStyle = MaterialTheme.typography.body2,
                icon = icon,
            )
            Spacer(modifier = Modifier.height(4.dp))
            description()
        }
    }
}
