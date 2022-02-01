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

  /**
   * If [StartLegacyAppEnum.REQUIRED] we run the legacy app.
   * If [StartLegacyAppEnum.NOT_REQUIRED] we run the modern KMP app.
   * If [StartLegacyAppEnum.UNKNOWN] we run the modern KMP app and query the peer for information.
   */
  private val START_LEGACY_APP = stringPreferencesKey("START_LEGACY_APP")
  fun getStartLegacyApp(context: Context): Flow<StartLegacyAppEnum> = prefs(context).map {
    StartLegacyAppEnum.safeValueOf(it[START_LEGACY_APP])
  }
  suspend fun saveStartLegacyApp(context: Context, value: StartLegacyAppEnum) = context.internalData.edit { it[START_LEGACY_APP] = value.name }
}

enum class StartLegacyAppEnum {
  UNKNOWN, REQUIRED, NOT_REQUIRED;

  companion object {
    fun safeValueOf(value: String?) = when (value) {
      REQUIRED.name -> REQUIRED
      NOT_REQUIRED.name -> NOT_REQUIRED
      else -> UNKNOWN
    }
  }
}
