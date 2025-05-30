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

import fr.acinq.bitcoin.ByteVector

object ApduCommands {

    private const val SELECT_FILE = "00A4000C02"

    // structure is CLA-INS-P1-P2 -- NDEF AID -- LE field
    // This AID is also the one defined in res/xml/apduservice.xml
    val SELECT_AID = ByteVector("00A4040007" + "D2760000850101" + "00")

    // file id of the CC file is 0xe103
    val SELECT_CC_FILE = ByteVector(SELECT_FILE + "E103")
    // file id of the ndef file is e104
    val SELECT_NDEF_FILE = ByteVector(SELECT_FILE + "E104")

    // file identifier = ndef
    // maximum ndef file size = 528b
    // read/write access 00
    val READ_CC = ByteVector("00B000000F")
    val READ_CC_RESPONSE = ByteVector("000F20003B00340406" + "E104" + "0210" + "0000" + "9000").toByteArray()

    val READ_NDEF_BINARY_LENGTH = ByteVector("00B0000002")
    val READ_NDEF_BINARY = ByteVector("00B0").toByteArray()

    val A_OKAY = ByteVector("9000").toByteArray()
    val A_ERROR = ByteVector("6A82").toByteArray()
    val NDEF_FILE = ByteVector("E104").toByteArray()
}