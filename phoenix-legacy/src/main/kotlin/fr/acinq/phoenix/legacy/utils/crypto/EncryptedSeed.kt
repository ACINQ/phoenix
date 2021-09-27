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

package fr.acinq.phoenix.legacy.utils.crypto

import android.content.Context
import androidx.annotation.WorkerThread
import com.google.common.io.Files
import com.tozny.crypto.android.AesCbcWithIntegrity
import fr.acinq.bitcoin.scala.MnemonicCode
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Wallet
import fr.acinq.phoenix.legacy.utils.tryWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.bouncycastle.util.encoders.Hex
import scodec.bits.ByteVector
import scodec.bits.`ByteVector$`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey

object SeedManager {

  /** Write the seed as an [EncryptedSeed.V1] blob to disk. */
  @Deprecated("use EncryptedSeed.V2 instead")
  fun writeSeedToDirV1(dir: File, seed: ByteArray, password: String) {
    try {
      // 1 - create dir
      if (!dir.exists()) {
        dir.mkdirs()
      }

      val seedFile = File(dir, Wallet.SEED_FILE)
      // encrypt and write in a temporary file
      val temp = File(dir, "temporary_seed.dat")
      val encryptedSeed = EncryptedSeed.V1.encrypt(seed, password)
      Files.write(encryptedSeed.serialize(), temp)

      // 3 - decrypt temp file and check validity; if correct, move temp file to final file
      val checkSeed = (getSeedFromDir(dir, temp.name) as EncryptedSeed.V1).decrypt(password)
      if (!AesCbcWithIntegrity.constantTimeEq(checkSeed, seed)) {
        throw GeneralSecurityException()
      } else {
        Files.move(temp, seedFile)
      }
    } catch (e: GeneralSecurityException) {
      throw RuntimeException("encryption failure when writing seed")
    }
  }

  /** Write the seed as an [EncryptedSeed.V2] encrypted blob to disk. Dir should be in the app's private directory. */
  @WorkerThread
  fun writeSeedToDisk(dir: File, encryptedSeed: EncryptedSeed.V2) {
    // 1 - create dir
    if (!dir.exists()) {
      dir.mkdirs()
    }

    // 2 - encrypt and write in a temporary file
    val temp = File(dir, "temporary_seed.dat")
    Files.write(encryptedSeed.serialize(), temp)

    // 3 - decrypt temp file and check validity; if correct, move temp file to final file
    val checkSeed = (getSeedFromDir(dir, temp.name) as EncryptedSeed.V2)
    if (!AesCbcWithIntegrity.constantTimeEq(checkSeed.ciphertext, encryptedSeed.ciphertext)) {
      throw GeneralSecurityException()
    }
    Files.move(temp, File(dir, Wallet.SEED_FILE))
  }

  /** Extract the encrypted seed contained in a given file/folder. */
  fun getSeedFromDir(dir: File, seedFileName: String = Wallet.SEED_FILE): EncryptedSeed? {
    val seedFile = File(dir, seedFileName)
    return if (!seedFile.exists() || !seedFile.canRead() || !seedFile.isFile) {
      null
    } else {
      EncryptedSeed.deserialize(Files.toByteArray(seedFile))
    }
  }
}

sealed class EncryptedSeed {

  /** Serializes an encrypted seed as a byte array. */
  abstract fun serialize(): ByteArray

  fun name(): String = javaClass.canonicalName ?: javaClass.simpleName

  /** Version 1 encrypts the seed using a 6-digits PIN and [AesCbcWithIntegrity]. */
  @Deprecated("this EncryptedSeed class is deprecated", replaceWith = ReplaceWith("EncryptedSeed.V2.WithAuth"), level = DeprecationLevel.WARNING)
  class V1(private val salt: ByteArray, private val civ: AesCbcWithIntegrity.CipherTextIvMac) : EncryptedSeed() {

    override fun serialize(): ByteArray {
      if (salt.size != SALT_LENGTH || civ.iv.size != IV_LENGTH || civ.mac.size != MAC_LENGTH) {
        throw RuntimeException("could not serialize seed because fields are not of the right length")
      }
      val array = ByteArrayOutputStream()
      array.write(SEED_FILE_VERSION_1.toInt())
      array.write(salt)
      array.write(civ.iv)
      array.write(civ.mac)
      array.write(civ.cipherText)
      return array.toByteArray()
    }

    /**
     * Decrypt an encrypted data object with a password and returns a byte array.
     * @throws GeneralSecurityException if the password is not correct
     */
    @Throws(GeneralSecurityException::class)
    fun decrypt(password: String): ByteArray {
      val sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
      return AesCbcWithIntegrity.decrypt(civ, sk)
    }

    companion object {
      private const val SALT_LENGTH = 128
      private const val IV_LENGTH = 16
      private const val MAC_LENGTH = 32

      /**
       * Encrypt a non encrypted seed with AES CBC and return an object containing the encrypted seed.
       *
       * @param seed     the seed to encrypt
       * @param password the password encrypting the seed
       * @return a encrypted seed ready to be serialized
       * @throws GeneralSecurityException
       */
      fun encrypt(seed: ByteArray, password: String): V1 {
        val salt = AesCbcWithIntegrity.generateSalt()
        val sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
        val civ = AesCbcWithIntegrity.encrypt(seed, sk)
        return V1(salt, civ)
      }

      /** Read a stream of a byte array and transform it into an EncryptSeedV1 object. */
      fun deserialize(stream: ByteArrayInputStream): V1 {
        val salt = ByteArray(SALT_LENGTH)
        stream.read(salt, 0, SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        stream.read(iv, 0, IV_LENGTH)
        val mac = ByteArray(MAC_LENGTH)
        stream.read(mac, 0, MAC_LENGTH)
        val cipher = ByteArray(stream.available())
        stream.read(cipher, 0, stream.available())
        return V1(salt, AesCbcWithIntegrity.CipherTextIvMac(cipher, iv, mac))
      }
    }
  }

  /** Version 2 encrypts the seed with a [SecretKey] from the Android Keystore. */
  sealed class V2(val keyAlias: String) : EncryptedSeed() {
    abstract val iv: ByteArray
    abstract val ciphertext: ByteArray

    /** This seed is encrypted with a key that does *NOT* require user unlock. */
    class NoAuth(override val iv: ByteArray, override val ciphertext: ByteArray) : V2(KeystoreHelper.KEY_NO_AUTH) {
      fun decrypt(): ByteArray = tryWith(GeneralSecurityException()) { getDecryptionCipher().doFinal(ciphertext) }

      companion object {
        fun encrypt(seed: ByteArray): NoAuth = tryWith(GeneralSecurityException()) {
          val cipher = KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_NO_AUTH)
          NoAuth(cipher.iv, cipher.doFinal(seed))
        }
      }
    }

    /** This seed is encrypted with a key that requires user authentication. */
    class WithAuth(override val iv: ByteArray, override val ciphertext: ByteArray) : V2(KeystoreHelper.KEY_WITH_AUTH) {
      fun decrypt(cipher: Cipher?): ByteArray = tryWith(GeneralSecurityException()) { cipher!!.doFinal(ciphertext) }

      companion object {
        fun encrypt(seed: ByteArray, cipher: Cipher): WithAuth = tryWith(GeneralSecurityException()) {
          WithAuth(cipher.iv, cipher.doFinal(seed))
        }
      }
    }

    fun getDecryptionCipher() = KeystoreHelper.getDecryptionCipher(keyAlias, iv)

    /** Serialize to a V2 ByteArray. */
    override fun serialize(): ByteArray {
      if (iv.size != IV_LENGTH) {
        throw RuntimeException("cannot serialize seed: iv not of the correct length")
      }
      val array = ByteArrayOutputStream()
      array.write(SEED_FILE_VERSION_2.toInt())
      array.write(when (keyAlias) {
        KeystoreHelper.KEY_NO_AUTH -> NO_AUTH_KEY_VERSION
        KeystoreHelper.KEY_WITH_AUTH -> REQUIRED_AUTH_KEY_VERSION
        else -> throw RuntimeException("unhandled key alias=$keyAlias")
      }.toInt())
      array.write(iv)
      array.write(ciphertext)
      return array.toByteArray()
    }

    companion object {
      private const val IV_LENGTH = 16
      private const val NO_AUTH_KEY_VERSION = 1
      private const val REQUIRED_AUTH_KEY_VERSION = 2

      fun deserialize(stream: ByteArrayInputStream): V2 {
        val keyVersion = stream.read()
        val iv = ByteArray(IV_LENGTH)
        stream.read(iv, 0, IV_LENGTH)
        val cipherText = ByteArray(stream.available())
        stream.read(cipherText, 0, stream.available())
        return when (keyVersion) {
          NO_AUTH_KEY_VERSION -> NoAuth(iv, cipherText)
          REQUIRED_AUTH_KEY_VERSION -> WithAuth(iv, cipherText)
          else -> throw RuntimeException("unhandled key version=$keyVersion")
        }
      }
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    const val SEED_FILE_VERSION_1: Byte = 1
    const val SEED_FILE_VERSION_2: Byte = 2

    /** Reads an array of byte and de-serializes it as an [EncryptedSeed] object. */
    fun deserialize(serialized: ByteArray): EncryptedSeed {
      val stream = ByteArrayInputStream(serialized)
      val version = stream.read()
      return when (version) {
        SEED_FILE_VERSION_1.toInt() -> V1.deserialize(stream)
        SEED_FILE_VERSION_2.toInt() -> V2.deserialize(stream)
        else -> throw RuntimeException("unhandled encrypted seed file version")
      }
    }

    /** Migrate a [EncryptedSeed.V1] to an [EncryptedSeed.V2.NoAuth], and return the unencrypted seed if successful. */
    fun migration_v1_v2(context: Context, encryptedSeed: V1, password: String): V2.NoAuth {
      val seed = try {
        encryptedSeed.decrypt(password)
      } catch (e: GeneralSecurityException) {
        if (password == Constants.DEFAULT_PIN) {
          log.debug("failed to migrate v1 seed with default pin, update encryption prefs to prevent inconsistencies")
          Prefs.setIsSeedEncrypted(context, true)
        }
        throw RuntimeException("failed to migrate v1 seed to v2")
      }
      val newEncryptedSeed = V2.NoAuth.encrypt(seed)
      if (!AesCbcWithIntegrity.constantTimeEq(newEncryptedSeed.decrypt(), seed)) {
        log.error("invalid seed when migrating to v2, failing migration")
        throw GeneralSecurityException()
      }
      return newEncryptedSeed
    }

    fun byteArray2String(array: ByteArray): String = String(Hex.decode(array), Charsets.UTF_8)
    fun byteArray2ByteVector(array: ByteArray): ByteVector = byteArray2String(array).run { `ByteVector$`.`MODULE$`.apply(MnemonicCode.toSeed(this, "").toArray()) }
  }
}
