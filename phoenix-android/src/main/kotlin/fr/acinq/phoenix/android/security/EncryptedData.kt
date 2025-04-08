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

package fr.acinq.phoenix.android.security

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.ChaCha20Poly1305
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.managers.cloudKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


/**
 * This object represents data encrypted with a key derived from the wallet seed. The type of encryption and key used depends on [version].
 *
 * Note that the data is zipped before encryption.
 *
 * @param version the version used for the data encryption and for serializing this object
 * @param data a byte array containing the zipped & encrypted payload, followed by the stuff needed for decryption (which depends on the version)
 */
class EncryptedData(val version: Version, val data: ByteArray) {

    sealed class Version(val code: Byte) {
        // use ChaCha20Poly1305, inspired from the channel backup encryption code in lightning-kmp
        // see https://github.com/ACINQ/lightning-kmp/blob/feda82c853660a792b911be518367a228ed6e0ee/modules/core/src/commonMain/kotlin/fr/acinq/lightning/serialization/channel/Encryption.kt#L14
        data object V1 : Version(1) {
            fun getKey(keyManager: LocalKeyManager) = keyManager.cloudKey().toByteArray()
        }
    }

    /** Decrypts the [data] payload. The data is unzipped after being decrypted. */
    fun decrypt(keyManager: LocalKeyManager): ByteVector {
        val decryptedPayload = when (val v = version) {
            is Version.V1 -> {
                val key = v.getKey(keyManager)
                // nonce is 12B, tag is 16B
                val ciphertext = data.dropLast(12 + 16).toByteArray()
                val nonce = data.takeLast(12 + 16).take(12).toByteArray()
                val tag = data.takeLast(16).toByteArray()
                ChaCha20Poly1305.decrypt(key, nonce, ciphertext, ByteArray(0), tag)
            }
        }

        val unzipped = ByteArrayInputStream(decryptedPayload).use { bis ->
            ZipInputStream(bis).use { zis ->
                zis.nextEntry
                zis.readBytes()
            }
        }

        return unzipped.byteVector()
    }

    /** Serialize this object into a byte array, so that it can be for example written to a file. */
    fun write(): ByteArray {
        return when (version) {
            Version.V1 -> {
                val bos = ByteArrayOutputStream()
                bos.write(version.code.toInt())
                bos.write(data)
                bos.toByteArray()
            }
        }
    }

    companion object {

        /**
         * Encrypts [data] using a key from the wallet's [keyManager]. The key used depends on [version]. The data is zipped before being encrypted.
         *
         * @param data the unencrypted data
         * @returns an [EncryptedData] object, containing the zipped & encrypted payload
         */
        fun encrypt(version: Version, data: ByteArray, keyManager: LocalKeyManager): EncryptedData {

            val payload = ByteArrayOutputStream().let {
                ZipOutputStream(it).use { zos ->
                    zos.putNextEntry(ZipEntry("data"))
                    zos.write(data)
                }
                it.toByteArray()
            }

            return when (version) {
                is Version.V1 -> {
                    val key = version.getKey(keyManager)
                    val nonce = Crypto.sha256(payload).take(12).toByteArray()
                    val (ciphertext, tag) = ChaCha20Poly1305.encrypt(key, nonce, payload, ByteArray(0))
                    EncryptedData(version, data = ciphertext + nonce + tag)
                }
            }
        }

        /** Deserializes the blob data into an [EncryptedData] object. Throws an exception if the version byte is invalid. */
        fun read(data: ByteArray): EncryptedData {
            return ByteArrayInputStream(data).use { bis ->
                when (val version = bis.read().toByte()) {
                    Version.V1.code -> {
                        val remainingBytes = bis.available()
                        val payload = ByteArray(remainingBytes)
                        bis.read(payload, 0, remainingBytes)
                        EncryptedData(version = Version.V1, data = payload)
                    }

                    else -> {
                        throw RuntimeException("unhandled version=$version")
                    }
                }
            }
        }
    }
}