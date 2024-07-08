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

package fr.acinq.phoenix.android.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.Setting
import fr.acinq.phoenix.android.components.SettingInteractive
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory


sealed class ClaimAddressState {
    data object Init : ClaimAddressState()
    data object None : ClaimAddressState()
    data object Claiming : ClaimAddressState()
    data class Done(val address: String) : ClaimAddressState()
    data class Failure(val e: Throwable) : ClaimAddressState()
}

class ExperimentalViewModel(val peerManager: PeerManager, val internalDataRepository: InternalDataRepository) : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)

    var claimAddressState by mutableStateOf<ClaimAddressState>(ClaimAddressState.Init)
        private set

    init {
        viewModelScope.launch {
            val address = internalDataRepository.getBip353Address.first()
            if (address.isBlank()) {
                claimAddressState = ClaimAddressState.None
            } else {
                claimAddressState = ClaimAddressState.Done(address)
            }
        }
    }

    fun claimAddress() {
        if (claimAddressState == ClaimAddressState.Claiming || claimAddressState == ClaimAddressState.Init) return
        claimAddressState = ClaimAddressState.Claiming
        viewModelScope.launch {
            log.info("claiming bip-353 address")
            try {
                withTimeout(10_000) {
                    val address = peerManager.getPeer().requestAddress(languageSubtag = "en")
                    internalDataRepository.saveBip353Address(address)
                    log.info("bip-353 address successfully claimed!")
                    claimAddressState = ClaimAddressState.Done(address)
                }
            } catch (e: Exception) {
                log.error("failed to claim address: ", e)
                claimAddressState = ClaimAddressState.Failure(e)
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
            return ExperimentalViewModel(peerManager, application.internalDataRepository) as T
        }
    }
}

@Composable
fun ExperimentalView(
    onBackClick: () -> Unit,
) {
    val vm = viewModel<ExperimentalViewModel>(factory = ExperimentalViewModel.Factory(business.peerManager))

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = "Experimental features"
        )

        CardHeader(text = "Bip-353 address")
        Card(modifier = Modifier.fillMaxWidth()) {
            ClaimAddressButton(vm.claimAddressState, onClaim = { vm.claimAddress() })
        }
    }
}

@Composable
private fun ClaimAddressButton(
    state: ClaimAddressState,
    onClaim: () -> Unit,
) {
    when (state) {
        is ClaimAddressState.Init -> {
            Setting(
                title = "Checking address",
                description = "Lets you share your Bolt12 code with a human-readable address, using a DNS look-up",
            )
        }
        is ClaimAddressState.None -> {
            SettingInteractive(
                title = "Claim my Bip-353 address",
                description = "Lets you share your Bolt12 code with a human-readable address, using a DNS look-up",
                onClick = onClaim,
            )
        }
        is ClaimAddressState.Claiming -> {
            Setting(
                title = "Claiming address...",
                description = "Lets you share your Bolt12 code with a human-readable address, using a DNS look-up",
            )
        }
        is ClaimAddressState.Done -> {
            Setting(
                title = state.address,
                description = "Lets you share your Bolt12 code with a human-readable address, using a DNS look-up",
            )
        }
        is ClaimAddressState.Failure -> {
            Setting(
                title = "Failure! ${state.e.localizedMessage}",
                description = "Lets you share your Bolt12 code with a human-readable address, using a DNS look-up",
            )
        }
    }
}
