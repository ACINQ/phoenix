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

import android.content.Context
import fr.acinq.phoenix.android.WalletId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

abstract class EncryptedPin {
    abstract val name: String
    override fun toString(): String = name

    /**
     * Data is encrypted with a key from the Android Keystore. The keystore's key does not require user authentication to be used.
     * The serialized content contains the version, the IV, and the payload.
     */
    abstract class KeystoreEncrypted : EncryptedPin() {
        abstract val iv: ByteArray
        abstract val ciphertext: ByteArray

        fun decrypt(): ByteArray = KeystoreHelper.getDecryptionCipher(KeystoreHelper.KEY_FOR_PINCODE_V1, iv).doFinal(ciphertext)

        fun serialize(version: Int): ByteArray {
            if (iv.size != IV_LENGTH) {
                throw RuntimeException("cannot serialize $name: iv not of the correct length (${iv.size}/$IV_LENGTH)")
            }
            val array = ByteArrayOutputStream()
            array.write(version)
            array.write(iv)
            array.write(ciphertext)
            return array.toByteArray()
        }

        companion object Companion {
            const val IV_LENGTH = 16
            fun deserialize(stream: ByteArrayInputStream): Pair<ByteArray, ByteArray> {
                val iv = ByteArray(IV_LENGTH)
                stream.read(iv, 0, IV_LENGTH)
                val cipherText = ByteArray(stream.available())
                stream.read(cipherText, 0, stream.available())
                return iv to cipherText
            }
        }
    }
}

object PinManager {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val LOCK_PIN_FILE_NAME = "pin.dat"
    private const val SPENDING_PIN_FILE_NAME = "spending_pin.dat"

    private fun getDataDir(context: Context): File {
        val datadir = SeedManager.getDatadir(context)
        if (!datadir.exists()) datadir.mkdirs()
        return datadir
    }

    private fun getEncryptedPinFromDisk(context: Context, fileName: String): ByteArray? {
        val encryptedPinFile = File(getDataDir(context), fileName)
        return if (!encryptedPinFile.exists()) {
            null
        } else if (!encryptedPinFile.isFile || !encryptedPinFile.canRead() || !encryptedPinFile.canWrite()) {
            log.warn("$fileName exists but is not usable")
            null
        } else {
            encryptedPinFile.readBytes().takeIf { it.isNotEmpty() }
        }
    }

    fun getLockPinMapFromDisk(context: Context): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk(context, LOCK_PIN_FILE_NAME)?.let {
            EncryptedPinLock.deserialize(it)
        }
        return when (encryptedPin) {
            is EncryptedPinLock.SingleWallet -> {
                log.warn("[SingleWallet] lock-pin, migration should be performed")
                emptyMap()
            }
            is EncryptedPinLock.MultipleWallet -> encryptedPin.decryptAndGetPins()
            null -> emptyMap()
        }
    }

    fun getSpendingPinMapFromDisk(context: Context): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk(context, SPENDING_PIN_FILE_NAME)?.let {
            EncryptedPinSpending.deserialize(it)
        }
        return when (encryptedPin) {
            is EncryptedPinSpending.SingleWallet -> {
                log.warn("[SingleWallet] spending-pin, migration should be performed")
                emptyMap()
            }
            is EncryptedPinSpending.MultipleWallet -> encryptedPin.decryptAndGetPins()
            null -> emptyMap()
        }
    }

    fun writeLockPinMapToDisk(context: Context, pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinLock.encrypt(pinMap)
        val datadir = getDataDir(context)
        val temp = File(datadir, "temporary_pin.dat")
        temp.writeBytes(encryptedPin.serialize(EncryptedPinLock.MULTIPLE_WALLET_VERSION))
        temp.copyTo(File(datadir, LOCK_PIN_FILE_NAME), overwrite = true)
        temp.delete()
    }

    fun writeSpendingPinMapToDisk(context: Context, pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinSpending.encrypt(pinMap)
        val datadir = getDataDir(context)
        val temp = File(datadir, "temporary_pin.dat")
        temp.writeBytes(encryptedPin.serialize(EncryptedPinSpending.MULTIPLE_WALLET_VERSION))
        temp.copyTo(File(datadir, SPENDING_PIN_FILE_NAME), overwrite = true)
        temp.delete()
    }

    fun migrateSingleWalletPinCode(context: Context, walletId: WalletId) {
        val encryptedLockPin = getEncryptedPinFromDisk(context, LOCK_PIN_FILE_NAME)?.let {
            EncryptedPinLock.deserialize(it)
        }
        when (encryptedLockPin) {
            is EncryptedPinLock.SingleWallet -> {
                val oldPin = encryptedLockPin.decrypt().decodeToString()
                writeLockPinMapToDisk(context, mapOf(walletId to oldPin))
                log.info("migrated lock-pin for wallet=$walletId")
            }
            else -> Unit
        }

        val encryptedSpendingPin = getEncryptedPinFromDisk(context, SPENDING_PIN_FILE_NAME)?.let {
            EncryptedPinSpending.deserialize(it)
        }
        when (encryptedSpendingPin) {
            is EncryptedPinSpending.SingleWallet -> {
                val oldPin = encryptedSpendingPin.decrypt().decodeToString()
                writeSpendingPinMapToDisk(context, mapOf(walletId to oldPin))
                log.info("migrated spending-pin for wallet=$walletId")
            }
            else -> Unit
        }
    }
}