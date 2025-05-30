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

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.core.content.IntentCompat
import org.slf4j.LoggerFactory

object NfcHelper {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun readNfcIntent(intent: Intent, onResultFound: (String) -> Unit) {
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        log.info("nfc tag discovered with tag=${tag}")

        val nfcMessages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.filterIsInstance<NdefMessage>()

        nfcMessages?.map { message ->
            message.records.mapNotNull {
                NdefParser.parseNdefRecord(it)
            }
        }?.flatten()?.firstOrNull()?.let {
            log.debug("found data in tag: $it")
            onResultFound(it)
        } ?: run {
            log.debug("no data found in tag")
        }
    }

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
}