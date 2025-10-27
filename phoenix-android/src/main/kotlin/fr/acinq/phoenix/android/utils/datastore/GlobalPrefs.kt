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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.BaseWalletId
import fr.acinq.phoenix.android.EmptyWalletId
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

class GlobalPrefs(private val data: DataStore<Preferences>) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val jsonFormat = Json { ignoreUnknownKeys = true }

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
        private val DEFAULT_WALLET_ID = stringPreferencesKey("DEFAULT_WALLET_ID")
        private val AVAILABLE_WALLETS_META = stringPreferencesKey("AVAILABLE_WALLETS_META")

        // the FCM token is global for the application and is shared across node_ids
        private val FCM_TOKEN = stringPreferencesKey("FCM_TOKEN")

        private val SHOW_INTRO = booleanPreferencesKey("SHOW_INTRO")
        private val LAST_USED_APP_CODE = intPreferencesKey("LAST_USED_APP_CODE")
        private val SHOW_RELEASE_NOTES_SINCE = intPreferencesKey("SHOW_RELEASE_NOTES_SINCE")
    }

    /**
     * Lists the metadata of known available wallets. Note that this is just metadata, not the actual wallets data like the seed.
     *
     * Specifically, the Phoenix SeedManager may be managing more or less seeds than this list contain ; if so the SeedManager is
     * always right, that is, we should ignore the data returned by that list if they don't match the SeedManager. This should only
     * happen if there's a syncing problem between the preferences and the seed file containing the map of seeds.
     */
    val getAvailableWalletsMeta: Flow<Map<WalletId, UserWalletMetadata>> = safeData.map {
        it[AVAILABLE_WALLETS_META]?.let { json ->
            try {
                jsonFormat.decodeFromString<Map<String, UserWalletMetadata>>(json).map { WalletId(it.key) to it.value }.toMap()
            } catch (e: Exception) {
                log.error("could not deserialize available_wallets=$json: ", e)
                null
            }
        } ?: emptyMap()
    }

    // use this when you know there's not metadata for this wallet id yet in the preference
    suspend fun saveAvailableWalletMeta(metadata: UserWalletMetadata) = data.edit {
        val existingMap: Map<WalletId, UserWalletMetadata> = getAvailableWalletsMeta.first()
        val newMap = existingMap + (metadata.walletId to metadata)
        it[AVAILABLE_WALLETS_META] = jsonFormat.encodeToString(newMap.map { it.key.nodeIdHash to it.value }.toMap())
    }
    suspend fun saveAvailableWalletMeta(walletId: WalletId, name: String?, avatar: String, isHidden: Boolean) = data.edit {
        val existingMap: Map<WalletId, UserWalletMetadata> = getAvailableWalletsMeta.first()
        val newMap = existingMap + (walletId to UserWalletMetadata(
            walletId = walletId,
            name = name,
            avatar = avatar,
            createdAt = existingMap[walletId]?.createdAt ?: currentTimestampMillis(),
            isHidden = isHidden,
        ))
        it[AVAILABLE_WALLETS_META] = jsonFormat.encodeToString(newMap.map { it.key.nodeIdHash to it.value }.toMap())
    }

    val getDefaultWallet: Flow<BaseWalletId> = safeData.map { it[DEFAULT_WALLET_ID]?.let { WalletId(it) } ?: EmptyWalletId }
    suspend fun saveDefaultWallet(walletId: WalletId) = data.edit { it[DEFAULT_WALLET_ID] = walletId.nodeIdHash }
    suspend fun clearDefaultWallet() = data.edit { it.remove(DEFAULT_WALLET_ID) }

    /** Returns the Firebase Cloud Messaging token. */
    val getFcmToken: Flow<String?> = safeData.map { it[FCM_TOKEN] }
    suspend fun saveFcmToken(token: String) = data.edit { it[FCM_TOKEN] = token }

    /** True if the intro screen must be shown. True by default. */
    val getShowIntro: Flow<Boolean> = safeData.map { it[SHOW_INTRO] ?: true }
    suspend fun saveShowIntro(showIntro: Boolean) = data.edit { it[SHOW_INTRO] = showIntro }

    /** Returns the build code of the last Phoenix instance that has been run on the device. Used for migration purposes. */
    val getLastUsedAppCode: Flow<Int?> = safeData.map { it[LAST_USED_APP_CODE] }
    suspend fun saveLastUsedAppCode(code: Int) = data.edit { it[LAST_USED_APP_CODE] = code }

    /** For some versions, we want to show a release note when opening the Home screen. This preference tracks from which code notes should be shown. If null, show nothing. */
    val showReleaseNoteSinceCode: Flow<Int?> = safeData.map { it[SHOW_RELEASE_NOTES_SINCE] }
    suspend fun saveShowReleaseNoteSinceCode(code: Int?) = data.edit {
        if (code == null) it.remove(SHOW_RELEASE_NOTES_SINCE) else it[SHOW_RELEASE_NOTES_SINCE] = code
    }
}

@Serializable
data class UserWalletMetadata(val walletId: WalletId, val name: String?, val avatar: String, val createdAt: Long?, val isHidden: Boolean) {
    @Composable
    fun nameOrDefault() = name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.wallet_name_default)
}

/** Helper method that finds the wallet metadata matching the node id in the map, or returns a default value if absent. */
fun Map<WalletId, UserWalletMetadata>.getByWalletIdOrDefault(walletId: WalletId): UserWalletMetadata = this[walletId] ?: UserWalletMetadata(walletId = walletId, name = null, avatar = "", createdAt = null, isHidden = false)
