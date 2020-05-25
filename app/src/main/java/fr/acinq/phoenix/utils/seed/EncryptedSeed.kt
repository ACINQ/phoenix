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

package fr.acinq.phoenix.utils.seed

import com.google.common.io.Files
import com.tozny.crypto.android.AesCbcWithIntegrity
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.NoSeedYet
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Wallet

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

class EncryptedSeed private constructor(version: Int, salt: ByteArray, civ: AesCbcWithIntegrity.CipherTextIvMac) : EncryptedData(version, salt, civ) {

  /**
   * Serializes an encrypted seed as a byte array.
   */
  @Throws(IOException::class)
  override fun write(): ByteArray {
    if (version == SEED_FILE_VERSION_1.toInt()) {
      if (salt?.size != SALT_LENGTH_V1 || civ.iv.size != IV_LENGTH_V1 || civ.mac.size != MAC_LENGTH_V1) {
        throw RuntimeException("could not serialize seed because fields are not of the right length")
      }
      val array = ByteArrayOutputStream()
      array.write(version)
      array.write(salt)
      array.write(civ.iv)
      array.write(civ.mac)
      array.write(civ.cipherText)
      return array.toByteArray()
    } else {
      throw RuntimeException("unhandled version")
    }
  }

  companion object {

    const val SEED_FILE_VERSION_1: Byte = 1
    private const val SALT_LENGTH_V1 = 128
    private const val IV_LENGTH_V1 = 16
    private const val MAC_LENGTH_V1 = 32

    /**
     * Encrypt a non encrypted seed with AES CBC and return an object containing the encrypted seed.
     *
     * @param seed     the seed to encrypt
     * @param password the password encrypting the seed
     * @param version  the version describing the serialization to use for the EncryptedSeed object
     * @return a encrypted seed ready to be serialized
     * @throws GeneralSecurityException
     */
    @Throws(GeneralSecurityException::class)
    private fun encrypt(seed: ByteArray, password: String, version: Int): EncryptedSeed {
      val salt = AesCbcWithIntegrity.generateSalt()
      val sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
      val civ = AesCbcWithIntegrity.encrypt(seed, sk)
      return EncryptedSeed(version, salt, civ)
    }

    /**
     * Reads an array of byte and de-serializes it as an EncryptedSeed object.
     *
     * @param serialized array to deserialize
     * @return
     */
    private fun read(serialized: ByteArray): EncryptedSeed {
      val stream = ByteArrayInputStream(serialized)
      val version = stream.read()
      if (version == SEED_FILE_VERSION_1.toInt()) {
        val salt = ByteArray(SALT_LENGTH_V1)
        stream.read(salt, 0, SALT_LENGTH_V1)
        val iv = ByteArray(IV_LENGTH_V1)
        stream.read(iv, 0, IV_LENGTH_V1)
        val mac = ByteArray(MAC_LENGTH_V1)
        stream.read(mac, 0, MAC_LENGTH_V1)
        val cipher = ByteArray(stream.available())
        stream.read(cipher, 0, stream.available())
        return EncryptedSeed(version, salt, AesCbcWithIntegrity.CipherTextIvMac(cipher, iv, mac))
      } else {
        throw RuntimeException("unhandled encrypted seed file version")
      }
    }

    fun writeSeedToDir(datadir: File, seed: ByteArray, password: String?) {
      try {
        // 1 - create datadir
        if (!datadir.exists()) {
          datadir.mkdirs()
        }

        val seedFile = File(datadir, Wallet.SEED_FILE)
        if (password == null) {
          // 2a - if there is no password, directly write the seed to file
          Files.write(seed, seedFile)
        } else {
          // 2b - encrypt and write in a temporary file
          val temp = File(datadir, "temporary_seed.dat")
          val encryptedSeed = encrypt(seed, password, SEED_FILE_VERSION_1.toInt())
          Files.write(encryptedSeed.write(), temp)

          // 3 - decrypt temp file and check validity; if correct, move temp file to final file
          val checkSeed = readSeedFromFile(temp, password)
          if (!AesCbcWithIntegrity.constantTimeEq(checkSeed, seed)) {
            throw GeneralSecurityException()
          } else {
            Files.move(temp, seedFile)
          }
        }
      } catch (e: GeneralSecurityException) {
        throw RuntimeException("encryption failure when writing seed")
      }
    }

    @Throws(IOException::class, IllegalAccessException::class, GeneralSecurityException::class)
    fun readSeedFromDir(datadir: File, password: String?): ByteArray {
      val seedFile = File(datadir, Wallet.SEED_FILE)
      if (!seedFile.exists() || !seedFile.canRead() || !seedFile.isFile) {
        throw NoSeedYet
      }
      return readSeedFromFile(seedFile, password)
    }

    private fun readSeedFromFile(seedFile: File, password: String?): ByteArray {
      val content = Files.toByteArray(seedFile)
      return if (password == null) {
        content
      } else {
        Files.toByteArray(seedFile)
        val encryptedSeed = read(content)
        return encryptedSeed.decrypt(password)
      }
    }
  }
}
