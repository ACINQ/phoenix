/*
 * Copyright 2019 ACINQ SAS
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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import fr.acinq.lightning.utils.currentTimestampMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.*


object QRCodeHelper {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** Create a Bitmap QR code from a String. */
    fun generateBitmap(source: String): Bitmap {
        val hintsMap = HashMap<EncodeHintType, Any>()
        hintsMap[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
        hintsMap[EncodeHintType.MARGIN] = 0
        val qrCode = Encoder.encode(source, ErrorCorrectionLevel.L, hintsMap)
        val width = qrCode.matrix.width
        val height = qrCode.matrix.height
        val rgbArray = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                rgbArray[i] = if (qrCode.matrix.get(x, y) > 0) Color.BLACK else Color.WHITE
                i++
            }
        }
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.RGB_565), 260, 260, false)
    }

    /** Saves a bitmap to the device's [MediaStore.Images] after adding white padding around it. */
    suspend fun saveQRToGallery(context: Context, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val imageName = "phoenix_${currentTimestampMillis()}.jpg"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_ADDED, currentTimestampMillis() / 1000)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val imageUri = contentResolver.insert(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }, values)
                    ?: return@withContext null

                val padding = 15
                val paddedBitmap = {
                    val outputimage = Bitmap.createBitmap(bitmap.getWidth() + padding * 2, bitmap.getHeight() + padding * 2, Bitmap.Config.RGB_565)
                    val canvas = Canvas(outputimage)
                    canvas.drawARGB(0xff, 0xff, 0xff, 0xff)
                    canvas.drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)
                    outputimage
                }

                contentResolver.openOutputStream(imageUri)?.use {
                    paddedBitmap.invoke().compress(Bitmap.CompressFormat.JPEG, 90, it)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(imageUri, values, null, null)
                }
                return@withContext imageUri
            } catch (e: Exception) {
                log.error("failed to save image to disk: ", e)
                return@withContext null
            }
        }
    }
}
