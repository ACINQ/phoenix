/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.utils.images

import android.graphics.ImageFormat
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.slf4j.LoggerFactory

/** A CameraX [ImageAnalysis.Analyzer] that uses zxing to perform the analysis. */
class ZxingQrCodeAnalyzer(
    private val onQrCodesDetected: (qrCode: Result) -> Unit
) : ImageAnalysis.Analyzer {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val reader = QRCodeReader()

    override fun getDefaultTargetResolution(): Size {
        return DEFAULT_RESOLUTION
    }

    override fun analyze(image: ImageProxy) {
        if (image.format != ImageFormat.YUV_420_888) {
            log.warn("unhandled format=${image.format}")
            return
        }

        try {
            val buffer = image.planes[0].buffer.apply { rewind() }
            val rowStride: Int = image.planes[0].rowStride
            val data = ByteArray(image.width * image.height)

            for (i in 0 until image.height) {
                buffer.position(i * rowStride)
                buffer.get(data, i * image.width, image.width)
            }

            val source = PlanarYUVLuminanceSource(data, image.width, image.height, 0, 0, image.width, image.height, false)

            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap, mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.ALSO_INVERTED to true,
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "ISO-8859-1"
            ))
            onQrCodesDetected(result)
        } catch (e: NotFoundException) {
            log.trace("no QR code found...")
        } catch (e: ChecksumException) {
            log.debug("QR code detected but checksum failed: ", e)
        } catch (e: FormatException) {
            log.warn("QR code detected but content does not match expectations: ", e)
        } catch (e: Exception) {
            log.debug("error when decoding: ", e)
        }
        image.close()
    }

    companion object {
        val DEFAULT_RESOLUTION = Size(1200, 1600)
    }
}