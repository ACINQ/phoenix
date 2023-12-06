/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.legacy.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import fr.acinq.phoenix.legacy.legacyPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException


object LegacyPrefsDatastore {
  private val log = LoggerFactory.getLogger(this::class.java)

  private fun prefs(context: Context): Flow<Preferences> {
    return context.legacyPrefs.data.catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }
  }

  suspend fun clear(context: Context) = context.legacyPrefs.edit { it.clear() }

  private val LEGACY_APP_STATUS = stringPreferencesKey("LEGACY_APP_STATUS")
  fun getLegacyAppStatus(context: Context): Flow<LegacyAppStatus> = prefs(context).map {
    when (it[LEGACY_APP_STATUS]) {
      LegacyAppStatus.Required.Expected.name() -> LegacyAppStatus.Required.Expected
      LegacyAppStatus.Required.InitStart.name() -> LegacyAppStatus.Required.InitStart
      LegacyAppStatus.Required.Running.name() -> LegacyAppStatus.Required.Running
      LegacyAppStatus.Required.Interrupted.name() -> LegacyAppStatus.Required.Interrupted
      LegacyAppStatus.NotRequired.name() -> LegacyAppStatus.NotRequired
      else -> LegacyAppStatus.Unknown
    }
  }
  suspend fun saveStartLegacyApp(context: Context, value: LegacyAppStatus) {
    context.legacyPrefs.edit { it[LEGACY_APP_STATUS] = value.name() }
  }

  private val HAS_MIGRATED_FROM_LEGACY = booleanPreferencesKey("HAS_MIGRATED_FROM_LEGACY")
  fun hasMigratedFromLegacy(context: Context): Flow<Boolean> = prefs(context).map { it[HAS_MIGRATED_FROM_LEGACY] ?: false }
  suspend fun saveHasMigratedFromLegacy(context: Context, hasMigrated: Boolean) = context.legacyPrefs.edit {
    it[HAS_MIGRATED_FROM_LEGACY] = hasMigrated
  }

  private val LEGACY_DATA_MIGRATION_EXPECTED = booleanPreferencesKey("LEGACY_DATA_MIGRATION_EXPECTED")
  fun getDataMigrationExpected(context: Context): Flow<Boolean?> = prefs(context).map { it[LEGACY_DATA_MIGRATION_EXPECTED] }
  suspend fun saveDataMigrationExpected(context: Context, isExpected: Boolean) = context.legacyPrefs.edit {
    it[LEGACY_DATA_MIGRATION_EXPECTED] = isExpected
  }

  private val LEGACY_PREFS_MIGRATION_EXPECTED = booleanPreferencesKey("LEGACY_PREFS_MIGRATION_EXPECTED")
  fun getPrefsMigrationExpected(context: Context): Flow<Boolean?> = prefs(context).map { it[LEGACY_PREFS_MIGRATION_EXPECTED] }
  suspend fun savePrefsMigrationExpected(context: Context, isExpected: Boolean) = context.legacyPrefs.edit {
    it[LEGACY_PREFS_MIGRATION_EXPECTED] = isExpected
  }

  /** List of transaction ids that can be used for swap-in, even if zero conf. Used for migration. */
  private val MIGRATION_TRUSTED_SWAP_IN_TXS = stringSetPreferencesKey("MIGRATION_TRUSTED_SWAP_IN_TXS")
  fun getMigrationTrustedSwapInTxs(context: Context): Flow<Set<String>> = prefs(context).map { it[MIGRATION_TRUSTED_SWAP_IN_TXS] ?: emptySet() }
  suspend fun saveMigrationTrustedSwapInTxs(context: Context, txs: Set<String>) = context.legacyPrefs.edit {
    it[MIGRATION_TRUSTED_SWAP_IN_TXS] = txs
  }
}

/**
 * Describes the status of the legacy app.
 *
 * If [NotRequired] we can safely skip the legacy app and stick to lightning-kmp.
 * If [Unknown] we should start lightning-kmp and request additional information from the peer.
 *
 * If [Required] the legacy app must be started, with the following possible statuses for UI/UX convenience:
 *   - [Required.Expected]: the legacy app should be started ASAP by the KMP app;
 *   - [Required.InitStart]: the legacy app is being started: we should be transitioning from the KMP app to the legacy app;
 *   - [Required.Running]: the legacy app is running, the KMP app should be in the background;
 *   - [Required.Interrupted]: some error occurred and the migration process has failed, we should stay in the legacy app;
 */
sealed class LegacyAppStatus {
  object Unknown: LegacyAppStatus()
  sealed class Required: LegacyAppStatus() {
    object Expected: Required()
    object InitStart: Required()
    object Running: Required()
    object Interrupted: Required()
  }
  object NotRequired: LegacyAppStatus()

  fun name() = javaClass.canonicalName ?: javaClass.simpleName
}
