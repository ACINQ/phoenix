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

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.managers.WalletBalance
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun HomeBalance(
    modifier: Modifier = Modifier,
    balance: MilliSatoshi?,
    swapInBalance: WalletBalance,
    unconfirmedChannelsBalance: MilliSatoshi,
    onShowSwapInWallet: () -> Unit,
    onShowChannels: () -> Unit,
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
                onClick = { context, inFiat ->
                    val mode = UserPrefs.getHomeAmountDisplayMode(context).firstOrNull()
                    when {
                        inFiat && mode == HomeAmountDisplayMode.BTC -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.REDACTED)
                        mode == HomeAmountDisplayMode.BTC -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.FIAT)
                        mode == HomeAmountDisplayMode.FIAT -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.REDACTED)
                        mode == HomeAmountDisplayMode.REDACTED -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.BTC)
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
    val balance = swapInBalance.total.toMilliSatoshi() + pendingChannelsBalance
    if (balance > 0.msat) {
        val nextSwapTimeout by business.peerManager.swapInNextTimeout.collectAsState(initial = null)
        val undecidedSwapBalance = swapInBalance.unconfirmed + swapInBalance.weaklyConfirmed
        FilledButton(
            icon = when {
                nextSwapTimeout?.let { it.second < 144 } ?: false -> R.drawable.ic_alert_triangle
                undecidedSwapBalance == 0.sat && pendingChannelsBalance == 0.msat -> R.drawable.ic_sleep
                else -> R.drawable.ic_clock
            },
            iconTint = MaterialTheme.typography.caption.color,
            text = if (balanceDisplayMode == HomeAmountDisplayMode.REDACTED) "****" else {
                stringResource(id = R.string.home__onchain_incoming, balance.toPrettyString(preferredAmountUnit, fiatRate, withUnit = true))
            },
            textStyle = MaterialTheme.typography.caption,
            space = 4.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            backgroundColor = Color.Transparent,
            onClick = onShowSwapInWallet,
        )
    }
}
