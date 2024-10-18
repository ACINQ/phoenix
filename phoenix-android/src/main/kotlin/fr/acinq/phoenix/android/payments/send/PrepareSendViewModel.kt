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

package fr.acinq.phoenix.android.payments.send

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.managers.SendManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class ReadImageState {
    data object Ready: ReadImageState()
    data object Reading: ReadImageState()
    data object NotFound: ReadImageState()
    data object Error: ReadImageState()

    val canProcess by lazy { this is Ready || this is Error }
}

sealed class ParsePaymentState {
    data object Ready : ParsePaymentState()

    sealed class Processing : ParsePaymentState()
    data object Parsing : Processing()
    data object ResolvingLnurl : Processing()
    data object ResolvingBip353 : Processing()

    sealed class ChoosePaymentMode: ParsePaymentState()
    data class ChooseOnchainOrBolt11(val request: String, val uri: BitcoinUri, val bolt11: Bolt11Invoice) : ChoosePaymentMode()
    data class ChooseOnchainOrOffer(val request: String, val uri: BitcoinUri, val offer: OfferTypes.Offer) : ChoosePaymentMode()

    data class Success(val data: SendManager.ParseResult.Success) : ParsePaymentState()

    sealed class Error : ParsePaymentState()
    data class ParsingFailure(val error: SendManager.ParseResult.BadRequest) : Error()
    data class GenericError(val cause: Throwable): Error() {
        val errorMessage: String by lazy { cause.localizedMessage ?: cause::class.java.name }
    }

    val isProcessing by lazy { this is Processing }

    val hasFailed by lazy { this is Error }
}

class PrepareSendViewModel(val sendManager: SendManager) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    var readImageState by mutableStateOf<ReadImageState>(ReadImageState.Ready)

    var parsePaymentState by mutableStateOf<ParsePaymentState>(ParsePaymentState.Ready)

    fun readImage(context: Context, uri: Uri, onDataFound: (String) -> Unit) {
        if (!(readImageState is ReadImageState.Ready || readImageState is ReadImageState.Error)) return
        readImageState = ReadImageState.Reading

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.info("failed to load file or read QR code for file=$uri: ", e)
            readImageState = when (e) {
                is com.google.zxing.NotFoundException -> ReadImageState.NotFound
                else -> ReadImageState.Error
            }
        }) {
            val bitmap = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
            }
            if (bitmap == null) {
                readImageState = ReadImageState.NotFound
                return@launch
            } else {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
                val result = QRCodeMultiReader().decodeMultiple(binaryBitmap)
                if (result.isEmpty()) {
                    log.debug("could not find any QR code for file={}", uri)
                    readImageState = ReadImageState.NotFound
                } else {
                    val data = result.first().text
                    if (data.isNotBlank()) {
                        log.debug("decoded data={} for file={}", data, uri)
                        delay(400)
                        readImageState = ReadImageState.Ready
                        onDataFound(data)
                    } else {
                        readImageState = ReadImageState.NotFound
                    }
                }
            }
        }
    }

    fun parsePaymentData(input: String) {
        if (parsePaymentState.isProcessing) return
        parsePaymentState = ParsePaymentState.Parsing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when parsing payment data: ${e.message}")
            parsePaymentState = ParsePaymentState.GenericError(e)
        }) {
            val result = sendManager.parse(
                request = input,
                progress = {
                    parsePaymentState = when (it) {
                        is SendManager.ParseProgress.ResolvingBip353 -> ParsePaymentState.ResolvingBip353
                        is SendManager.ParseProgress.LnurlServiceFetch -> ParsePaymentState.ResolvingLnurl
                    }
                }
            )

            parsePaymentState = when (result) {
                is SendManager.ParseResult.BadRequest -> ParsePaymentState.ParsingFailure(result)
                is SendManager.ParseResult.Success -> {
                    log.info("successfully parsed $result from input=$input")
                    when (result) {
                        is SendManager.ParseResult.Uri -> {
                            val bolt11 = result.uri.paymentRequest
                            val offer = result.uri.offer
                            when {
                                bolt11 != null -> ParsePaymentState.ChooseOnchainOrBolt11(request = input, uri = result.uri.copy(paymentRequest = null, offer = null), bolt11 = bolt11)
                                offer != null -> ParsePaymentState.ChooseOnchainOrOffer(request = input, uri = result.uri.copy(paymentRequest = null, offer = null), offer = offer)
                                else -> ParsePaymentState.Success(result)
                            }
                        }
                        else -> ParsePaymentState.Success(result)
                    }
                }
            }
        }
    }

    fun resetParsing() {
        if (parsePaymentState !is ParsePaymentState.Processing) parsePaymentState = ParsePaymentState.Ready
    }

    class Factory(
        private val sendManager: SendManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PrepareSendViewModel(sendManager) as T
        }
    }
}
