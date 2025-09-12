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

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.layouts.ItemCard
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.buttons.addressUrl
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.components.buttons.openLink
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.darken
import fr.acinq.phoenix.android.utils.monoTypo


@Composable
fun SwapInAddresses(
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
) {
    val vm = viewModel<SwapInAddressesViewModel>(factory = SwapInAddressesViewModel.Factory(business.peerManager))

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.swapinaddresses_title),
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                CardHeader(text = stringResource(id = R.string.swapinaddresses_taproot))
                Spacer(modifier = Modifier.height(8.dp))
            }

            val taprootAddresses = vm.taprootAddresses
            if (taprootAddresses.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ProgressView(text = stringResource(id = R.string.swapinaddresses_sync), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            } else {
                itemsIndexed(taprootAddresses) { index, (address, state, isCurrent) ->
                    ItemCard(index = index, maxItemsCount = taprootAddresses.size) {
                        AddressStateView(address = address, state = state, isCurrent = isCurrent)
                    }
                }
            }

            val legacyAddress = vm.legacyAddress.value
            legacyAddress?.let { (address, state) ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CardHeader(text = stringResource(id = R.string.swapinaddresses_legacy))
                }
                item {
                    Card {
                        AddressStateView(address = address, state = state, isCurrent = false)
                    }
                }
            }
        }
    }
}


@Composable
private fun AddressStateView(
    address: String,
    state: WalletState.AddressState,
    isCurrent: Boolean,
) {
    val context = LocalContext.current
    val link = addressUrl(address = address)
    Clickable(onClick = { link?.let { openLink(context, link) } }) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val meta = state.meta
            if (isCurrent || state.alreadyUsed) {
                Surface(shape = RectangleShape, color = if (isCurrent) MaterialTheme.colors.primary else borderColor.darken(.98f), modifier = Modifier.width(3.dp).fillMaxHeight(0.9f)) { }
                Spacer(Modifier.width(13.dp))
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            if (meta is WalletState.AddressMeta.Derived) {
                Text(
                    text = meta.index.toString(),
                    style = monoTypo.copy(color = if (isCurrent) MaterialTheme.typography.body1.color else MaterialTheme.typography.caption.color, fontSize = 14.sp),
                    textAlign = TextAlign.End,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = address, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                style = monoTypo.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp))
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, address, "Copy address") },
                modifier = Modifier.fillMaxHeight(),
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}