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
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.android.utils.mutedTextColor
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
    val nodeParams by business.nodeParamsManager.nodeParams.collectAsState()
    var showLegacyNodeId by remember { mutableStateOf(false) }

    CardHeader(text = stringResource(id = R.string.walletinfo_lightning))
    Spacer(Modifier.height(8.dp))
    Card(externalPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
        LightningNodeIdView(nodeId = nodeParams?.nodeId?.toString(), onLightningWalletClick)
        val keyManager by business.walletManager.keyManager.collectAsState()
        if (showLegacyNodeId) {
            keyManager?.let {
                SettingWithCopy(title = stringResource(id = R.string.walletinfo_legacy_nodeid), value = it.nodeKeys.legacyNodeKey.publicKey.toHex())
            }
        }
    }

    if (!showLegacyNodeId) {
        Button(
            text = stringResource(id = R.string.walletinfo_legacy_nodeid_toggle),
            icon = R.drawable.ic_chevron_down,
            onClick = { showLegacyNodeId = true },
            modifier = Modifier.padding(horizontal = 32.dp),
            shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
            backgroundColor = MaterialTheme.colors.surface,
            textStyle = MaterialTheme.typography.subtitle2,
            space = 6.dp,
            padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
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
    if (nodeId == null) {
        ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
    } else {
        Clickable(onClick = onClick) {
            SettingWithCopy(
                title = stringResource(id = R.string.walletinfo_nodeid),
                value = nodeId,
                maxLinesValue = 2
            )
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
    val txUrl = txUrl(txId = utxo.outPoint.txid)
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
            text = utxo.outPoint.txid.toString(),
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
