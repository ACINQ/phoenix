/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.services

import android.app.Service
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.android.components.nfc.NfcState
import fr.acinq.phoenix.android.components.nfc.NfcStateRepository
import fr.acinq.phoenix.android.utils.nfc.ApduCommands
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.A_ERROR
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.A_OKAY
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.NDEF_FILE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_CC
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_CC_RESPONSE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_NDEF_BINARY
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_NDEF_BINARY_LENGTH
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_AID
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_AID_LE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_CC_FILE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_NDEF_FILE
import fr.acinq.phoenix.android.utils.nfc.NfcHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


/**
 * This service emulates a NDEF type 4 tag. See the NFC Forum specs section 5.4 for the expected
 * sequence of messages/responses. We hardcode most of them in [ApduCommands].
 *
 * Uses [NfcStateRepository] to pass the payment request to emit.
 *
 * The message will be a Ndef text record with TNF WELL-KNOWN.
 */
class HceService : HostApduService() {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var ndefMessage: HceMessage? = null

    override fun onCreate() {
        super.onCreate()
        log.debug("creating hce service")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log.debug("onStartCommand")
        serviceScope.launch {
            NfcStateRepository.state.collect {
                ndefMessage = when (it) {
                    is NfcState.EmulatingTag -> HceMessage(it.paymentRequest)
                    else -> null
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        log.debug("processing apdu command={} extras={}", commandApdu?.byteVector()?.toHex(), extras)

        if (commandApdu == null) return A_ERROR
        val nfcState = NfcStateRepository.state.value
        if (nfcState is NfcState.Inactive) {
            log.debug("trying to emulate tag in state={}, aborting", nfcState)
            return A_ERROR
        }
        val message = ndefMessage ?: return A_ERROR

        return when {
            SELECT_AID.contentEquals(commandApdu) or SELECT_AID_LE.contentEquals(commandApdu) -> {
                log.debug("selecting ndef tag AID, returning {}", A_OKAY.toHexString())
                A_OKAY
            }

            SELECT_CC_FILE.contentEquals(commandApdu) -> {
                log.debug("selecting capability container, returning {}", A_OKAY.toHexString())
                A_OKAY
            }

            READ_CC.contentEquals(commandApdu) -> {
                log.debug("selecting capability container file, returning {}", READ_CC_RESPONSE.toHexString())
                READ_CC_RESPONSE
            }

            SELECT_NDEF_FILE.contentEquals(commandApdu) -> {
                log.debug("selecting ndef file, returning {}", A_OKAY.toHexString())
                A_OKAY
            }

            READ_NDEF_BINARY_LENGTH.contentEquals(commandApdu) -> {
                val response = message.messageLength + A_OKAY
                log.debug("reading ndef message length, returning {}", response.toHexString())
                response
            }

            commandApdu.size > 2 && commandApdu.sliceArray(0..1).contentEquals(READ_NDEF_BINARY) -> {
                // we may receive several commands in a row if the message is large enough
                val response = getBinaryResponse(commandApdu, message)
                val expectedResponse = message.message.records[0].payload.toHexString() + A_OKAY.toHexString()
                if (expectedResponse.contains(response.toHexString())) {
                    log.info("done sending payment request via nfc: ${message.paymentRequest}")
                    NfcStateRepository.updateState(NfcState.Inactive)
                }
                log.debug("reading ndef message, returning {}", response.toHexString())
                response
            }

            else -> {
                log.debug("unhandled apdu command={}", commandApdu)
                A_ERROR
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getBinaryResponse(commandApdu: ByteArray, message: HceMessage): ByteArray {
        try {
            if (commandApdu.size < 5) {
                log.error("invalid APDU: too short (${commandApdu.size} bytes)")
                return A_ERROR
            }

            // the full response before applying offset
            val response = message.messageLength + message.messageBytes

            // the reader may want the data in chunks
            val offset = commandApdu.sliceArray(2..3).toHexString().toInt(16)
            val chunkSize = commandApdu[4].toInt() and 0xFF
            log.debug("offset=$offset length=$chunkSize for full_response=${response.toHexString()} =")

            if (offset >= response.size) {
                log.error("offset $offset is beyond full response size ${response.size}")
                return A_ERROR
            }

            val responseAfterOffset = response.sliceArray(offset until response.size)
            val chunkSizeForOffset = minOf(chunkSize, responseAfterOffset.size)
            val chunk = responseAfterOffset.copyOfRange(0, chunkSizeForOffset) + A_OKAY

            return chunk
        } catch (e: Exception) {
            log.error("error when getting binary response: {}", e.localizedMessage)
            return A_ERROR
        }
    }

    override fun onDeactivated(reason: Int) {
        when (reason) {
            DEACTIVATION_DESELECTED -> log.info("deactivation: different AID selected")
            DEACTIVATION_LINK_LOSS -> log.info("deactivation: link lost")
            else -> log.info("deactivation: code $reason")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        log.info("service removed")
        if (NfcStateRepository.state.value is NfcState.EmulatingTag) {
            NfcStateRepository.updateState(NfcState.Inactive)
        }
    }

    private data class HceMessage(val paymentRequest: String) {
        val message by lazy { NdefMessage(NfcHelper.createTextRecord(paymentRequest, NDEF_FILE)) }
        val messageBytes: ByteArray by lazy { message.toByteArray() }
        val messageLength by lazy {
            NfcHelper.fillByteArrayToFixedDimension(messageBytes.size.toBigInteger().toByteArray(), 2)
        }

        override fun toString(): String {
            return "message=$paymentRequest length=$messageLength tnf=${message.records.firstOrNull()?.tnf} type=${message.records.firstOrNull()?.type}"
        }
    }
}
