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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.BuildConfig
import org.slf4j.LoggerFactory

object Migration {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** Apply migration scripts when needed. */
  fun doMigration(context: Context) {
    val version = Prefs.getLastVersionUsed(context)
    if (0 < version && version < BuildConfig.VERSION_CODE) {
      log.info("last installed version: $version is behind current version: ${BuildConfig.VERSION_CODE}, starting migration")
      log.info("end of migration")
    } else {
      log.debug("last installed version: $version, no migration needed")
    }
    Prefs.setLastVersionUsed(context, BuildConfig.VERSION_CODE)
  }

  // fun applyMigration_XX(context: Context, fromVersion: Int) {
  //   ...
  //   MigrationPrefs.saveMigrationDone(context, fromVersion, XX)
  // }
}

object MigrationPrefs {
  private const val FILE_NAME = "migration_prefs"
  private const val MIGRATION_CODE = "MIGRATION"
  private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

  private fun getMigrationKey(fromVersion: Int, versionMigrated: Int) = "${MIGRATION_CODE}_FROM_${fromVersion}_COMPLETED_${versionMigrated}"

  fun isMigrationDone(context: Context, key: String): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false)
  }

  @SuppressLint("ApplySharedPref")
  fun saveMigrationDone(context: Context, fromVersion: Int, migrationVersionDone: Int) {
    getPrefs(context).edit().putBoolean(getMigrationKey(fromVersion, migrationVersionDone), true).commit()
  }
}
