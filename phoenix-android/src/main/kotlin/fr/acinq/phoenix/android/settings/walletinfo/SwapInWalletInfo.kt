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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs

@Composable
fun SwapInWalletInfo(
    onBackClick: () -> Unit,
    onViewChannelPolicyClick: () -> Unit,
) {
    val context = LocalContext.current
    val btcUnit = LocalBitcoinUnit.current

    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)
    val swapInWallet by business.peerManager.swapInWallet.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.walletinfo_onchain_swapin), helpMessage = stringResource(id = R.string.walletinfo_onchain_swapin_help))
        Card(
            internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onViewChannelPolicyClick,
        ) {
            when (val policy = liquidityPolicyInPrefs) {
                is LiquidityPolicy.Disable -> {
                    Text(text = stringResource(id = R.string.walletinfo_onchain_swapin_policy_disabled_details))
                }
                is LiquidityPolicy.Auto -> {
                    Text(text = annotatedStringResource(id = R.string.walletinfo_onchain_swapin_policy_auto_details, policy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true)))
                }
                null -> {}
            }
            Spacer(Modifier.height(12.dp))
            TextWithIcon(text = stringResource(id = R.string.walletinfo_onchain_swapin_policy_view), textStyle = MaterialTheme.typography.caption.copy(fontSize = 14.sp), icon = R.drawable.ic_settings, iconTint = MaterialTheme.typography.caption.color)
        }

        swapInWallet?.let { wallet ->
            if (wallet.all.balance == 0.sat) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = stringResource(id = R.string.walletinfo_onchain_swapin_empty), style = MaterialTheme.typography.caption, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                if (wallet.deeplyConfirmed.balance > 0.sat) {
                    SwappableBalanceView(balance = wallet.deeplyConfirmed.balance.toMilliSatoshi())
                }
                val waitingForSwap = wallet.unconfirmed + wallet.weaklyConfirmed
                if (waitingForSwap.isNotEmpty()) {
                    NotSwappableWalletView(wallet = wallet)
                }
            }
        }
    }
}

@Composable
private fun SwappableBalanceView(
    balance: MilliSatoshi,
) {
    CardHeader(text = stringResource(id = R.string.walletinfo_swappable_title))
    Card(
        modifier = Modifier.fillMaxWidth(),
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BalanceRow(balance = balance)
    }
}

@Composable
private fun NotSwappableWalletView(
    wallet: WalletState.WalletWithConfirmations
) {
    val minConfirmations = wallet.swapInParams.minConfirmations
    CardHeader(text = stringResource(id = R.string.walletinfo_not_swappable_title, minConfirmations))
    Card {
        wallet.weaklyConfirmed.forEach {
            UtxoRow(it, (minConfirmations - wallet.confirmationsNeeded(it)) to minConfirmations)
        }
        wallet.unconfirmed.forEach {
            UtxoRow(it, null to minConfirmations)
        }
    }
}
