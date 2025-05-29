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

package fr.acinq.phoenix.android.utils.nfc

import android.nfc.NdefRecord
import org.slf4j.LoggerFactory

object NdefParser {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun parseNdefRecord(record: NdefRecord): String? {
        val tnf = record.tnf
        val type = record.type
        val payload = record.payload
        log.info("parsing ndef record tnf=$tnf type=$type payload=${payload.decodeToString()}")

        return try {
            when (tnf) {
                NdefRecord.TNF_WELL_KNOWN -> {
                    if (type.contentEquals(NdefRecord.RTD_TEXT)) {
                        parseTextRecord(payload)
                    } else if (type.contentEquals(NdefRecord.RTD_URI)) {
                        parseUriRecord(payload)
                    } else {
                        log.debug("unhandled well-known record with type={}", type)
                        null
                    }
                }
                NdefRecord.TNF_ABSOLUTE_URI -> {
                    parseAbsoluteUriRecord(payload)
                }
                else -> {
                    log.debug("unhandled tnf={}", tnf)
                    null
                }
            }
        } catch (e: Exception) {
            log.warn("failed to parse ndef record: ${e.message}")
            null
        }
    }

    private fun parseTextRecord(payload: ByteArray): String {
        val textEncoding = if (((payload[0].toInt() and 0x80) == 0)) "UTF-8" else "UTF-16"
        val languageCodeLength = payload[0].toInt() and 0x3F
        return String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
    }

    private fun parseUriRecord(payload: ByteArray): String {
        // URI records have a prefix byte followed by the URI
        return String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    private fun parseAbsoluteUriRecord(payload: ByteArray): String {
        return String(payload, Charsets.UTF_8)
    }
}