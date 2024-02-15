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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.utils.BlockchainExplorer


@Composable
fun SwapInAddresses(
    onBackClick: () -> Unit,
) {
    val vm = viewModel<SwapInAddressesViewModel>(factory = SwapInAddressesViewModel.Factory(business.peerManager))

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.swapinaddresses_title),
        )

        val addresses = vm.taprootAddresses
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                CardHeader(text = stringResource(id = R.string.swapinaddresses_taproot))
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (addresses.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ProgressView(text = stringResource(id = R.string.swapinaddresses_sync), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            } else {
                itemsIndexed(addresses) { index, (address, state) ->
                    ItemCard(index = index, maxItemsCount = addresses.size) {
                        AddressStateView(address = address, state = state)
                    }
                }
            }
            vm.legacyAddress.value?.let { (address, state) ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CardHeader(text = stringResource(id = R.string.swapinaddresses_legacy))
                }
                item {
                    Card {
                        AddressStateView(address = address, state = state)
                    }
                }
            }
        }
    }
}


@Composable
private fun AddressStateView(
    address: String,
    state: WalletState.Companion.AddressState,
) {
    val context = LocalContext.current
    val link = business.blockchainExplorer.addressUrl(addr = address, website = BlockchainExplorer.Website.MempoolSpace)
    Clickable(onClick = { openLink(context, link) }) {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            val meta = state.meta
            if (meta is WalletState.Companion.AddressMeta.Derived) {
                Text(
                    text = meta.index.toString(),
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.End,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = address, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, address, "Copy address") },
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

}