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
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.managers.finalWalletPath
import fr.acinq.phoenix.managers.finalWalletXpub
import fr.acinq.phoenix.managers.swapInWalletPath
import fr.acinq.phoenix.managers.swapInWalletXpub

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
    CardHeader(text = stringResource(id = R.string.walletinfo_lightning))
    Card {
        LightningNodeIdView(nodeId = nodeParams?.nodeId?.toString(), onLightningWalletClick)
//        InlineButton(
//            text = "View channels",
//            icon = R.drawable.ic_arrow_next,
//            fontSize = 12.sp,
//            iconSize = 14.dp,
//            onClick = onLightningWalletClick,
//            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
//        )
    }
}

@Composable
private fun SwapInWalletView(onSwapInWalletClick: () -> Unit) {
    val swapInWallet by business.balanceManager.swapInWallet.collectAsState()
    val keyManager by business.walletManager.keyManager.collectAsState()

    CardHeaderWithHelp(
        text = stringResource(id = R.string.walletinfo_onchain_swapin),
        helpMessage = stringResource(id = R.string.walletinfo_onchain_swapin_about)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSwapInWalletClick,
    ) {
        swapInWallet?.let { wallet ->
            OnchainBalanceView(confirmed = wallet.confirmedBalance, unconfirmed = wallet.unconfirmedBalance)
        } ?: ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
        keyManager?.let {
            HSeparator(modifier = Modifier.padding(start = 16.dp), width = 50.dp)
            XpubView(xpub = it.swapInWalletXpub(), path = it.swapInWalletPath())
        }
    }
}

@Composable
private fun FinalWalletView(onFinalWalletClick: () -> Unit) {
    val finalWallet by business.balanceManager.finalWallet.collectAsState()
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
            OnchainBalanceView(confirmed = wallet.confirmedBalance, unconfirmed = wallet.unconfirmedBalance)
        } ?: ProgressView(text = stringResource(id = R.string.walletinfo_loading_data))
        keyManager?.let {
            HSeparator(modifier = Modifier.padding(start = 16.dp), width = 50.dp)
            XpubView(xpub = it.finalWalletXpub(), path = it.finalWalletPath())
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
                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp).weight(1f)) {
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
                        text = "+ ${it.toPrettyString(btcUnit, withUnit = true)} incoming",
                        style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

@Composable
private fun XpubView(xpub: String, path: String) {
    val context = LocalContext.current
    Row {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp).weight(1f)) {
            Row {
                Text(
                    text = stringResource(id = R.string.walletinfo_xpub),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.walletinfo_path, path),
                    style = MaterialTheme.typography.subtitle2.copy(fontSize = 12.sp),
                    modifier = Modifier
                        .alignByBaseline(),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = xpub, style = MaterialTheme.typography.subtitle2)
        }
        Button(
            icon = R.drawable.ic_copy,
            onClick = { copyToClipboard(context, xpub, context.getString(R.string.walletinfo_xpub)) }
        )
    }
}

@Composable
fun UnconfirmedWalletView(
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

@Composable
private fun UtxoRow(utxo: WalletState.Utxo, isConfirmed: Boolean) {
    val context = LocalContext.current
    val txUrl = txUrl(txId = utxo.outPoint.txid.toHex())
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = "open link in explorer") {
                openLink(context, link = txUrl)
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PhoenixIcon(
            resourceId = if (isConfirmed) { R.drawable.ic_chain } else { R.drawable.ic_clock },
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = utxo.outPoint.txid.toHex(),
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AmountView(amount = utxo.amount.toMilliSatoshi(), modifier = Modifier.alignByBaseline())
    }
}

@Composable
internal fun BalanceWithContent(
    balance: MilliSatoshi?,
    content: @Composable () -> Unit = {},
) {
    val btcUnit = LocalBitcoinUnit.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (balance == null) {
            ProgressView(text = stringResource(id = R.string.walletinfo_loading_data), padding = PaddingValues(0.dp))
        } else {
            Row {
                AmountView(amount = balance, amountTextStyle = MaterialTheme.typography.h4, forceUnit = btcUnit, modifier = Modifier.alignByBaseline(), onClick = null)
                Spacer(modifier = Modifier.width(4.dp))
                AmountInFiatView(amount = balance, modifier = Modifier.alignByBaseline())
            }
        }
        content()
    }
}
