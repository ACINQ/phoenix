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

package fr.acinq.phoenix.android.utils.nfc

import android.nfc.NdefRecord

object NfcHelper {

    fun createTextRecord(text: String, id: ByteArray): NdefRecord {
        val languageBytes = "en".toByteArray(Charsets.US_ASCII)
        val langLength = languageBytes.size and 0x3F
        val textBytes = text.toByteArray(Charsets.UTF_8)

        val payload = byteArrayOf(langLength.toByte()) + languageBytes.copyOfRange(0, langLength) + textBytes

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, id, payload)
    }

    fun fillByteArrayToFixedDimension(array: ByteArray, fixedSize: Int): ByteArray =
        if (array.size >= fixedSize) {
            array.copyOfRange(0, fixedSize)
        } else {
            fillByteArrayToFixedDimension(byteArrayOf(0x00) + array, fixedSize)
        }

    fun intToByteArray(value: Int): ByteArray {
        if (value == 0) return byteArrayOf(0)
        val bytes = mutableListOf<Byte>()
        var temp = value
        while (temp != 0) {
            bytes.add(0, (temp and 0xFF).toByte()) // prepend to preserve big-endian order
            temp = temp ushr 8
        }
        return bytes.toByteArray()
    }
}