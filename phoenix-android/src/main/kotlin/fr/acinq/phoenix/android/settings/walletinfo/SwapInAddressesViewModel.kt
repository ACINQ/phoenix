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
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.phoenixSwapInWallet
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory



class SwapInAddressesViewModel(private val peerManager: PeerManager) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)

    val taprootAddresses = mutableStateListOf<TaprootAddress>()
    val legacyAddress = mutableStateOf<Pair<String, WalletState.AddressState>?>(null)

    init {
        monitorSwapAddresses()
    }

    @UiThread
    private fun monitorSwapAddresses() {
        viewModelScope.launch {
            peerManager.getPeer().phoenixSwapInWallet.wallet.walletStateFlow.collect { walletState ->
                val currentTaprootAddress = walletState.firstUnusedDerivedAddress
                val addresses = walletState.addresses.toList()
                val legacy = addresses.firstOrNull { it.second.meta is WalletState.AddressMeta.Single }
                val taprootList = addresses.filter { it.second.meta is WalletState.AddressMeta.Derived }
                    .sortedByDescending { it.second.meta.indexOrNull }
                    .map {
                        TaprootAddress(address = it.first, state = it.second, isCurrent = it.first == currentTaprootAddress?.first)
                    }
                log.info("swap-in taproot addresses update: ${taprootAddresses.size} -> ${taprootList.size}")
                taprootAddresses.clear()
                taprootAddresses.addAll(taprootList)
                legacyAddress.value = legacy
            }
        }
    }

    data class TaprootAddress(val address: String, val state: WalletState.AddressState, val isCurrent: Boolean)

    class Factory(
        private val peerManager: PeerManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return SwapInAddressesViewModel(peerManager) as T
        }
    }
}