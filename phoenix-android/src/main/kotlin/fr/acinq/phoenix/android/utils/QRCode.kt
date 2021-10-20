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

package fr.acinq.phoenix.android.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.util.*

/**
 * Created by DPA on 19/06/19.
 */
object QRCode {

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
    return Bitmap.createScaledBitmap(Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.RGB_565), 200, 200, false)
  }
}
