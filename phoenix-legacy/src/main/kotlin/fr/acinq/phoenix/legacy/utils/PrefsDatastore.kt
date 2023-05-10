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
import fr.acinq.phoenix.legacy.internalData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException


object PrefsDatastore {
  private val log = LoggerFactory.getLogger(this::class.java)

  private fun prefs(context: Context): Flow<Preferences> {
    return context.internalData.data.catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }
  }

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
    context.internalData.edit { it[LEGACY_APP_STATUS] = value.name() }
  }

  private val json = Json { ignoreUnknownKeys = true }
  private val MIGRATION_RESULT = stringPreferencesKey("MIGRATION_RESULT")
  fun getMigrationResult(context: Context): Flow<MigrationResult?> = prefs(context).map { it[MIGRATION_RESULT]?.let { json.decodeFromString(MigrationResult.serializer(), it) } }
  suspend fun saveMigrationResult(context: Context, result: MigrationResult) = context.internalData.edit {
    it[MIGRATION_RESULT] = json.encodeToString(result)
  }

  private val DATA_MIGRATION_EXPECTED = booleanPreferencesKey("DATA_MIGRATION_EXPECTED")
  fun getDataMigrationExpected(context: Context): Flow<Boolean> = prefs(context).map { it[DATA_MIGRATION_EXPECTED] ?: false }
  suspend fun saveDataMigrationExpected(context: Context, isExpected: Boolean) = context.internalData.edit {
    it[DATA_MIGRATION_EXPECTED] = isExpected
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

@Serializable
data class MigrationResult(val newNodeId: String, val legacyNodeId: String, val address: String)
