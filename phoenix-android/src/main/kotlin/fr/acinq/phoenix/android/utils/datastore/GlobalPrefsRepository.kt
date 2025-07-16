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

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.random.Random

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
        private val DEFAULT_NODE_ID = stringPreferencesKey("DEFAULT_NODE_ID")
        private val ACTIVE_NODE_ID = stringPreferencesKey("ACTIVE_NODE_ID")
        private val AVAILABLE_WALLETS_META = stringPreferencesKey("AVAILABLE_WALLETS_META")
    }

    /**
     * Lists the metadata of known available wallets. Note that this is just metadata, not the actual wallets data like the seed.
     *
     * Specifically, the Phoenix SeedManager may be managing more or less seeds than this list contain ; if so the SeedManager is
     * always right, that is, we should ignore the data returned by that list if they don't match the SeedManager. This should only
     * happen if there's a syncing problem between the preferences and the seed file containing the map of seeds.
     */
    val getAvailableWalletsMeta: Flow<Map<String, UserWalletMetadata>> = safeData.map {
        it[AVAILABLE_WALLETS_META]?.let { json ->
            try {
                Json.decodeFromString<Map<String, UserWalletMetadata>>(json)
            } catch (e: Exception) {
                log.error("could not deserialize available_wallets=$json: ", e)
                null
            }
        } ?: emptyMap()
    }

    suspend fun saveAvailableWalletMeta(name: String?, nodeId: String) = data.edit {
        val existingMap: Map<String, UserWalletMetadata> = getAvailableWalletsMeta.first()
        val newMap = existingMap + (nodeId to UserWalletMetadata(name = name, nodeId = nodeId, createdAt = existingMap[nodeId]?.createdAt ?: currentTimestampMillis()))
        it[AVAILABLE_WALLETS_META] = Json.encodeToString(newMap)
    }

    val getActiveNodeId: Flow<String?> = safeData.map { it[ACTIVE_NODE_ID] }
    suspend fun saveActiveNodeId(nodeId: String) = data.edit { it[ACTIVE_NODE_ID] = nodeId }

    val getDefaultNodeId: Flow<String?> = safeData.map { it[DEFAULT_NODE_ID] }
    suspend fun saveDefaultNodeId(nodeId: String) = data.edit { it[DEFAULT_NODE_ID] = nodeId }
}

@Serializable
data class UserWalletMetadata(val nodeId: String, val name: String?, val createdAt: Long?) {
    val color: Color by lazy {
        val randomSeed = java.util.UUID.nameUUIDFromBytes(ByteVector.fromHex(nodeId).sha256().toByteArray()).mostSignificantBits
        val random = Random(randomSeed)

        Color.hsv(
            hue = random.nextInt(360).toFloat(),
            saturation = 0.6f + random.nextFloat() * 0.4f,
            value = 0.75f + random.nextFloat() * 0.1f
        )
    }
}
