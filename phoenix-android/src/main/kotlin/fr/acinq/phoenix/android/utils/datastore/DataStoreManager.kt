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

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.globalPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

object DataStoreManager {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + supervisor)

    // maps of: (wallet_id -> userPrefs) and (wallet_id -> internalPrefs)
    private val _userPrefsMapFlow = MutableStateFlow<Map<WalletId, UserPrefs>>(emptyMap())
    private val _internalPrefsMapFlow = MutableStateFlow<Map<WalletId, InternalPrefs>>(emptyMap())

    fun loadUserPrefsForWallet(context: Context, walletId: WalletId): UserPrefs {
        val existingUserPrefs = _userPrefsMapFlow.value[walletId]
        if (existingUserPrefs != null) return existingUserPrefs

        val newUserPrefs = PreferenceDataStoreFactory.create {
            userPrefsFile(context, walletId)
        }.let { UserPrefs(it) }

        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap[walletId] = newUserPrefs
        _userPrefsMapFlow.value = newUserPrefsMap

        return newUserPrefs
    }

    fun loadInternalPrefsForWallet(context: Context, walletId: WalletId): InternalPrefs {
        val existingInternalPrefs = _internalPrefsMapFlow.value[walletId]
        if (existingInternalPrefs != null) return existingInternalPrefs

        val newInternalPrefs = PreferenceDataStoreFactory.create {
            internalPrefsFile(context, walletId)
        }.let { InternalPrefs(it) }

        val newInternalPrefsMap = _internalPrefsMapFlow.value.toMutableMap()
        newInternalPrefsMap[walletId] = newInternalPrefs
        _internalPrefsMapFlow.value = newInternalPrefsMap

        return newInternalPrefs
    }

    /** Deletes the preferences files for a given wallet id, and removes the preferences from the map flow. */
    fun deleteNodeUserPrefs(context: Context, id: WalletId): Boolean {
        val userPrefsFile = userPrefsFile(context, id)
        val userPrefsFileDeleted = userPrefsFile.delete()

        val internalPrefsFile = internalPrefsFile(context, id)
        val internalPrefsFileDeleted = internalPrefsFile.delete()

        return userPrefsFileDeleted && internalPrefsFileDeleted
    }

    fun migratePrefsForWallet(context: Context, id: WalletId) {
        try {
            val userPrefsOldFile = context.dataStoreFile("userprefs.preferences_pb")
            if (userPrefsOldFile.exists()) {
                log.info("migrating prefs: ${userPrefsOldFile.name}")
                val userPrefsNewFile = userPrefsFile(context, id)
                userPrefsOldFile.copyTo(userPrefsNewFile, overwrite = true)
                userPrefsOldFile.delete()
            }

            val internalPrefsOldFile = context.dataStoreFile("internaldata.preferences_pb")
            if (internalPrefsOldFile.exists()) {
                // some internal prefs need to be moved to the global prefs
                runBlocking {
                    GlobalPrefs(context.globalPrefs).saveShowIntro(false)
                }
                log.info("migrating prefs: ${internalPrefsOldFile.name}")
                BusinessManager.refreshFcmToken()
                val internalPrefsNewFile = internalPrefsFile(context, id)
                internalPrefsOldFile.copyTo(internalPrefsNewFile, overwrite = true)
                internalPrefsOldFile.delete()
            }
        } catch (e: Exception) {
            log.error("error when migrating prefs for wallet=$id")
        }
    }

    private fun userPrefsFile(context: Context, id: WalletId): File = context.dataStoreFile("userprefs_${id.nodeIdHash}.preferences_pb")
    private fun internalPrefsFile(context: Context, id: WalletId): File = context.dataStoreFile("internalprefs_${id.nodeIdHash}.preferences_pb")
}