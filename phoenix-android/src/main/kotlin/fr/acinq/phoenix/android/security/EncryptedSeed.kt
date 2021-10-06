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

import fr.acinq.phoenix.android.utils.tryWith
import fr.acinq.secp256k1.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey

sealed class EncryptedSeed {

  /** Serializes an encrypted seed as a byte array. */
  abstract fun serialize(): ByteArray

  fun name(): String = javaClass.canonicalName ?: javaClass.simpleName

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
        else -> throw UnhandledEncryptionKeyAlias(keyAlias)
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
          else -> throw UnhandledEncryptionKeyVersion(keyVersion)
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
        SEED_FILE_VERSION_2.toInt() -> V2.deserialize(stream)
        else -> throw UnhandledSeedVersion(SEED_FILE_VERSION_1.toInt())
      }
    }

    fun toMnemonics(array: ByteArray): List<String> = String(Hex.decode(String(array, Charsets.UTF_8)), Charsets.UTF_8).split(" ")
    fun fromMnemonics(words: List<String>): ByteArray = Hex.encode(words.joinToString(" ").encodeToByteArray()).encodeToByteArray()
  }

  class UnhandledEncryptionKeyVersion(key: Int): RuntimeException("unhandled encryption key version=$key")
  class UnhandledEncryptionKeyAlias(alias: String): RuntimeException("unhandled encryption key alias=$alias")
  class UnhandledSeedVersion(version: Int): RuntimeException("unhandled encrypted seed version=$version")
}
