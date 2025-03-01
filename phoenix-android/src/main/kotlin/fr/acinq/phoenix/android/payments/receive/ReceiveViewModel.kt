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

package fr.acinq.phoenix.android.payments.receive

import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.images.QRCodeHelper
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.SwapAddressFormat
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.managers.phoenixSwapInWallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

sealed class LightningInvoiceState {
    data object Init : LightningInvoiceState()
    data object Generating : LightningInvoiceState()
    data class Show(val invoice: Bolt11Invoice) : LightningInvoiceState()
    data class Error(val e: Throwable) : LightningInvoiceState()
}

sealed class BitcoinAddressState {
    data object Init : BitcoinAddressState()
    data class Show(val currentIndex: Int, val currentAddress: String, val image: ImageBitmap) : BitcoinAddressState()
    data class Error(val e: Throwable) : BitcoinAddressState()
}

sealed class OfferState {
    data object Init : OfferState()
    data class Show(val encoded: String, val bitmap: ImageBitmap) : OfferState()
}

class ReceiveViewModel(
    private val chain: Chain,
    private val peerManager: PeerManager,
    private val nodeParamsManager: NodeParamsManager,
    private val walletManager: WalletManager,
    private val internalDataRepository: InternalDataRepository,
    private val userPrefs: UserPrefsRepository
): ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    var lightningInvoiceState by mutableStateOf<LightningInvoiceState>(LightningInvoiceState.Init)
    var currentSwapAddress by mutableStateOf<BitcoinAddressState>(BitcoinAddressState.Init)
    var offerState by mutableStateOf<OfferState>(OfferState.Init)

    /** Bitmap containing the LN invoice qr code. It is not stored in the state to avoid brutal transitions and flickering. */
    var lightningQRBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /** When true, the Lightning QR UI show the editing invoice form. Not stored in the LN invoice state, to keep a reference to the current invoice state. */
    var isEditingLightningInvoice by mutableStateOf(false)

    init {
        monitorCurrentSwapAddress()
        getDeterministicOffer()
    }

    @UiThread
    fun generateInvoice(
        amount: MilliSatoshi?,
        description: String,
        expirySeconds: Long
    ) {
        isEditingLightningInvoice = false
        if (lightningInvoiceState is LightningInvoiceState.Generating) return
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to generate invoice :", e)
            lightningInvoiceState = LightningInvoiceState.Error(e)
        }) {
            lightningInvoiceState = LightningInvoiceState.Generating
            log.info("generating new invoice with amount=$amount desc=$description expirySec=$expirySeconds")
            val pr = peerManager.getPeer().createInvoice(
                paymentPreimage = Lightning.randomBytes32(),
                amount = amount,
                description = Either.Left(description),
                expiry = expirySeconds.seconds
            )
            lightningQRBitmap = QRCodeHelper.generateBitmap(pr.write()).asImageBitmap()
            log.debug("generated new invoice=${pr.write()}")
            lightningInvoiceState = LightningInvoiceState.Show(pr)
        }
    }

    @UiThread
    private fun monitorCurrentSwapAddress() {
        viewModelScope.launch {
            val swapAddressFormat = userPrefs.getSwapAddressFormat.first()
            if (swapAddressFormat == SwapAddressFormat.LEGACY) {
                val legacySwapInAddress = peerManager.getPeer().phoenixSwapInWallet.legacySwapInAddress
                val image = QRCodeHelper.generateBitmap(legacySwapInAddress).asImageBitmap()
                currentSwapAddress = BitcoinAddressState.Show(0, legacySwapInAddress, image)
            } else {
                // immediately set an address using the index saved in settings, so that the user does not have to wait for the wallet to synchronise
                val keyManager = walletManager.keyManager.filterNotNull().first()
                val startIndex = internalDataRepository.getLastUsedSwapIndex.first()
                val startAddress = keyManager.swapInOnChainWallet.getSwapInProtocol(startIndex).address(chain)
                val image = QRCodeHelper.generateBitmap(startAddress).asImageBitmap()
                currentSwapAddress = BitcoinAddressState.Show(startIndex, startAddress, image)

                // monitor the actual address from the swap-in wallet -- might take some time since the wallet must check all previous addresses
                peerManager.getPeer().phoenixSwapInWallet.swapInAddressFlow.filterNotNull().collect { (newAddress, newIndex) ->
                    val newImage = QRCodeHelper.generateBitmap(newAddress).asImageBitmap()
                    internalDataRepository.saveLastUsedSwapIndex(newIndex)
                    currentSwapAddress = BitcoinAddressState.Show(newIndex, newAddress, newImage)
                }
            }
        }
    }

    private fun getDeterministicOffer() {
        viewModelScope.launch {
            val offerData = nodeParamsManager.defaultOffer()
            val encoded = offerData.defaultOffer.encode()
            val image = QRCodeHelper.generateBitmap(encoded).asImageBitmap()
            offerState = OfferState.Show(encoded, image)
        }
    }

    class Factory(
        private val chain: Chain,
        private val peerManager: PeerManager,
        private val nodeParamsManager: NodeParamsManager,
        private val walletManager: WalletManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return ReceiveViewModel(chain, peerManager, nodeParamsManager, walletManager, application.internalDataRepository, application.userPrefs) as T
        }
    }
}
