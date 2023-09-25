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

package fr.acinq.phoenix.android.payments

import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Either
import fr.acinq.phoenix.android.utils.BitmapHelper
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


class ReceiveViewModel(
    val peerManager: PeerManager
): ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)

    sealed class LightningInvoiceState {
        object Init : LightningInvoiceState()
        object Generating : LightningInvoiceState()
        data class Show(val paymentRequest: PaymentRequest) : LightningInvoiceState()
        data class Error(val e: Throwable) : LightningInvoiceState()
    }

    sealed class BitcoinAddressState {
        object Init : BitcoinAddressState()
        data class Show(val address: String, val image: ImageBitmap) : BitcoinAddressState()
        data class Error(val e: Throwable) : BitcoinAddressState()
    }

    var lightningInvoiceState by mutableStateOf<LightningInvoiceState>(LightningInvoiceState.Init)
    var bitcoinAddressState by mutableStateOf<BitcoinAddressState>(BitcoinAddressState.Init)

    /** Bitmap containing the LN invoice qr code. It is not stored in the state to avoid brutal transitions and flickering. */
    var lightningQRBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /** When true, the Lightning QR UI show the editing invoice form. Not stored in the LN invoice state, to keep a reference to the current invoice state. */
    var isEditingLightningInvoice by mutableStateOf(false)

    init {
        generateBitcoinAddress()
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
                expirySeconds = expirySeconds
            )
            lightningQRBitmap = BitmapHelper.generateBitmap(pr.write()).asImageBitmap()
            log.debug("generated new invoice=${pr.write()}")
            lightningInvoiceState = LightningInvoiceState.Show(pr)
        }
    }

    @UiThread
    private fun generateBitcoinAddress() {
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to generate address :", e)
            bitcoinAddressState = BitcoinAddressState.Error(e)
        }) {
            val address = peerManager.getPeer().swapInAddress
            val image = BitmapHelper.generateBitmap(address).asImageBitmap()
            bitcoinAddressState = BitcoinAddressState.Show(address, image)
        }
    }

    class Factory(
        private val peerManager: PeerManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReceiveViewModel(peerManager) as T
        }
    }
}
