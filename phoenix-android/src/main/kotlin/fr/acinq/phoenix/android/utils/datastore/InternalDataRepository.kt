/*
 * Copyright 2023 ACINQ SAS
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.services.ChannelsWatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

class InternalDataRepository(private val internalData: DataStore<Preferences>) {
    private companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private val IS_SCREEN_LOCKED = booleanPreferencesKey("is_screen_locked")
        private val LAST_REJECTED_ONCHAIN_SWAP_AMOUNT = longPreferencesKey("LAST_REJECTED_ONCHAIN_SWAP_AMOUNT")
        private val LAST_REJECTED_ONCHAIN_SWAP_TIMESTAMP = longPreferencesKey("LAST_REJECTED_ONCHAIN_SWAP_TIMESTAMP")
        private val SEED_MANUAL_BACKUP_DONE = booleanPreferencesKey("SEED_MANUAL_BACKUP_DONE")
        private val LAST_USED_APP_CODE = intPreferencesKey("LAST_USED_APP_CODE")
        private val SEED_LOSS_DISCLAIMER_READ = booleanPreferencesKey("SEED_LOSS_DISCLAIMER_READ")
        private val LEGACY_MIGRATION_ADDRESS_WARNING_SHOWN = booleanPreferencesKey("LEGACY_MIGRATION_ADDRESS_WARNING_SHOWN")
        private val LEGACY_MIGRATION_MESSSAGE_SHOWN = booleanPreferencesKey("LEGACY_MIGRATION_MESSSAGE_SHOWN")
        private val SHOW_INTRO = booleanPreferencesKey("SHOW_INTRO")
        private val FCM_TOKEN = stringPreferencesKey("FCM_TOKEN")
        private val CHANNELS_WATCHER_OUTCOME = stringPreferencesKey("CHANNELS_WATCHER_RESULT")
        private val LAST_USED_SWAP_INDEX = intPreferencesKey("LAST_USED_SWAP_INDEX")
        private val INFLIGHT_PAYMENTS_COUNT = intPreferencesKey("INFLIGHT_PAYMENTS_COUNT")
        private val SHOW_SPLICEOUT_CAPACITY_DISCLAIMER = booleanPreferencesKey("SHOW_SPLICEOUT_CAPACITY_DISCLAIMER")
        private val REMOTE_WALLET_NOTICE_READ_INDEX = intPreferencesKey("REMOTE_WALLET_NOTICE_READ_INDEX")
        private val BIP_353_ADDRESS = stringPreferencesKey("BIP_353_ADDRESS")

        private val SHOW_RELEASE_NOTES_SINCE = intPreferencesKey("SHOW_RELEASE_NOTES_SINCE")
    }

    val log = LoggerFactory.getLogger(this::class.java)

    /** Retrieve data stored in [internalData], with a fallback to empty data if prefs file can't be read. */
    private val safeData: Flow<Preferences> = internalData.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    suspend fun clear() = internalData.edit { it.clear() }

    val isScreenLocked: Flow<Boolean> = safeData.map { it[IS_SCREEN_LOCKED] ?: false }
    suspend fun saveIsScreenLocked(isLocked: Boolean) {
        internalData.edit { it[IS_SCREEN_LOCKED] = isLocked }
    }

    /** Returns the Firebase Cloud Messaging token. */
    val getFcmToken: Flow<String?> = safeData.map { it[FCM_TOKEN] }
    suspend fun saveFcmToken(token: String) = internalData.edit { it[FCM_TOKEN] = token }

    /** Returns the build code of the last Phoenix instance that has been run on the device. Used for migration purposes. */
    val getLastUsedAppCode: Flow<Int?> = safeData.map { it[LAST_USED_APP_CODE] }
    suspend fun saveLastUsedAppCode(code: Int) = internalData.edit { it[LAST_USED_APP_CODE] = code }

    /** For some versions, we want to show a release note when opening the Home screen. This preference tracks from which code notes should be shown. If null, show nothing. */
    val showReleaseNoteSinceCode: Flow<Int?> = safeData.map { it[SHOW_RELEASE_NOTES_SINCE] }
    suspend fun saveShowReleaseNoteSinceCode(code: Int?) = internalData.edit {
        if (code == null) it.remove(SHOW_RELEASE_NOTES_SINCE) else it[SHOW_RELEASE_NOTES_SINCE] = code
    }

    /** True when the user states that he made a manual backup of the seed. */
    val isManualSeedBackupDone: Flow<Boolean> = safeData.map { it[SEED_MANUAL_BACKUP_DONE] ?: false }
    suspend fun saveManualSeedBackupDone(isDone: Boolean) = internalData.edit { it[SEED_MANUAL_BACKUP_DONE] = isDone }

    /** True if the user has read the seed loss disclaimer. */
    val isSeedLossDisclaimerRead: Flow<Boolean> = safeData.map { it[SEED_LOSS_DISCLAIMER_READ] ?: false }
    suspend fun saveSeedLossDisclaimerRead(isRead: Boolean) = internalData.edit { it[SEED_LOSS_DISCLAIMER_READ] = isRead }

    /** True if a seed backup warning should be displayed - computed from SEED_MANUAL_BACKUP_DONE & SEED_LOSS_DISCLAIMER_READ. */
    val showSeedBackupNotice = safeData.map { it[SEED_MANUAL_BACKUP_DONE] != true || it[SEED_LOSS_DISCLAIMER_READ] != true }

    /** True if a migration notice has already be shown. */
    val getLegacyMigrationMessageShown: Flow<Boolean> = safeData.map { it[LEGACY_MIGRATION_MESSSAGE_SHOWN] ?: false }
    suspend fun saveLegacyMigrationMessageShown(isShown: Boolean) = internalData.edit { it[LEGACY_MIGRATION_MESSSAGE_SHOWN] = isShown }

    /** True if a dialog about the swap-in address change following the migration has been shown. */
    val getLegacyMigrationAddressWarningShown: Flow<Boolean> = safeData.map { it[LEGACY_MIGRATION_ADDRESS_WARNING_SHOWN] ?: false }
    suspend fun saveLegacyMigrationAddressWarningShown(isShown: Boolean) = internalData.edit { it[LEGACY_MIGRATION_ADDRESS_WARNING_SHOWN] = isShown }

    /** True if the intro screen must be shown. True by default. */
    val getShowIntro: Flow<Boolean> = safeData.map { it[SHOW_INTRO] ?: true }
    suspend fun saveShowIntro(showIntro: Boolean) = internalData.edit { it[SHOW_INTRO] = showIntro }

    /** Returns the last hannels-watcher job result. */
    val getChannelsWatcherOutcome: Flow<ChannelsWatcher.Outcome?> = safeData.map {
        it[CHANNELS_WATCHER_OUTCOME]?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    suspend fun saveChannelsWatcherOutcome(outcome: ChannelsWatcher.Outcome) = internalData.edit {
        it[CHANNELS_WATCHER_OUTCOME] = json.encodeToString(outcome)
    }

    /** Return (amount, timestamp) of the last rejected swap-in. Prevent spamming user with duplicate notifications for the same on-chain deposit. */
    val getLastRejectedOnchainSwap: Flow<Pair<MilliSatoshi, Long>?> = safeData.map {
        val amount = it[LAST_REJECTED_ONCHAIN_SWAP_AMOUNT]
        val timestamp = it[LAST_REJECTED_ONCHAIN_SWAP_TIMESTAMP]
        if (amount != null && timestamp != null) amount.msat to timestamp else null
    }
    suspend fun saveLastRejectedOnchainSwap(liquidityEvent: LiquidityEvents.Rejected) = internalData.edit {
        it[LAST_REJECTED_ONCHAIN_SWAP_AMOUNT] = liquidityEvent.amount.msat
        it[LAST_REJECTED_ONCHAIN_SWAP_TIMESTAMP] = currentTimestampMillis()
    }

    val getLastUsedSwapIndex: Flow<Int> = safeData.map { it[LAST_USED_SWAP_INDEX] ?: 0 }
    suspend fun saveLastUsedSwapIndex(index: Int) = internalData.edit { it[LAST_USED_SWAP_INDEX] = index }

    val getInFlightPaymentsCount: Flow<Int> = safeData.map { it[INFLIGHT_PAYMENTS_COUNT] ?: 0 }
    suspend fun saveInFlightPaymentsCount(count: Int) = internalData.edit { it[INFLIGHT_PAYMENTS_COUNT] = count }

    val getSpliceoutCapacityDisclaimer: Flow<Boolean> = safeData.map { it[SHOW_SPLICEOUT_CAPACITY_DISCLAIMER] ?: true }
    suspend fun saveSpliceoutCapacityDisclaimer(show: Boolean) = internalData.edit { it[SHOW_SPLICEOUT_CAPACITY_DISCLAIMER] = show }

    val getLastReadWalletNoticeIndex: Flow<Int> = safeData.map { it[REMOTE_WALLET_NOTICE_READ_INDEX] ?: -1 }
    suspend fun saveLastReadWalletNoticeIndex(index: Int) = internalData.edit { it[REMOTE_WALLET_NOTICE_READ_INDEX] = index }

    val getBip353Address: Flow<String> = safeData.map { it[BIP_353_ADDRESS] ?: "" }
    suspend fun saveBip353Address(address: String) = internalData.edit { it[BIP_353_ADDRESS] = address }
}