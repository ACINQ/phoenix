/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.utils.encrypt

import com.tozny.crypto.android.AesCbcWithIntegrity
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.phoenix.BuildConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

class EncryptedBackup private constructor(version: Int, civ: AesCbcWithIntegrity.CipherTextIvMac) : EncryptedData(version, null, civ) {

  /**
   * Serializes an encrypted backup as a byte array, with the result depending on the object version.
   */
  @Throws(IOException::class)
  override fun write(): ByteArray {
    if (version == BACKUP_VERSION_1.toInt() || version == BACKUP_VERSION_2.toInt()) {
      if (civ.iv.size != IV_LENGTH_V1 || civ.mac.size != MAC_LENGTH_V1) {
        throw RuntimeException("could not serialize backup because fields are not of the correct length")
      }
      val array = ByteArrayOutputStream()
      array.write(version)
      array.write(civ.iv)
      array.write(civ.mac)
      array.write(civ.cipherText)
      return array.toByteArray()
    } else {
      throw RuntimeException("unhandled version")
    }
  }

  companion object {

    /**
     * Version 1 uses the same derivation path as BIP49 for the encryption key.
     *
     * @see .generateBackupKey_v1
     */
    @Deprecated("should only be used to decrypt older files, not to encrypt new files.")
    const val BACKUP_VERSION_1: Byte = 1

    /**
     * Version 2 uses either m/42'/0' (mainnet) or m/42'/1' (testnet) as derivation path for the encryption key.
     * This is the only difference with version 1.
     *
     * @see .generateBackupKey_v2
     */
    const val BACKUP_VERSION_2: Byte = 2

    private const val IV_LENGTH_V1 = 16
    private const val MAC_LENGTH_V1 = 32

    /**
     * Encrypt data with AES CBC and return an EncryptedBackup object containing the encrypted data.
     *
     * @param data    data to encrypt
     * @param key     the secret key encrypting the data
     * @param version the version describing the serialization to use for the EncryptedBackup object
     * @return a encrypted backup object ready to be serialized
     * @throws GeneralSecurityException
     */
    @Throws(GeneralSecurityException::class)
    fun encrypt(data: ByteArray, key: AesCbcWithIntegrity.SecretKeys, version: Int): EncryptedBackup {
      val civ = AesCbcWithIntegrity.encrypt(data, key)
      return EncryptedBackup(version, civ)
    }

    /**
     * Read an array of byte and deserializes it as an EncryptedBackup object.
     *
     * @param serialized array to deserialize
     * @return
     */
    fun read(serialized: ByteArray): EncryptedBackup {
      val stream = ByteArrayInputStream(serialized)
      val version = stream.read()
      if (version == BACKUP_VERSION_1.toInt() || version == BACKUP_VERSION_2.toInt()) {
        val iv = ByteArray(IV_LENGTH_V1)
        stream.read(iv, 0, IV_LENGTH_V1)
        val mac = ByteArray(MAC_LENGTH_V1)
        stream.read(mac, 0, MAC_LENGTH_V1)
        val cipher = ByteArray(stream.available())
        stream.read(cipher, 0, stream.available())
        return EncryptedBackup(version, AesCbcWithIntegrity.CipherTextIvMac(cipher, iv, mac))
      } else {
        throw RuntimeException("unhandled encrypted backup version")
      }
    }

    /**
     * Derives a hardened key from the extended key. This is used to encrypt/decrypt the channels backup files.
     * Path is the same as BIP49.
     */
    fun generateBackupKey_v1(pk: DeterministicWallet.ExtendedPrivateKey): ByteVector32 {
      val dpriv = DeterministicWallet.derivePrivateKey(pk, DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/49'"))
      return dpriv.secretkeybytes()
    }

    /**
     * Derives a hardened key from the extended key. This is used to encrypt/decrypt the channels backup files.
     * Path depends on the chain used by the wallet, mainnet or testnet.
     */
    fun generateBackupKey_v2(pk: DeterministicWallet.ExtendedPrivateKey): ByteVector32 {
      val dpriv = DeterministicWallet.derivePrivateKey(pk, DeterministicWallet.`KeyPath$`.`MODULE$`.apply(if ("mainnet" == BuildConfig.CHAIN) "m/42'/0'" else "m/42'/1'"))
      return dpriv.secretkeybytes()
    }
  }
}
