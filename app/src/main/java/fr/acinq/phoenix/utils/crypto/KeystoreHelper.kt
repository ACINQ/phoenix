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

package fr.acinq.phoenix.utils.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.tryWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object KeystoreHelper {

  /** This key does not require the user to be authenticated */
  const val KEY_NO_AUTH = "PHOENIX_KEY_NO_AUTH"

  /** This key requires the user to be authenticated (with schema, pin, fingerprint...) */
  const val KEY_WITH_AUTH = "PHOENIX_KEY_REQUIRE_AUTH"

  private val ENC_ALGO = KeyProperties.KEY_ALGORITHM_AES
  private val ENC_BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
  private val ENC_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7

  private val keyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  }

  private fun getOrCreateKeyNoAuthRequired(): SecretKey {
    keyStore.getKey(KEY_NO_AUTH, null)?.let { return it as SecretKey }
    val spec = KeyGenParameterSpec.Builder(KEY_NO_AUTH, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(ENC_BLOCK_MODE)
      .setEncryptionPaddings(ENC_PADDING)
      .setRandomizedEncryptionRequired(true)
      .setKeySize(256)
      .setUserAuthenticationRequired(false)
      .setInvalidatedByBiometricEnrollment(false)
      .build()
    return KeyGenerator.getInstance(ENC_ALGO, keyStore.provider).run {
      init(spec)
      generateKey()
    }
  }

  private fun getOrCreateKeyWithAuth(): SecretKey {
    keyStore.getKey(KEY_WITH_AUTH, null)?.let { return it as SecretKey }
    val spec = KeyGenParameterSpec.Builder(KEY_WITH_AUTH, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).run {
      setBlockModes(ENC_BLOCK_MODE)
      setEncryptionPaddings(ENC_PADDING)
      setRandomizedEncryptionRequired(true)
      setKeySize(256)
      setUserAuthenticationRequired(true)
      setUserAuthenticationValidityDurationSeconds(-1)
      setInvalidatedByBiometricEnrollment(false)
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        setUnlockedDeviceRequired(true)
        setIsStrongBoxBacked(true)
      }
      build()
    }
    return KeyGenerator.getInstance(ENC_ALGO, keyStore.provider).run {
      init(spec)
      generateKey()
    }
  }

  private fun getKeyForName(keyName: String): SecretKey = when (keyName) {
    KEY_NO_AUTH -> getOrCreateKeyNoAuthRequired()
    KEY_WITH_AUTH -> getOrCreateKeyWithAuth()
    else -> throw IllegalArgumentException("unhandled key=$keyName")
  }

  /** Get encryption Cipher for given key. */
  internal fun getEncryptionCipher(keyName: String): Cipher = Cipher.getInstance("$ENC_ALGO/$ENC_BLOCK_MODE/$ENC_PADDING").run {
    init(Cipher.ENCRYPT_MODE, getKeyForName(keyName))
    this
  }

  /** Get decryption Cipher for given key. */
  internal fun getDecryptionCipher(keyName: String, iv: ByteArray): Cipher =  Cipher.getInstance("$ENC_ALGO/$ENC_BLOCK_MODE/$ENC_PADDING").run {
    init(Cipher.DECRYPT_MODE, getKeyForName(keyName), IvParameterSpec(iv))
    this
  }

  // -- legacy
  private const val PIN_KEY_NAME = "PHOENIX_KEY_PIN"

  private fun getKeyForPin(): SecretKey? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return keyStore.getKey(PIN_KEY_NAME, null) as SecretKey?
  }

  fun decryptPin(context: Context): ByteArray {
    val sk = getKeyForPin() ?: throw RuntimeException("could not retrieve PIN key from keystore")
    val iv = Prefs.getEncryptedPINIV(context) ?: throw java.lang.RuntimeException("pin initialization vector is missing")
    val pin = Prefs.getEncryptedPIN(context) ?: throw java.lang.RuntimeException("encrypted pin is missing")
    val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    cipher.init(Cipher.DECRYPT_MODE, sk, IvParameterSpec(iv))
    return cipher.doFinal(pin)
  }
}
