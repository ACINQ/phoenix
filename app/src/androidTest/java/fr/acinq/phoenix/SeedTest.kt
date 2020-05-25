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

package fr.acinq.phoenix

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.tozny.crypto.android.AesCbcWithIntegrity
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Migration
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.seed.EncryptedSeed
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.*
import kotlin.random.Random


@RunWith(AndroidJUnit4::class)
class SeedTest {

  private val password = "123456"

  @get:Rule
  var temp = TemporaryFolder()

  @Test
  fun basicTest() {
    val salt = AesCbcWithIntegrity.generateSalt()
    val plaintext = "this is not encrypted".toByteArray(charset("UTF-8"))
    val keys = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
    val civ = AesCbcWithIntegrity.encrypt(plaintext, keys)
    val decrypted = AesCbcWithIntegrity.decrypt(civ, keys)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted))
  }

  @Test
  fun writeAndReadSeed() {
    val seed = ByteArray(16)
    SecureRandom().nextBytes(seed)
    val datadir = temp.newFolder(UUID.randomUUID().toString())
    EncryptedSeed.writeSeedToDir(datadir, seed, password)
    val decrypted = EncryptedSeed.readSeedFromDir(datadir, password)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, decrypted))
  }

  @Test(expected = GeneralSecurityException::class)
  fun failReadSeedWrongPassword() {
    val seed = ByteArray(16)
    SecureRandom().nextBytes(seed)
    val datadir = temp.newFolder(UUID.randomUUID().toString())
    EncryptedSeed.writeSeedToDir(datadir, seed, password)
    EncryptedSeed.readSeedFromDir(datadir, "999999")
  }

  @Test
  fun removeDefaultPin() {
    val seed = ByteArray(16)
    SecureRandom().nextBytes(seed)
    val datadir = temp.newFolder(UUID.randomUUID().toString())
    EncryptedSeed.writeSeedToDir(datadir, seed, Constants.DEFAULT_PIN)
    Migration.removeDefaultPinEncryption(datadir)
    val decrypted = EncryptedSeed.readSeedFromDir(datadir, null)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, decrypted))
  }
}
