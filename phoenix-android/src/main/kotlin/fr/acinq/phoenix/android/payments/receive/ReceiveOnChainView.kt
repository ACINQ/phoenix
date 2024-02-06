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

package fr.acinq.phoenix.android.payments.receive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AddressLinkButton
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore

@Composable
fun BitcoinAddressView(
    vm: ReceiveViewModel,
    maxWidth: Dp
) {
    val context = LocalContext.current
    var showAddressHistory by remember { mutableStateOf(false) }

    InvoiceHeader(
        icon = R.drawable.ic_chain,
        helpMessage = stringResource(id = R.string.receive_bitcoin_help),
        content = { Text(text = stringResource(id = R.string.receive_bitcoin_title)) },
    )

    when (val state = vm.currentSwapAddress) {
        is BitcoinAddressState.Init -> {
            Box(contentAlignment = Alignment.Center) {
                QRCodeView(bitmap = null, data = null, maxWidth = maxWidth)
                Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = stringResource(id = R.string.receive_bitcoin_generating)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = null, maxWidth = maxWidth)
        }
        is BitcoinAddressState.Show -> {
            QRCodeView(data = state.currentAddress, bitmap = state.image, maxWidth = maxWidth)
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareAddressesButtons(
                onCopy = { copyToClipboard(context, data = state.currentAddress) },
                onShare = { share(context, "bitcoin:${state.currentAddress}", context.getString(R.string.receive_bitcoin_share_subject), context.getString(R.string.receive_bitcoin_share_title)) },
                onHistory = { showAddressHistory = true },
                maxWidth = maxWidth,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDetail(label = stringResource(id = R.string.receive_bitcoin_address_label), value = state.currentAddress)

            val isFromLegacy by LegacyPrefsDatastore.hasMigratedFromLegacy(context).collectAsState(initial = false)
            if (isFromLegacy) {
                Spacer(modifier = Modifier.height(24.dp))
                InfoMessage(
                    header = stringResource(id = R.string.receive_onchain_legacy_warning_title),
                    details = stringResource(id = R.string.receive_onchain_legacy_warning),
                    detailsStyle = MaterialTheme.typography.subtitle2,
                    alignment = Alignment.CenterHorizontally
                    )
            }
        }
        is BitcoinAddressState.Error -> {
            ErrorMessage(
                header = stringResource(id = R.string.receive_bitcoin_error),
                details = state.e.message
            )
        }
    }

    if (showAddressHistory) {
        SwapAddressesView(onDismiss = { showAddressHistory = false }, addresses = vm.swapAddresses)
    }
}

@Composable
fun CopyShareAddressesButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onHistory: () -> Unit,
    maxWidth: Dp,
) {
    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
        BorderButton(icon = R.drawable.ic_copy, onClick = onCopy)
        Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
        BorderButton(icon = R.drawable.ic_share, onClick = onShare)
        Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
        BorderButton(
            text = if (maxWidth <= 360.dp) null else "Addresses",
            icon = R.drawable.ic_history,
            onClick = onHistory
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapAddressesView(
    onDismiss: () -> Unit,
    addresses: List<Pair<String, WalletState.Companion.AddressState>>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val availableHeightDp = screenHeight - 480

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            // executed when user click outside the sheet, and after sheet has been hidden thru state.
            onDismiss()
        },
        modifier = Modifier.heightIn(min = 200.dp, max = availableHeightDp.coerceAtLeast(400).dp),
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(addresses) { (address, state) ->
                AddressStateView(address = address, state = state)
            }
            item {
                if (addresses.size == 1) {
                    val meta = addresses.first().second.meta
                    if (meta is WalletState.Companion.AddressMeta.Derived && meta.index > 0 || meta is WalletState.Companion.AddressMeta.Single) {
                        Text(
                            text = "Checking other addresses...",
                            style = MaterialTheme.typography.subtitle2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(150.dp))
            }
        }
    }
}

@Composable
private fun AddressStateView(
    address: String,
    state: WalletState.Companion.AddressState,
) {
    Clickable(onClick = {  }) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (val meta = state.meta) {
                    is WalletState.Companion.AddressMeta.Derived -> "#${meta.index}"
                    is WalletState.Companion.AddressMeta.Single -> "-"
                },
                modifier = Modifier
                    .width(20.dp)
                    .alignByBaseline(),
                style = MaterialTheme.typography.subtitle2,
            )
            AddressLinkButton(address = address, modifier = Modifier
                .weight(1f, fill = true)
                .alignByBaseline())
        }
    }

}