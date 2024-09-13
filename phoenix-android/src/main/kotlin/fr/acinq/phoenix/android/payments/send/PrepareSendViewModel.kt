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
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
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

    val canProcess = this is Ready || this is Error
}

class PrepareSendViewModel : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    var readImageState by mutableStateOf<ReadImageState>(ReadImageState.Ready)

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
}
