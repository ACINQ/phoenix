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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*

@Composable
fun FinalWalletInfo(
    onBackClick: () -> Unit
) {
    val finalWallet by business.balanceManager.finalWallet.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.walletinfo_onchain_final))
        ConfirmedBalanceView(balance = finalWallet?.let { it.weaklyConfirmedBalance + it.deeplyConfirmedBalance }?.toMilliSatoshi())
        if (finalWallet?.unconfirmedBalance?.takeIf { it > 0.sat } != null) {
            UnconfirmedWalletView(wallet = finalWallet)
        }
    }
}

@Composable
private fun ConfirmedBalanceView(
    balance: MilliSatoshi?
) {
    CardHeader(text = stringResource(id = R.string.walletinfo_confirmed_title))
    Card {
        BalanceWithContent(balance = balance) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.walletinfo_onchain_final_about), style = MaterialTheme.typography.subtitle2)
        }
    }
}

@Composable
private fun UnconfirmedWalletView(
    wallet: WalletState?
) {
    CardHeader(text = stringResource(id = R.string.walletinfo_unconfirmed_title))
    Card {
        BalanceWithContent(balance = wallet?.unconfirmedBalance?.toMilliSatoshi())
        if (!wallet?.unconfirmedUtxos.isNullOrEmpty()) {
            HSeparator()
            wallet?.unconfirmedUtxos?.forEach { UtxoRow(it, false) }
        }
    }
}