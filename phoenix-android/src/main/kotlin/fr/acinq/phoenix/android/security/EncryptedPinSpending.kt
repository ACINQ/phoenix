/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.security

import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.utils.extensions.tryWith
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException


/**
 * This object represents an encrypted PIN data.
 *
 * Similar to the [EncryptedSeed] data: it contains a version, IV, and the encrypted payload and uses a key
 * from the Android Keystore to encrypt the PIN.
 */
sealed class EncryptedPinSpending: EncryptedPin.KeystoreEncrypted() {

    class SingleWallet(override val iv: ByteArray, override val ciphertext: ByteArray) : EncryptedPinSpending() {
        override val name: String = "ENCRYPTED_SPENDING_PIN_SINGLEWALLET"
    }

    class MultipleWallet(override val iv: ByteArray, override val ciphertext: ByteArray) : EncryptedPinSpending() {
        override val name: String = "ENCRYPTED_SPENDING_PIN_MULTIWALLET"

        fun decryptAndGetPins(): Map<WalletId, String> {
            val payload = decrypt().decodeToString()
            val json = Json.decodeFromString<Map<String, String>>(payload)
            return json.map { WalletId(it.key) to it.value }.toMap()
        }
    }

    companion object {
        const val SINGLE_WALLET_VERSION = 1
        const val MULTIPLE_WALLET_VERSION = 2

        fun encrypt(pinMap: Map<WalletId, String>): MultipleWallet = tryWith(GeneralSecurityException()) {
            val json = Json.encodeToString(
                pinMap.map { (id, pin) -> id.nodeIdHash to pin }.toMap()
            )
            val cipher = KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_FOR_PINCODE_V1)
            MultipleWallet(cipher.iv, cipher.doFinal(json.encodeToByteArray()))
        }

        fun deserialize(serialized: ByteArray): EncryptedPinSpending {
            val stream = ByteArrayInputStream(serialized)
            val version = stream.read()
            when (version) {
                SINGLE_WALLET_VERSION, MULTIPLE_WALLET_VERSION -> {
                    val (iv, ciphertext) = deserialize(stream)
                    return when (version) {
                        SINGLE_WALLET_VERSION -> SingleWallet(iv, ciphertext)
                        MULTIPLE_WALLET_VERSION -> MultipleWallet(iv, ciphertext)
                        else -> throw IllegalArgumentException("unhandled V1 spending-pin version=$version")
                    }
                }
                else -> throw IllegalArgumentException("unhandled spending-pin file version=$version")
            }
        }
    }
}
