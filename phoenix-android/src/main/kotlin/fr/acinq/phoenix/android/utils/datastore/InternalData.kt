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

package fr.acinq.phoenix.android.utils.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import fr.acinq.phoenix.android.service.ChannelsWatcher
import fr.acinq.phoenix.legacy.internalData
import fr.acinq.phoenix.legacy.utils.Prefs as LegacyPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

object InternalData {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // -- Firebase Cloud Messaging token.
    private val FCM_TOKEN = stringPreferencesKey("FCM_TOKEN")
    fun getFcmToken(context: Context): Flow<String?> = prefs(context).map { it[FCM_TOKEN] }
    suspend fun saveFcmToken(context: Context, token: String) = context.internalData.edit { it[FCM_TOKEN] = token }

    // -- Build code of the last Phoenix instance that has been run on the device. Used for migration purposes.
    private val LAST_USED_APP_CODE = intPreferencesKey("LAST_USED_APP_CODE")
    fun getLastUsedAppCode(context: Context): Flow<Int?> = prefs(context).map { it[LAST_USED_APP_CODE] }
    suspend fun saveLastUsedAppCode(context: Context, code: Int) = context.internalData.edit { it[LAST_USED_APP_CODE] = code }

    // -- When the user states that he made a manual backup of the seed
    private val SEED_MANUAL_BACKUP_DONE = booleanPreferencesKey("SEED_MANUAL_BACKUP_DONE")
    fun isManualSeedBackupDone(context: Context): Flow<Boolean> = prefs(context).map { it[SEED_MANUAL_BACKUP_DONE] ?: false }
    suspend fun saveManualSeedBackupDone(context: Context, isDone: Boolean) = context.internalData.edit { it[SEED_MANUAL_BACKUP_DONE] = isDone }

    // -- When the user has read the seed loss disclaimer
    private val SEED_LOSS_DISCLAIMER_READ = booleanPreferencesKey("SEED_LOSS_DISCLAIMER_READ")
    fun isSeedLossDisclaimerRead(context: Context): Flow<Boolean> = prefs(context).map { it[SEED_LOSS_DISCLAIMER_READ] ?: false }
    suspend fun saveSeedLossDisclaimerRead(context: Context, isRead: Boolean) = context.internalData.edit { it[SEED_LOSS_DISCLAIMER_READ] = isRead }

    // -- Whether a seed backup warning should be displayed - computed from SEED_MANUAL_BACKUP_DONE & SEED_LOSS_DISCLAIMER_READ
    fun showSeedBackupNotice(context: Context) = prefs(context).map { it[SEED_MANUAL_BACKUP_DONE] != true || it[SEED_LOSS_DISCLAIMER_READ] != true }

    // -- Show a message summing up the migration result.
    private val MIGRATION_RESULT_SHOWN = booleanPreferencesKey("MIGRATION_RESULT_SHOWN")
    fun getMigrationResultShown(context: Context): Flow<Boolean> = prefs(context).map { it[MIGRATION_RESULT_SHOWN] ?: false }
    suspend fun saveMigrationResultShown(context: Context, isShown: Boolean) = context.internalData.edit { it[MIGRATION_RESULT_SHOWN] = isShown }

    // -- Show introduction screen at startup. True by default.
    private val SHOW_INTRO = booleanPreferencesKey("SHOW_INTRO")
    fun getShowIntro(context: Context): Flow<Boolean> = prefs(context).map { it[SHOW_INTRO] ?: LegacyPrefs.showFTUE(context) }
    suspend fun saveShowIntro(context: Context, showIntro: Boolean) = context.internalData.edit { it[SHOW_INTRO] = showIntro }

    // -- Channels-watcher job result
    private val CHANNELS_WATCHER_OUTCOME = stringPreferencesKey("CHANNELS_WATCHER_RESULT")
    fun getChannelsWatcherOutcome(context: Context): Flow<ChannelsWatcher.Outcome?> = prefs(context).map {
        it[CHANNELS_WATCHER_OUTCOME]?.let { json.decodeFromString(it) }
    }
    suspend fun saveChannelsWatcherOutcome(context: Context, outcome: ChannelsWatcher.Outcome) = context.internalData.edit {
        it[CHANNELS_WATCHER_OUTCOME] = json.encodeToString(outcome)
    }

    private fun prefs(context: Context): Flow<Preferences> {
        return context.internalData.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
    }
}