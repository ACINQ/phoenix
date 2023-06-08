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

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.utils.extensions.deeplyConfirmedBalance
import fr.acinq.phoenix.utils.extensions.totalConfirmedBalance
import fr.acinq.phoenix.utils.extensions.unconfirmedBalance
import fr.acinq.phoenix.utils.extensions.weaklyConfirmedBalance

@Composable
fun SwapInWalletInfo(
    onBackClick: () -> Unit,
    onViewChannelPolicyClick: () -> Unit,
) {
    val swapInWallet by business.peerManager.swapInWallet.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.walletinfo_onchain_swapin))
        SwappableBalanceView(balance = swapInWallet?.deeplyConfirmedBalance()?.toMilliSatoshi(), onViewChannelPolicyClick = onViewChannelPolicyClick)
        NotSwappableWalletView(wallet = swapInWallet)
    }
}

@Composable
private fun SwappableBalanceView(
    balance: MilliSatoshi?,
    onViewChannelPolicyClick: () -> Unit,
) {
    val context = LocalContext.current
    val btcUnit = LocalBitcoinUnit.current
    val liquidityPolicyInPrefs by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    CardHeader(text = stringResource(id = R.string.walletinfo_swappable_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        BalanceWithContent(balance = balance) {
            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            when (val policy = liquidityPolicyInPrefs) {
                is LiquidityPolicy.Disable -> {
                    Text(
                        text = stringResource(id = R.string.walletinfo_onchain_swapin_policy_disabled_details),
                        style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
                    )
                }
                is LiquidityPolicy.Auto -> {
                    Text(
                        text = annotatedStringResource(id = R.string.walletinfo_onchain_swapin_policy_auto_details, policy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true)),
                        style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
                    )
                }
                null -> {}
            }
            Spacer(modifier = Modifier.height(8.dp))
            InlineButton(
                text = stringResource(id = R.string.walletinfo_onchain_swapin_policy_view_button),
                fontSize = 14.sp,
                onClick = onViewChannelPolicyClick
            )
        }
    }
}

@Composable
private fun NotSwappableWalletView(
    wallet: WalletState.WalletWithConfirmations?
) {
    if (wallet != null && (wallet.unconfirmed.isNotEmpty() || wallet.weaklyConfirmed.isNotEmpty())) {
        CardHeader(text = stringResource(id = R.string.walletinfo_not_swappable_title))
        Card {
            val swapInConfirmations = business.peerManager.peerState.collectAsState().value?.walletParams?.swapInConfirmations
            BalanceWithContent(balance = wallet.weaklyConfirmedBalance().toMilliSatoshi())
            Text(
                text = annotatedStringResource(id = R.string.walletinfo_not_swappable_details, swapInConfirmations ?: "??"),
                style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            HSeparator()
            wallet.weaklyConfirmed.forEach {
                UtxoRow(it, 1)
            }
            wallet.unconfirmed.forEach {
                UtxoRow(it, null)
            }
        }
    }
}
