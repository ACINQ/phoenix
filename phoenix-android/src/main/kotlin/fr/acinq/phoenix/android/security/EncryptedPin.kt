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

import android.content.Context
import fr.acinq.phoenix.android.utils.tryWith
import fr.acinq.secp256k1.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey


/**
 * This object represents an encrypted PIN data.
 *
 * Similar to the [EncryptedSeed] data: it contains a version, IV, and the encrypted payload and uses a key
 * from the Android Keystore to encrypt the PIN.
 */
sealed class EncryptedPin {

    abstract val name: String
    override fun toString(): String = name

    abstract fun serialize(): ByteArray

    /**
     * Version 1 encrypts the PIN with a [SecretKey] from the Android Keystore which does not
     * require user authentication in order to be used.
     */
    data class V1(val iv: ByteArray, val ciphertext: ByteArray) : EncryptedPin() {
        override val name: String = "ENCRYPTED_PIN_V1"

        fun decrypt(): ByteArray = KeystoreHelper.getDecryptionCipher(KeystoreHelper.KEY_FOR_PINCODE_V1, iv).doFinal(ciphertext)

        override fun serialize(): ByteArray {
            if (iv.size != IV_LENGTH) {
                throw RuntimeException("cannot serialize $name: iv not of the correct length (${iv.size}/$IV_LENGTH)")
            }
            val array = ByteArrayOutputStream()
            array.write(version.toInt())
            array.write(iv)
            array.write(ciphertext)
            return array.toByteArray()
        }

        companion object {
            /** Version byte written at the start of the encrypted pin file. */
            const val version: Byte = 1

            fun encrypt(pin: ByteArray): V1 = tryWith(GeneralSecurityException()) {
                val cipher = KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_FOR_PINCODE_V1)
                val ciphertext = cipher.doFinal(pin)
                V1(cipher.iv, ciphertext)
            }
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        private const val IV_LENGTH = 16
        private const val FILE_NAME = "pin.dat"

        /** Reads an array of byte and de-serializes it as an [EncryptedPin] object. */
        private fun deserialize(serialized: ByteArray): EncryptedPin {
            val stream = ByteArrayInputStream(serialized)
            return when (val version = stream.read()) {
                V1.version.toInt() -> {
                    val iv = ByteArray(IV_LENGTH)
                    stream.read(iv, 0, IV_LENGTH)
                    val cipherText = ByteArray(stream.available())
                    stream.read(cipherText, 0, stream.available())
                    V1(iv, cipherText)
                }

                else -> throw UnsupportedOperationException("unhandled version=$version")
            }
        }

        private fun getDataDir(context: Context): File {
            val datadir = SeedManager.getDatadir(context)
            if (!datadir.exists()) datadir.mkdirs()
            return datadir
        }

        fun getPinFromDisk(context: Context): String? {
            val encryptedPinFile = File(getDataDir(context), FILE_NAME)
            return if (!encryptedPinFile.exists()) {
                null
            } else if (!encryptedPinFile.isFile || !encryptedPinFile.canRead() || !encryptedPinFile.canWrite()) {
                log.warn("pin.dat exists but is not usable")
                null
            } else {
                encryptedPinFile.readBytes().takeIf { it.isNotEmpty() }?.let {
                    deserialize(it)
                }?.let {
                    when (it) {
                        is V1 -> it.decrypt().decodeToString()
                    }
                }
            }
        }

        fun writePinToDisk(context: Context, pin: String) {
            val encryptedPin = V1.encrypt(pin.encodeToByteArray())
            val datadir = getDataDir(context)
            val temp = File(datadir, "temporary_pin.dat")
            temp.writeBytes(encryptedPin.serialize())
            temp.copyTo(File(datadir, FILE_NAME), overwrite = true)
            temp.delete()
        }
    }
}
