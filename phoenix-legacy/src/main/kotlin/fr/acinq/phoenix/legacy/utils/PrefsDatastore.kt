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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import fr.acinq.phoenix.legacy.datastore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException


object PrefsDatastore {
  private val log = LoggerFactory.getLogger(this::class.java)


  private fun prefs(context: Context): Flow<Preferences> {
    return context.datastore.data.catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }
  }

  val PREFS_SKIP_LEGACY_CHECK = booleanPreferencesKey("PREFS_SKIP_LEGACY_CHECK")

  fun getSkipLegacyCheck(context: Context): Flow<Boolean> = prefs(context).map { it[PREFS_SKIP_LEGACY_CHECK] ?: false }
  suspend fun saveSkipLegacyCheck(context: Context, value: Boolean) = context.datastore.edit { it[PREFS_SKIP_LEGACY_CHECK] = value }
}
