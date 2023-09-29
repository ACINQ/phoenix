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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.finalOnChainWalletPath

@Composable
fun WalletInfoView(
    onBackClick: () -> Unit,
    onLightningWalletClick: () -> Unit,
    onSwapInWalletClick: () -> Unit,
    onFinalWalletClick: () -> Unit,
) {
    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.walletinfo_title))
        OffChainWalletView(onLightningWalletClick)
        SwapInWalletView(onSwapInWalletClick)
        FinalWalletView(onFinalWalletClick)
    }
}

@Composable
private fun OffChainWalletView(onLightningWalletClick: () -> Unit) {
    val context = LocalContext.current
    val nodeParams by business.nodeParamsManager.nodeParams.collectAsState()
    CardHeader(text = stringResource(id = R.string.walletinfo_lightning))
    Card {
        LightningNodeIdView(nodeId = nodeParams?.nodeId?.toString(), onLightningWalletClick)

        val isDataMigrationExpected by LegacyPrefsDatastore.getDataMigrationExpected(context).collectAsState(initial = null)
        if (isDataMigrationExpected != null) {
            val keyManager by business.walletManager.keyManager.collectAsState()
            keyManager?.let {
                SettingWithCopy(title = stringResource(id = R.string.walletinfo_legacy_nodeid), value = it.nodeKeys.legacyNodeKey.publicKey.toHex())
            }
        }
    }
}

@Composable
private fun SwapInWalletView(onSwapInWalletClick: () -> Unit) {
    val swapInWallet by business.peerManager.swapInWallet.collectAsState()
    val keyManager by business.walletManager.keyManager.collectAsState()

    CardHeaderWithHelp(
        text = stringResource(id = R.string.walletinfo_onchain_swapin),
        helpMessage = stringResource(id = R.string.walletinfo_onchain_swapin_help),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSwapInWalletClick,
    ) {
        swapInWallet?.let { wallet ->
            OnchainBalanceView(confirmed = (wallet.deeplyConfirmed + wallet.lockedUntilRefund + wallet.readyForRefund).balance, unconfirmed = wallet.unconfirmed.balance + wallet.weaklyConfirmed.balance)
        } ?: ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
        keyManager?.let {
            HSeparator(modifier = Modifier.padding(start = 16.dp), width = 50.dp)
            SettingWithCopy(
                title = stringResource(id = R.string.walletinfo_descriptor),
                value = it.swapInOnChainWallet.descriptor,
                maxLinesValue = 2
            )
        }
    }
}

@Composable
private fun FinalWalletView(onFinalWalletClick: () -> Unit) {
    val finalWallet by business.peerManager.finalWallet.collectAsState()
    val keyManager by business.walletManager.keyManager.collectAsState()

    CardHeaderWithHelp(
        text = stringResource(id = R.string.walletinfo_onchain_final),
        helpMessage = stringResource(id = R.string.walletinfo_onchain_final_about)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onFinalWalletClick,
    ) {
        finalWallet?.let { wallet ->
            OnchainBalanceView(confirmed = wallet.deeplyConfirmed.balance, unconfirmed = wallet.unconfirmed.balance)
        } ?: ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
        keyManager?.let {
            HSeparator(modifier = Modifier.padding(start = 16.dp), width = 50.dp)
            SettingWithCopy(
                title = stringResource(id = R.string.walletinfo_xpub),
                titleMuted = stringResource(id = R.string.walletinfo_path, it.finalOnChainWalletPath),
                value = it.finalOnChainWallet.xpub,
                maxLinesValue = 2,
            )
        }
    }
}

@Composable
private fun LightningNodeIdView(
    nodeId: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    if (nodeId == null) {
        ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
    } else {
        Clickable(onClick = onClick) {
            Row {
                Column(modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                    .weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.walletinfo_nodeid),
                        style = MaterialTheme.typography.body2,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = nodeId, style = MaterialTheme.typography.subtitle2)
                }
                Button(
                    icon = R.drawable.ic_copy,
                    onClick = { copyToClipboard(context, nodeId, context.getString(R.string.walletinfo_nodeid)) }
                )
            }
        }
    }
}

@Composable
private fun OnchainBalanceView(
    confirmed: Satoshi?,
    unconfirmed: Satoshi?,
) {
    val btcUnit = LocalBitcoinUnit.current
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        when (confirmed) {
            null -> Text(text = stringResource(id = R.string.walletinfo_loading_data), color = mutedTextColor)
            else -> {
                AmountView(amount = confirmed.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.h4, forceUnit = btcUnit, modifier = Modifier.alignByBaseline(), onClick = null)
                unconfirmed.takeUnless { it == 0.sat }?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.walletinfo_incoming_balance, it.toPrettyString(btcUnit, withUnit = true)),
                        style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

@Composable
fun UtxoRow(utxo: WalletState.Utxo, progress: Pair<Int?, Int>?) {
    val context = LocalContext.current
    val txUrl = txUrl(txId = utxo.outPoint.txid.toHex())
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = stringResource(id = R.string.accessibility_explorer_link)) {
                openLink(context, link = txUrl)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (progress != null) {
            Text(
                text = "${progress.first ?: "0"}/${progress.second}",
                style = monoTypo.copy(color = if (progress.first == null) mutedTextColor else MaterialTheme.colors.primary),
            )
        } else {
            PhoenixIcon(
                resourceId = R.drawable.ic_clock,
                tint = MaterialTheme.colors.primary,
            )
        }
        Text(
            text = utxo.outPoint.txid.toHex(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp
        )
        AmountView(amount = utxo.amount.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp), unitTextStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp), prefix = "+")
    }
}

@Composable
internal fun BalanceRow(balance: MilliSatoshi?) {
    val btcUnit = LocalBitcoinUnit.current
    if (balance == null) {
        ProgressView(text = stringResource(id = R.string.walletinfo_loading_data), padding = PaddingValues(0.dp))
    } else {
        Row {
            AmountView(amount = balance, amountTextStyle = MaterialTheme.typography.h4, forceUnit = btcUnit, modifier = Modifier.alignByBaseline(), onClick = null)
            Spacer(modifier = Modifier.width(4.dp))
            AmountInFiatView(amount = balance, modifier = Modifier.alignByBaseline())
        }
    }
}
