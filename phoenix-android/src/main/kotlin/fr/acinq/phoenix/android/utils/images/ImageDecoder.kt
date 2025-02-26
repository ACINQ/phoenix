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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.slf4j.LoggerFactory

object ImageDecoder {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Decode a base 64 encoded JPG or PNG image and return a [Bitmap] object. This may be used by lnurl-pay services
     * when they want to provide an image for a payment.
     */
    fun decodeBase64Image(source: String): Bitmap? = try {
        val imageBytes = Base64.decode(source, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        log.debug("source is not a valid image: {}", e.localizedMessage)
        null
    }
}