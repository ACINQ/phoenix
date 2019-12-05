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
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec

abstract class EncryptedData internal constructor(val version: Int, val salt: ByteArray?, internal val civ: AesCbcWithIntegrity.CipherTextIvMac) {

  @Throws(IOException::class)
  open fun write(): ByteArray {
    throw UnsupportedOperationException()
  }

  /**
   * Decrypt an encrypted data object with a password and returns a byte array
   *
   * @param password password protecting the data
   * @return a byte array containing the decrypted data
   * @throws GeneralSecurityException if the password is not correct
   */
  @Throws(GeneralSecurityException::class)
  fun decrypt(password: String): ByteArray {
    val sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
    return AesCbcWithIntegrity.decrypt(civ, sk)
  }

  @Throws(GeneralSecurityException::class)
  fun decrypt(key: AesCbcWithIntegrity.SecretKeys): ByteArray {
    return AesCbcWithIntegrity.decrypt(civ, key)
  }

  companion object {

    fun secretKeyFromBinaryKey(key: ByteVector32): AesCbcWithIntegrity.SecretKeys {
      val keyBytes = key.bytes().toArray()
      val confidentialityKeyBytes = ByteArray(16)
      System.arraycopy(keyBytes, 0, confidentialityKeyBytes, 0, 16)
      val integrityKeyBytes = ByteArray(16)
      System.arraycopy(keyBytes, 16, integrityKeyBytes, 0, 16)

      val confidentialityKey = SecretKeySpec(confidentialityKeyBytes, "AES")
      val integrityKey = SecretKeySpec(integrityKeyBytes, "PBKDF2WithHmacSHA1")
      return AesCbcWithIntegrity.SecretKeys(confidentialityKey, integrityKey)
    }
  }
}
