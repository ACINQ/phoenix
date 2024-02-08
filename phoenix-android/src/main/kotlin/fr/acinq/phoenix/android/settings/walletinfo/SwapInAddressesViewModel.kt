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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.annotation.UiThread
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


class SwapInAddressesViewModel(private val peerManager: PeerManager) : ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)

    val taprootAddresses = mutableStateListOf<Pair<String, WalletState.Companion.AddressState>>()
    val legacyAddress = mutableStateOf<Pair<String, WalletState.Companion.AddressState>?>(null)

    init {
        monitorSwapAddresses()
    }

    @UiThread
    private fun monitorSwapAddresses() {
        viewModelScope.launch {
            peerManager.getPeer().swapInWallet.wallet.walletStateFlow.collect { walletState ->
                val newAddresses = walletState.addresses.toList().sortedByDescending {
                    val meta = it.second.meta
                    if (meta is WalletState.Companion.AddressMeta.Derived) {
                        meta.index
                    } else {
                        -1 // legacy address goes to the bottom
                    }
                }
                val (legacy, taprootList) = newAddresses.last() to newAddresses.dropLast(1)
                log.info("swap-in taproot addresses update: ${taprootAddresses.size} -> ${taprootList.size}")
                taprootAddresses.clear()
                taprootAddresses.addAll(taprootList)
                legacyAddress.value = legacy
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return SwapInAddressesViewModel(peerManager) as T
        }
    }
}