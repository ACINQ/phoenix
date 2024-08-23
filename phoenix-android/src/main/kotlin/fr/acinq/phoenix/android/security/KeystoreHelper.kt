/*
 * Copyright 2020 ACINQ SAS
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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object KeystoreHelper {

  private val log: Logger = LoggerFactory.getLogger(this::class.java)

  /** The alias of the key used to encrypt an [EncryptedSeed.V2] seed. */
  const val KEY_NO_AUTH = "PHOENIX_KEY_NO_AUTH"

  /** The alias of the key used to encrypt a [EncryptedPin.V1] PIN. */
  const val KEY_FOR_PINCODE_V1 = "PHOENIX_KEY_FOR_PINCODE_V1"

  private val ENC_ALGO = KeyProperties.KEY_ALGORITHM_AES
  private val ENC_BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
  private val ENC_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7

  private val keyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  }

  private fun getOrCreateKeyNoAuthRequired(): SecretKey {
    keyStore.getKey(KEY_NO_AUTH, null)?.let { return it as SecretKey }
    val spec = KeyGenParameterSpec.Builder(KEY_NO_AUTH, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).apply {
      setBlockModes(ENC_BLOCK_MODE)
      setEncryptionPaddings(ENC_PADDING)
      setRandomizedEncryptionRequired(true)
      setKeySize(256)
      setUserAuthenticationRequired(false)
      setInvalidatedByBiometricEnrollment(false)
    }
    return generateKeyWithSpec(spec)
  }

  /** Generate key from key gen specs. If possible, store the key in strongbox. */
  private fun generateKeyWithSpec(spec: KeyGenParameterSpec.Builder): SecretKey {
    val keygen = KeyGenerator.getInstance(ENC_ALGO, keyStore.provider)
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      // try to use strongbox on Android 9+, which is only supported by a few devices
      try {
        spec.setIsStrongBoxBacked(true)
        keygen.init(spec.build())
        keygen.generateKey()
      } catch (e: Exception) {
        log.warn("failed to generate key with strongbox enabled: ${e.javaClass.simpleName}: ${e.localizedMessage}, trying again without strongbox")
        spec.setIsStrongBoxBacked(false)
        keygen.init(spec.build())
        keygen.generateKey()
      }
    } else {
      keygen.init(spec.build())
      keygen.generateKey()
    }
  }

  private fun getKeyForName(keyName: String): SecretKey = when (keyName) {
    KEY_NO_AUTH -> getOrCreateKeyNoAuthRequired()
    KEY_FOR_PINCODE_V1 -> getOrCreateKeyNoAuthRequired()
    else -> throw IllegalArgumentException("unhandled key=$keyName")
  }

  /** Get encryption Cipher for given key. */
  internal fun getEncryptionCipher(keyName: String): Cipher = Cipher.getInstance("$ENC_ALGO/$ENC_BLOCK_MODE/$ENC_PADDING").apply {
    init(Cipher.ENCRYPT_MODE, getKeyForName(keyName), parameters)
  }

  /** Get decryption Cipher for given key. */
  internal fun getDecryptionCipher(keyName: String, iv: ByteArray): Cipher = Cipher.getInstance("$ENC_ALGO/$ENC_BLOCK_MODE/$ENC_PADDING").apply {
    init(Cipher.DECRYPT_MODE, getKeyForName(keyName), IvParameterSpec(iv))
  }
}
