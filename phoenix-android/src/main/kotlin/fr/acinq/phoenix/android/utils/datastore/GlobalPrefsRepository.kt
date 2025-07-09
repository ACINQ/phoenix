/*
 * Copyright 2025 ACINQ SAS
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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.acinq.bitcoin.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException

class GlobalPrefsRepository(private val data: DataStore<Preferences>) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** Retrieve preferences from [data], with a fallback to empty prefs if the data file can't be read. */
    private val safeData: Flow<Preferences> = data.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    suspend fun clear() = data.edit { it.clear() }

    private companion object {
        // tracks which wallet the app should try to load first, may be null
        private val EXPECTED_NODE_ID_ON_START = stringPreferencesKey("EXPECTED_NODE_ID_ON_START")
    }


    val getExpectedNodeIdOnStart: Flow<PublicKey?> = safeData.map {
        it[EXPECTED_NODE_ID_ON_START]?.let { id ->
            try {
                PublicKey.fromHex(id)
            } catch (e: Exception) {
                log.error("failed to read EXPECTED_NODE_ID_ON_START=$id: ", e.message)
                null
            }
        }
    }
    suspend fun saveExpectedNodeIdOnStart(nodeId: PublicKey) = data.edit { it[EXPECTED_NODE_ID_ON_START] = nodeId.toHex() }
}