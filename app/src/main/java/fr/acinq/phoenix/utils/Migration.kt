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

package fr.acinq.phoenix.utils

import android.content.Context
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.BuildConfig
import fr.acinq.phoenix.utils.seed.EncryptedSeed
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Created by DPA on 22/05/20.
 */
object Migration {

  private val log = LoggerFactory.getLogger(this::class.java)

  fun doMigration(context: Context) {
    val version = Prefs.getLastVersionUsed(context)
    if (0 < version && version < BuildConfig.VERSION_CODE) {
      log.info("last installed version: $version is behind current version: ${BuildConfig.VERSION_CODE}, starting migration")
      removeDefaultPin(context, version)
      log.info("end of migration")
    } else {
      log.debug("last installed version: $version, no migration needed")
    }
    Prefs.setLastVersionUsed(context, BuildConfig.VERSION_CODE)
  }

  /**
   * ============
   * Version < 11
   * ============
   * Prior to code 11 the seed is always encrypted even when user has not set a pin, using the [Constants.DEFAULT_PIN]
   * default pin code. This does not bring any security benefits and slows down the node startup since decrypting
   * takes time. It should be removed.
   *
   * Note: with version 11, the user can still protect access to the wallet with a PIN/biometric auth,
   * using [Prefs.PREFS_IS_WALLET_PROTECTED_WITH_PIN], without encrypting the seed.
   */
  private fun removeDefaultPin(context: Context, version: Int) {
    if (version < 11) {
      log.info("checking legacy default pin")
      val isSeedEncrypted = Prefs.isSeedEncrypted(context)
      if (isSeedEncrypted) {
        log.info("enabling wallet pin protection")
        Prefs.setIsWalletBehindPin(context, true)
      } else {
        if (!isMigrationDone(context, PREFS_DEFAULT_ENCRYPTION_DONE)) {
          try {
            removeDefaultPinEncryption(Wallet.getDatadir(context))
            Prefs.setIsWalletBehindPin(context, false)
            Prefs.setIsSeedEncrypted(context, false)
            log.info("default pin successfully removed")
          } catch (e: Exception) {
            try {
              // try to read the seed without a pin, in   case the preferences are not consistent with the actual state of the seed
              EncryptedSeed.readSeedFromDir(Wallet.getDatadir(context), null)
              log.info("no encryption, ignore default pin migration")
              Prefs.setIsSeedEncrypted(context, false)
              Prefs.setIsWalletBehindPin(context, false)
            } catch (e: Exception) {
              log.info("failed to use default pin or none: migration ignored")
              Prefs.setIsSeedEncrypted(context, true)
              Prefs.setIsWalletBehindPin(context, true)
            }
          } finally {
            saveMigrationDone(context, PREFS_DEFAULT_ENCRYPTION_DONE)
          }
        } else {
         log.info("legacy default pin migration already done")
        }
      }
    } else {
      log.debug("ignored default pin migration for version=$version")
    }
  }

  fun removeDefaultPinEncryption(datadir: File) {
    val seed = EncryptedSeed.readSeedFromDir(datadir, Constants.DEFAULT_PIN)
    log.info("removing default encryption")
    EncryptedSeed.writeSeedToDir(datadir, seed, null)
  }

  // -- Completed migration are saved in prefs, so that they are not done twice.
  private const val PREFS_DEFAULT_ENCRYPTION_DONE: String = "PREFS_MIGRATION_DEFAULT_ENCRYPTION_DONE"

  private fun isMigrationDone(context: Context, key: String): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false)
  }

  private fun saveMigrationDone(context: Context, key: String) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, true).commit()
  }
}
