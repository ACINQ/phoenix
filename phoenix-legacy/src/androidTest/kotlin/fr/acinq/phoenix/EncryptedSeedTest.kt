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
import fr.acinq.phoenix.legacy.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.legacy.utils.crypto.KeystoreHelper
import fr.acinq.phoenix.legacy.utils.crypto.SeedManager
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.*


@RunWith(AndroidJUnit4::class)
class EncryptedSeedTest {

  private val password = "123456"

  @get:Rule
  var temp = TemporaryFolder()

  lateinit var instrumentationContext: Context

  @Before
  fun setup() {
    instrumentationContext = InstrumentationRegistry.getInstrumentation().context
  }

  @Test
  fun v1basicTest() {
    val salt = AesCbcWithIntegrity.generateSalt()
    val plaintext = "this is not encrypted".toByteArray(charset("UTF-8"))
    val keys = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
    val civ = AesCbcWithIntegrity.encrypt(plaintext, keys)
    val decrypted = AesCbcWithIntegrity.decrypt(civ, keys)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted))
  }

  @Test
  fun write_read_v1() {
    val seed = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val datadir = temp.newFolder(UUID.randomUUID().toString())

    SeedManager.writeSeedToDirV1(datadir, seed, password)
    val decrypted = (SeedManager.getSeedFromDir(datadir) as EncryptedSeed.V1).decrypt(password)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, decrypted))
  }

  @Test(expected = GeneralSecurityException::class)
  fun read_v1_wrong_pass() {
    val seed = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val datadir = temp.newFolder(UUID.randomUUID().toString())

    SeedManager.writeSeedToDirV1(datadir, seed, password)
    (SeedManager.getSeedFromDir(datadir) as EncryptedSeed.V1).decrypt("999999")
  }

  @Test
  fun migrate_v1_v2() {
    val seed = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val datadir = temp.newFolder(UUID.randomUUID().toString())

    // 1 - write v1 seed to disk
    SeedManager.writeSeedToDirV1(datadir, seed, password)
    val encryptedSeedv1 = SeedManager.getSeedFromDir(datadir) as EncryptedSeed.V1

    // 2 - do migration
    val encryptedSeedV2 = EncryptedSeed.migration_v1_v2(instrumentationContext, encryptedSeedv1, password)
    SeedManager.writeSeedToDisk(datadir, encryptedSeedV2)

    // 3 - check new seed is the same
    val newSeed = (SeedManager.getSeedFromDir(datadir) as EncryptedSeed.V2.NoAuth).decrypt()
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, newSeed))
  }

  @Test
  fun write_read_v2_no_auth() {
    val seed = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val datadir = temp.newFolder(UUID.randomUUID().toString())

    EncryptedSeed.V2.NoAuth.encrypt(seed).apply {
      SeedManager.writeSeedToDisk(datadir, this)
    }

    (SeedManager.getSeedFromDir(datadir) as EncryptedSeed.V2.NoAuth).apply {
      Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, decrypt()))
    }
  }

  @Test(expected = GeneralSecurityException::class)
  fun encrypt_v2_with_auth_needs_successful_auth() {
    val seed = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val cipher = KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_WITH_AUTH)
    EncryptedSeed.V2.WithAuth.encrypt(seed, cipher)
  }

}
