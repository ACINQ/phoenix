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
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.SwapAddressFormat
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.android.utils.images.QRCodeHelper
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
    data class Generating(val amount: MilliSatoshi?, val description: String?, val expirySeconds: Long?, val makeReusable: Boolean) : LightningInvoiceState()
    sealed class Done : LightningInvoiceState() {
        abstract val paymentData: String
        abstract val amount: MilliSatoshi?
        abstract val description: String?
        data class Bolt11(val invoice: Bolt11Invoice, val expirySeconds: Long?) : Done() {
            override val paymentData by lazy { invoice.write() }
            override val description: String? by lazy { invoice.description }
            override val amount: MilliSatoshi? by lazy { invoice.amount }
        }
        data class Bolt12(val offer: OfferTypes.Offer) : Done() {
            override val paymentData by lazy { offer.encode() }
            override val description: String? by lazy { offer.description }
            override val amount: MilliSatoshi? by lazy { offer.amount }
        }
    }
    data class Error(val e: Throwable) : LightningInvoiceState()
}

data class BitcoinAddressState(val currentIndex: Int, val currentAddress: String, val image: ImageBitmap)

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
    var bitcoinAddressState by mutableStateOf<BitcoinAddressState?>(null)

    /** Bitmap containing the LN invoice qr code. It is not stored in the state to avoid brutal transitions and flickering. */
    var lightningQRBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    init {
        monitorCurrentSwapAddress()
    }

    @UiThread
    fun generateInvoice(
        amount: MilliSatoshi?,
        description: String,
        expirySeconds: Long,
        isReusable: Boolean,
    ) {
        if (lightningInvoiceState is LightningInvoiceState.Generating) return

        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to generate invoice :", e)
            lightningInvoiceState = LightningInvoiceState.Error(e)
        }) {
            lightningInvoiceState = LightningInvoiceState.Generating(amount, description, expirySeconds, isReusable)
            log.info("generating new (${if (isReusable) "bolt12" else "bolt11"}) invoice with amount=$amount desc=$description expirySec=$expirySeconds")

            if (isReusable) {
                val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
                val bolt12Offer = nodeParams.randomOffer(trampolineNodeId = NodeParamsManager.trampolineNodeId, amount = amount, description = description).first
                lightningQRBitmap = QRCodeHelper.generateBitmap(bolt12Offer.encode()).asImageBitmap()
                log.debug("generated new bolt12 offer=${bolt12Offer.encode()}")
                lightningInvoiceState = LightningInvoiceState.Done.Bolt12(bolt12Offer)
            } else {
                val bolt11Invoice = peerManager.getPeer().createInvoice(
                    paymentPreimage = Lightning.randomBytes32(),
                    amount = amount,
                    description = Either.Left(description),
                    expiry = expirySeconds.seconds
                )
                lightningQRBitmap = QRCodeHelper.generateBitmap(bolt11Invoice.write()).asImageBitmap()
                log.debug("generated new bolt11 invoice=${bolt11Invoice.write()}")
                lightningInvoiceState = LightningInvoiceState.Done.Bolt11(bolt11Invoice, expirySeconds)
            }
        }
    }

    @UiThread
    private fun monitorCurrentSwapAddress() {
        viewModelScope.launch {
            val swapAddressFormat = userPrefs.getSwapAddressFormat.first()
            if (swapAddressFormat == SwapAddressFormat.LEGACY) {
                val legacySwapInAddress = peerManager.getPeer().phoenixSwapInWallet.legacySwapInAddress
                val image = QRCodeHelper.generateBitmap(legacySwapInAddress).asImageBitmap()
                bitcoinAddressState = BitcoinAddressState(0, legacySwapInAddress, image)
            } else {
                // immediately set an address using the index saved in settings, so that the user does not have to wait for the wallet to synchronise
                val keyManager = walletManager.keyManager.filterNotNull().first()
                val startIndex = internalDataRepository.getLastUsedSwapIndex.first()
                val startAddress = keyManager.swapInOnChainWallet.getSwapInProtocol(startIndex).address(chain)
                val image = QRCodeHelper.generateBitmap(startAddress).asImageBitmap()
                bitcoinAddressState = BitcoinAddressState(startIndex, startAddress, image)

                // monitor the actual address from the swap-in wallet -- might take some time since the wallet must check all previous addresses
                peerManager.getPeer().phoenixSwapInWallet.swapInAddressFlow.filterNotNull().collect { (newAddress, newIndex) ->
                    val newImage = QRCodeHelper.generateBitmap(newAddress).asImageBitmap()
                    internalDataRepository.saveLastUsedSwapIndex(newIndex)
                    bitcoinAddressState = BitcoinAddressState(newIndex, newAddress, newImage)
                }
            }
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
