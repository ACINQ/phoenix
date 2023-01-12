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
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.utils.BitmapHelper
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.ReceiveController
import fr.acinq.phoenix.controllers.payments.Receive
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReceiveViewModel(
    controller: ReceiveController,
    description: String,
    expiry: Long
) : MVIControllerViewModel<Receive.Model, Receive.Intent>(controller) {

    /** State of the view */
    var state by mutableStateOf<ReceiveViewState>(ReceiveViewState.Default)

    /** Bitmap containing the invoice/address qr code */
    var qrBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Custom invoice description */
    var customDesc by mutableStateOf(description)

    /** Custom invoice amount */
    var customAmount by mutableStateOf<MilliSatoshi?>(null)

    /** Custom invoice expiry */
    var customExpiry by mutableStateOf<Long>(expiry)

    @UiThread
    fun generateInvoice() {
        val amount = customAmount
        val desc = customDesc
        val expiry = customExpiry
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to generate invoice with amount=$amount desc=$desc :", e)
            state = ReceiveViewState.Error(e)
        }) {
            log.info("generating invoice with amount=$amount desc=$desc")
            state = ReceiveViewState.Default
            controller.intent(Receive.Intent.Ask(amount = amount, desc = desc, expirySeconds = expiry))
        }
    }

    @UiThread
    fun generateQrCodeBitmap(invoice: String) {
        viewModelScope.launch(Dispatchers.Default) {
            log.info("generating qrcode for invoice=$invoice")
            try {
                qrBitmap = BitmapHelper.generateBitmap(invoice).asImageBitmap()
            } catch (e: Exception) {
                log.error("error when generating bitmap QR for invoice=$invoice:", e)
            }
        }
    }

    class Factory(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> ReceiveController,
        private val description: String,
        private val expiry: Long,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReceiveViewModel(controllerFactory.getController(), description, expiry) as T
        }
    }
}