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

import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.android.components.nfc.NfcState
import fr.acinq.phoenix.android.components.nfc.NfcStateRepository
import fr.acinq.phoenix.android.services.HceService
import org.slf4j.LoggerFactory

/**
 * This class retrieves the first NDEF message in an NFC tag.
 *
 * Two tag types are supported:
 * - ISO-DEP tags (NFC Forum Type 4, e.g. NTAG424-DNA, Bolt Cards): communicates via a sequence
 *   of APDU commands, mirroring what's done in [HceService].
 * - Plain NDEF tags (NFC Forum Type 1/2, e.g. NTAG213/215/216): reads the NDEF message directly
 *   using the Android [Ndef] API without APDU negotiation.
 */
class NfcReaderCallback(val onFoundData: (String) -> Unit) : NfcAdapter.ReaderCallback {
    private val log = LoggerFactory.getLogger(this::class.java)

    @OptIn(ExperimentalStdlibApi::class)
    override fun onTagDiscovered(tag: Tag?) {
        log.info("discovered tag=$tag")

        if (!NfcStateRepository.isReading()) {
            log.info("aborting tag discovery: nfc_state=${NfcStateRepository.state.value}")
            return
        }

        // Prefer ISO-DEP (NFC Forum Type 4: NTAG424-DNA, Bolt Cards, …)
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            readIsoDepTag(isoDep)
            return
        }

        // Fallback: plain NDEF (NFC Forum Type 1/2: NTAG213, NTAG215, NTAG216, …)
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            readNdefTag(ndef)
            return
        }

        log.info("aborting tag discovery: tag does not support iso-dep or plain ndef")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun readIsoDepTag(isoDep: IsoDep) {
        try {
            log.debug("connecting to iso-dep tag")
            isoDep.connect()
            log.info("iso-dep reader connected, starting communication...")

            val selectCommand = ApduCommands.SELECT_AID
            log.debug("SEND select aid: ${selectCommand.toByteArray().toHexString()}")
            val responseSelect = isoDep.transceive(selectCommand.toByteArray())
            log.debug("RECV select aid: ${responseSelect.toByteVector().toHex()}")
            if (!responseSelect.contentEquals(ApduCommands.A_OKAY)) {
                throw IllegalArgumentException("invalid response for select aid: ${responseSelect.toHexString()}")
            }

            log.debug("SEND select cc: ${ApduCommands.SELECT_CC_FILE.toByteArray().toHexString()}")
            val selectCC = isoDep.transceive(ApduCommands.SELECT_CC_FILE.toByteArray())
            log.debug("RECV select cc: ${selectCC.toByteVector().toHex()}")
            if (!selectCC.contentEquals(ApduCommands.A_OKAY)) {
                throw IllegalArgumentException("invalid response for select cc: ${selectCC.toHexString()}")
            }

            log.debug("SEND read cc: ${ApduCommands.READ_CC.toByteArray().toHexString()}")
            val responseReadCC = isoDep.transceive(ApduCommands.READ_CC.toByteArray())
            log.debug("RECV read cc: ${responseReadCC.toByteVector().toHex()}")
            if (!responseReadCC.takeLast(2).toByteArray().contentEquals(ApduCommands.A_OKAY)) {
                throw IllegalArgumentException("invalid response for read cc: ${responseReadCC.toByteVector().toHex()}")
            }

            log.debug("SEND select ndef: ${ApduCommands.SELECT_NDEF_FILE.toByteArray().toHexString()}")
            val selectNdefFile = isoDep.transceive(ApduCommands.SELECT_NDEF_FILE.toByteArray())
            log.debug("RECV select ndef: ${selectNdefFile.toByteVector().toHex()}")
            if (!selectNdefFile.contentEquals(ApduCommands.A_OKAY)) {
                throw IllegalArgumentException("invalid response for select ndef: ${selectNdefFile.toByteVector().toHex()}")
            }

            log.debug("SEND select bin len: ${ApduCommands.READ_NDEF_BINARY_LENGTH.toByteArray().toHexString()}")
            val readBinaryLengthResponse = isoDep.transceive(ApduCommands.READ_NDEF_BINARY_LENGTH.toByteArray())
            log.debug("RECV select bin len: ${readBinaryLengthResponse.toByteVector().toHex()}")
            val (c,s) = readBinaryLengthResponse.dropLast(2) to readBinaryLengthResponse.takeLast(2)
            if (!s.toByteArray().contentEquals(ApduCommands.A_OKAY)) {
                throw IllegalArgumentException("invalid response for bin length: ${s.toByteArray().toHexString()}")
            }
            val messageLength = c.toByteArray().toHexString().toInt(16)
            log.debug("binary ndef message length=$messageLength")

            // depending on the length of the message contained in the tag, we send several commands to retrieve the
            // data in chunks

            var offset = 2
            val chunkSize = 59
            var result = byteArrayOf()

            while (offset < messageLength) {
                val size = if (offset + chunkSize > messageLength) {
                    messageLength - offset + 2
                } else {
                    chunkSize
                }
                val b = NfcHelper.intToByteArray(offset) + NfcHelper.intToByteArray(size)
                val padded = ApduCommands.READ_NDEF_BINARY + NfcHelper.fillByteArrayToFixedDimension(b, 3)
                log.debug("SEND read bin (o=$offset, len=$size): ${padded.toHexString()}")
                val r = isoDep.transceive(padded)
                log.debug("RECV read bin: ${r.toHexString()}")
                if (!r.takeLast(2).toByteArray().contentEquals(ApduCommands.A_OKAY)) {
                    throw IllegalArgumentException("invalid response for bin read (o=$offset len=$size): ${r.toHexString()}")
                }
                result += r.dropLast(2) // must drop A_OKAY suffix

                offset += chunkSize
            }

            val m = NdefMessage(result)
            log.info("successfully read iso-dep tag, found ndef_message=$m")
            val d = m.records.mapNotNull {
                NdefParser.parseNdefRecord(it)
            }
            d.firstOrNull()?.let {
                onFoundData(it)
            }

        } catch (e: Exception) {
            log.error("failed to read iso-dep tag: ", e)
        } finally {
            isoDep.close()
            if (NfcStateRepository.isReading()) {
                NfcStateRepository.updateState(NfcState.Inactive)
            }
            log.info("terminated iso-dep reader callback")
        }
    }

    private fun readNdefTag(ndef: Ndef) {
        try {
            log.debug("connecting to ndef tag type=${ndef.type}")
            ndef.connect()
            log.info("ndef reader connected, reading message...")

            val message = ndef.ndefMessage
            if (message == null) {
                log.info("ndef tag contains no message")
                return
            }

            log.info("successfully read ndef tag, found message=$message")
            val data = message.records.mapNotNull { NdefParser.parseNdefRecord(it) }
            data.firstOrNull()?.let { onFoundData(it) }

        } catch (e: Exception) {
            log.error("failed to read ndef tag: ", e)
        } finally {
            ndef.close()
            if (NfcStateRepository.isReading()) {
                NfcStateRepository.updateState(NfcState.Inactive)
            }
            log.info("terminated ndef reader callback")
        }
    }
}