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
import fr.acinq.phoenix.legacy.internalData
import fr.acinq.phoenix.legacy.userPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException

object InternalData {
    private val log = LoggerFactory.getLogger(this::class.java)

    // -- Firebase Cloud Messaging token.
    private val FCM_TOKEN = stringPreferencesKey("FCM_TOKEN")
    fun getFcmToken(context: Context): Flow<String?> = prefs(context).map { it[FCM_TOKEN] }
    suspend fun saveFcmToken(context: Context, token: String) = context.internalData.edit { it[FCM_TOKEN] = token }

    // -- Build code of the last Phoenix instance that has been run on the device. Used for migration purposes.
    private val LAST_USED_APP_CODE = intPreferencesKey("LAST_USED_APP_CODE")
    fun getLastUsedAppCode(context: Context): Flow<Int?> = prefs(context).map { it[LAST_USED_APP_CODE] }
    suspend fun saveLastUsedAppCode(context: Context, code: Int) = context.internalData.edit { it[LAST_USED_APP_CODE] = code }

    // -- Timestamp of the last time the user has checked his mnemonics. Used to display notifications and messages.
    private val MNEMONICS_CHECK_TIMESTAMP = longPreferencesKey("MNEMONICS_CHECK_TIMESTAMP")
    fun getMnemonicsCheckTimestamp(context: Context): Flow<Long?> = prefs(context).map { it[MNEMONICS_CHECK_TIMESTAMP] }
    suspend fun saveMnemonicsCheckTimestamp(context: Context, timestamp: Long) = context.internalData.edit { it[MNEMONICS_CHECK_TIMESTAMP] = timestamp }

    // -- Preferences from the legacy app must be migrated when needed.
    private val IS_LEGACY_PREFS_MIGRATION_DONE = booleanPreferencesKey("IS_LEGACY_PREFS_MIGRATION_DONE")
    fun getIsLegacyPrefsMigrationDone(context: Context): Flow<Boolean> = prefs(context).map { it[IS_LEGACY_PREFS_MIGRATION_DONE] ?: false }
    suspend fun saveIsLegacyPrefsMigrationDone(context: Context, isDone: Boolean) = context.internalData.edit { it[IS_LEGACY_PREFS_MIGRATION_DONE] = isDone }



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