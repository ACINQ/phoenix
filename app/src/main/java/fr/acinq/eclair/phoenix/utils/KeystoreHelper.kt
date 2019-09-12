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

package fr.acinq.eclair.phoenix.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object KeystoreHelper {

  val log: Logger = LoggerFactory.getLogger(this::class.java)
  private const val PIN_KEY_NAME = "PHOENIX_KEY_PIN"

  public fun generateKeyForPin() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    keyGenerator.init(KeyGenParameterSpec.Builder(PIN_KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
      .setUserAuthenticationRequired(true)
      .setUserAuthenticationValidityDurationSeconds(10)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
      .build())
    keyGenerator.generateKey()
  }

  public fun getAllAliases(): ArrayList<String> {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return Collections.list(keyStore.aliases())
  }

  private fun getKeyForPin(): SecretKey? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return keyStore.getKey(PIN_KEY_NAME, null) as SecretKey?
  }

  fun deleteKeyForPin() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    keyStore.deleteEntry(PIN_KEY_NAME)
  }

  fun encryptPin(context: Context, pin: String) {
    getKeyForPin()?.let { sk ->
      val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
      cipher.init(Cipher.ENCRYPT_MODE, sk)
      val iv = cipher.iv
      log.info("encrypt pin with iv=$iv")
      Prefs.saveEncryptedPINIV(context, iv)
      val encrypted = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
      Prefs.saveEncryptedPIN(context, encrypted)
    } ?: throw RuntimeException("no key found!")
  }

  fun decryptPin(context: Context): ByteArray? {
    val sk = getKeyForPin() ?: throw RuntimeException("could not retrieve PIN key from keystore")
    val iv = Prefs.getEncryptedPINIV(context) ?: throw java.lang.RuntimeException("pin initialization vector is missing")
    val pin = Prefs.getEncryptedPIN(context) ?: throw java.lang.RuntimeException("encrypted pin is missing")
    log.info("decrypt pin with iv=$iv")
    val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    cipher.init(Cipher.DECRYPT_MODE, sk, IvParameterSpec(iv))
    return cipher.doFinal(pin)
  }
}
