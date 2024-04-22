/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.IconPopup
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.utils.extensions.nextTimeout
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.roundToInt


@Composable
fun SwapInWallet(
    onBackClick: () -> Unit,
    onViewChannelPolicyClick: () -> Unit,
    onAdvancedClick: () -> Unit,
) {
    val btcUnit = LocalBitcoinUnit.current

    val liquidityPolicyInPrefs by userPrefs.getLiquidityPolicy.collectAsState(null)
    val swapInWallet by business.peerManager.swapInWallet.collectAsState()
    var showAdvancedMenuPopIn by remember { mutableStateOf(false) }

    DefaultScreenLayout(isScrollable = true) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            content = {
                Text(text = stringResource(id = R.string.walletinfo_onchain_swapin))
                IconPopup(
                    popupMessage = stringResource(id = R.string.walletinfo_onchain_swapin_help),
                    popupLink = stringResource(id = R.string.walletinfo_onchain_swapin_help_faq_link)
                            to "https://phoenix.acinq.co/faq#can-i-deposit-funds-on-chain-to-phoenix-and-how-long-does-it-take-before-i-can-use-it"
                )
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.TopEnd) {
                    DropdownMenu(expanded = showAdvancedMenuPopIn, onDismissRequest = { showAdvancedMenuPopIn = false }) {
                        DropdownMenuItem(onClick = onAdvancedClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(stringResource(R.string.swapin_signer_title), style = MaterialTheme.typography.body1)
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
        )
        Card {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                when (val policy = liquidityPolicyInPrefs) {
                    is LiquidityPolicy.Disable -> {
                        Text(text = annotatedStringResource(id = R.string.walletinfo_onchain_swapin_policy_disabled_details))
                    }
                    is LiquidityPolicy.Auto -> {
                        Text(text = annotatedStringResource(id = R.string.walletinfo_onchain_swapin_policy_auto_details, policy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true)))
                        swapInWallet?.swapInParams?.maxConfirmations?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(text = annotatedStringResource(id = R.string.walletinfo_onchain_swapin_policy_auto_details_timeout, ceil(it.toDouble() / (144 * 30)).toInt()))
                        }
                    }
                    null -> {}
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                text = stringResource(id = R.string.walletinfo_onchain_swapin_policy_view),
                textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.primary),
                icon = R.drawable.ic_settings,
                iconTint = MaterialTheme.colors.primary,
                onClick = onViewChannelPolicyClick,
                modifier = Modifier.fillMaxWidth(),
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                space = 8.dp,
                horizontalArrangement = Arrangement.Start,
            )
        }

        swapInWallet?.let { wallet ->
            if (wallet.all.balance == 0.sat) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.walletinfo_onchain_swapin_empty),
                    style = MaterialTheme.typography.caption, modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                val waitingForSwap = wallet.unconfirmed + wallet.weaklyConfirmed
                if (waitingForSwap.isNotEmpty()) {
                    WaitingForConfirmations(wallet = wallet)
                }

                if (wallet.deeplyConfirmed.balance > 0.sat) {
                    ReadyForSwapView(wallet = wallet)
                }

                if (wallet.lockedUntilRefund.balance > 0.sat) {
                    LockedView(wallet = wallet)
                }

                if (wallet.readyForRefund.balance > 0.sat) {
                    RefundView(wallet = wallet)
                }
            }
        }
    }
}

@Composable
private fun WaitingForConfirmations(
    wallet: WalletState.WalletWithConfirmations
) {
    val minConfirmations = wallet.swapInParams.minConfirmations
    val displayedCount by remember { mutableStateOf(3) }
    val confirming = wallet.weaklyConfirmed + wallet.unconfirmed

    CardHeader(text = stringResource(id = R.string.walletinfo_confirming_title, minConfirmations))
    Card {
        confirming.take(displayedCount).forEach {
            UtxoRow(it, (minConfirmations - wallet.confirmationsNeeded(it)) to minConfirmations)
        }
        if (displayedCount < confirming.size) {
            Text(
                text = stringResource(id = R.string.walletinfo_confirming_more, confirming.size - displayedCount),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 10.dp),
                style = MaterialTheme.typography.caption.copy(fontSize = 14.sp)
            )
        }
    }

}

@Composable
private fun ReadyForSwapView(
    wallet: WalletState.WalletWithConfirmations,
) {
    val swappableUtxos = wallet.deeplyConfirmed
    val swappableBalance = swappableUtxos.balance.toMilliSatoshi()
    val notifications by business.notificationsManager.notifications.collectAsState()
    val lastSwapFailedNotification = notifications.map { it.second }
        .filterIsInstance<Notification.PaymentRejected>()
        .firstOrNull {
            it.source == LiquidityEvents.Source.OnChainWallet && it.amount == swappableBalance
        }

    CardHeader(text = stringResource(id = R.string.walletinfo_swappable_title))
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BalanceRow(balance = swappableBalance)

        if (lastSwapFailedNotification != null) {
            Spacer(modifier = Modifier.height(8.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(8.dp))
            val btcUnit = LocalBitcoinUnit.current
            Text(
                text = stringResource(id = R.string.walletinfo_onchain_swapin_last_attempt, lastSwapFailedNotification.createdAt.toRelativeDateString()),
                style = MaterialTheme.typography.body2,
            )
            Text(
                text = when (lastSwapFailedNotification) {
                    is Notification.OverAbsoluteFee -> stringResource(
                        id = R.string.inappnotif_payment_rejected_over_absolute,
                        lastSwapFailedNotification.fee.toPrettyString(btcUnit, withUnit = true),
                        lastSwapFailedNotification.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true),
                    )
                    is Notification.OverRelativeFee -> stringResource(
                        id = R.string.inappnotif_payment_rejected_over_relative,
                        lastSwapFailedNotification.fee.toPrettyString(btcUnit, withUnit = true),
                        DecimalFormat("0.##").format(lastSwapFailedNotification.maxRelativeFeeBasisPoints.toDouble() / 100),
                    )
                    is Notification.FeePolicyDisabled -> stringResource(id = R.string.walletinfo_onchain_swapin_last_attempt_disabled)
                    is Notification.ChannelsInitializing -> stringResource(id = R.string.walletinfo_onchain_swapin_last_attempt_channels_init)
                },
            )
        }

        val remainingBlocks = remember(wallet) { wallet.nextTimeout?.second }
        when {
            remainingBlocks == null -> {}
            remainingBlocks <= 144 -> {
                Spacer(modifier = Modifier.height(12.dp))
                TextWithIcon(
                    text = stringResource(id = R.string.walletinfo_onchain_swapin_timeout_1day),
                    textStyle = MaterialTheme.typography.caption.copy(color = negativeColor),
                    icon = R.drawable.ic_alert_triangle,
                    iconTint = negativeColor
                )
            }
            remainingBlocks < 144 * 30 -> {
                Spacer(modifier = Modifier.height(12.dp))
                TextWithIcon(
                    text = stringResource(id = R.string.walletinfo_onchain_swapin_timeout, ceil(remainingBlocks.toDouble() / 144).toInt()),
                    icon = R.drawable.ic_alert_triangle,
                    iconTint = MaterialTheme.typography.body1.color
                )
            }
        }
    }
}

@Composable
private fun LockedView(
    wallet: WalletState.WalletWithConfirmations,
) {
    CardHeader(text = stringResource(id = R.string.walletinfo_onchain_swapin_locked_title))
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BalanceRow(balance = wallet.lockedUntilRefund.balance.toMilliSatoshi())
        Spacer(modifier = Modifier.height(8.dp))
        val closestRefundBlockWait = remember {
            val oldestLocked = wallet.lockedUntilRefund.maxOf { wallet.confirmations(it) }
            wallet.swapInParams.refundDelay - oldestLocked
        }
        Text(
            text = stringResource(id = R.string.walletinfo_onchain_swapin_locked_description, (closestRefundBlockWait.toDouble() / 144).roundToInt()),
            style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
        )
    }
}

@Composable
private fun RefundView(
    wallet: WalletState.WalletWithConfirmations,
) {
    CardHeader(text = stringResource(id = R.string.walletinfo_onchain_swapin_refund_title))
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BalanceRow(balance = wallet.readyForRefund.balance.toMilliSatoshi())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.walletinfo_onchain_swapin_refund_description),
            style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
        )
    }
}