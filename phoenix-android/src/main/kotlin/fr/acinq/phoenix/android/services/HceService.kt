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
import fr.acinq.phoenix.android.utils.nfc.ApduCommands
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.A_ERROR
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.A_OKAY
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.NDEF_FILE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_CC
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_CC_RESPONSE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_NDEF_BINARY
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.READ_NDEF_BINARY_LENGTH
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_AID
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_CC_FILE
import fr.acinq.phoenix.android.utils.nfc.ApduCommands.SELECT_NDEF_FILE
import fr.acinq.phoenix.android.utils.nfc.NfcHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


/**
 * This service emulates a NDEF type 4 tag. See the NFC Forum specs section 5.4 for the expected
 * sequence of messages/responses. We hardcode most of them in [ApduCommands].
 *
 * Uses [HceStateRepository] to pass the payment request to emit.
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
            HceStateRepository.state.collect {
                ndefMessage = when (it) {
                    is HceState.Active -> HceMessage(it.paymentRequest)
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
        if (HceStateRepository.state.value is HceState.Inactive) return A_ERROR
        val message = ndefMessage ?: return A_ERROR

        return when {
            SELECT_AID.contentEquals(commandApdu) -> {
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
                val response = getBinaryResponse(commandApdu, message)
                val expectedResponse = message.message.records[0].payload.toHexString() + A_OKAY.toHexString()
                if (expectedResponse.contains(response.toHexString())) {
                    log.info("sent payment request via nfc: ${message.paymentRequest}")
                    HceStateRepository.updateState(HceState.Inactive)
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

            val offset = commandApdu.sliceArray(2..3).toHexString().toInt(16)
            val length = commandApdu[4].toInt() and 0xFF

            val fullResponse = message.messageLength + message.messageBytes

            if (offset >= fullResponse.size) {
                log.error("offset $offset is beyond full response size ${fullResponse.size}")
                return A_ERROR
            }

            log.debug("full_response=${fullResponse.toHexString()} with offset=$offset length=$length")

            val slicedResponse = fullResponse.sliceArray(offset until fullResponse.size)
            val realLength = minOf(length, slicedResponse.size)
            val response = slicedResponse.copyOfRange(0, realLength) + A_OKAY

            return response
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
        HceStateRepository.updateState(HceState.Inactive)
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

sealed class HceState {
    data object Inactive: HceState()
    data class Active(val paymentRequest: String): HceState()
}

object HceStateRepository {
    private val _state = MutableStateFlow<HceState?>(null)
    val state = _state.asStateFlow()

    fun updateState(s: HceState) {
        _state.value = s
    }
}

