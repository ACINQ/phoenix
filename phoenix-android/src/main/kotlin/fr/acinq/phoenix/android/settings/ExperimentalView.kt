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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.data.canRequestLiquidity
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.delay
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
            log.debug("claiming bip-353 address")
            try {

                withTimeout(5_000) {
                    val address = peerManager.getPeer().requestAddress(languageSubtag = "en")
                    internalDataRepository.saveBip353Address(address)
                    delay(500)
                    log.info("successfully claimed bip-353 address=$address")
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
    appViewModel: AppViewModel,
) {
    val vm = viewModel<ExperimentalViewModel>(factory = ExperimentalViewModel.Factory(business.peerManager))

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.experimental_title)
        )

        CardHeader(text = stringResource(id = R.string.bip353_header))
        Card(modifier = Modifier.fillMaxWidth()) {
            ClaimAddressButton(state = vm.claimAddressState, onClaim = { vm.claimAddress() })
        }

        // flavored component
        ManageHeadlessView(appViewModel = appViewModel)
    }
}

@Composable
private fun ClaimAddressButton(
    state: ClaimAddressState,
    onClaim: () -> Unit,
) {
    val channels by business.peerManager.channelsFlow.collectAsState()
    val canClaimAddress = channels.canRequestLiquidity()

    when (state) {
        is ClaimAddressState.Init -> {
            Setting(
                title = stringResource(id = R.string.utils_loading_data),
                description = stringResource(id = R.string.bip353_subtitle),
                icon = R.drawable.ic_arobase,
            )
        }
        is ClaimAddressState.None -> {
            Setting(
                title = stringResource(id = R.string.bip353_empty),
                leadingIcon = { PhoenixIcon(R.drawable.ic_arobase) },
                subtitle = {
                    Text(text = stringResource(id = R.string.bip353_subtitle))
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledButton(
                        text = stringResource(id = R.string.bip353_claim_button),
                        onClick = onClaim,
                        padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        enabled = canClaimAddress,
                        enabledEffect = true,
                    )
                }
            )
        }
        is ClaimAddressState.Claiming -> {
            Setting(
                title = stringResource(id = R.string.bip353_claiming),
                description = stringResource(id = R.string.bip353_subtitle),
                icon = R.drawable.ic_arobase,
            )
        }
        is ClaimAddressState.Done -> {
            val context = LocalContext.current
            Setting(
                title = state.address,
                leadingIcon = { PhoenixIcon(R.drawable.ic_arobase) },
                trailingIcon = {
                    Button(
                        icon = R.drawable.ic_copy,
                        onClick = { copyToClipboard(context, context.getString(R.string.utils_bip353_with_prefix, state.address)) }
                    )
                },
                subtitle = {
                    Text(text = stringResource(id = R.string.bip353_subtitle))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.bip353_subtitle2))
                },
            )
        }
        is ClaimAddressState.Failure -> {
            Setting(
                title = stringResource(id = R.string.bip353_error),
                icon = R.drawable.ic_cross_circle,
                subtitle = {
                    Text(text = stringResource(id = R.string.bip353_subtitle))
                }
            )
        }
    }
}
