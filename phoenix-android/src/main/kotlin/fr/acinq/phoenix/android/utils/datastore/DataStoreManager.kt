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
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.android.BusinessManager
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

    // map of: node_id -> userPrefs for that node_id
    // note that the UI relies on [AppViewModel.activeWalletInUI] to track the currently active user preferences
    private val _userPrefsMapFlow = MutableStateFlow<Map<String, UserPrefs>>(emptyMap())
    private val _internalPrefsMapFlow = MutableStateFlow<Map<String, InternalPrefs>>(emptyMap())

    fun loadUserPrefsForNodeId(context: Context, nodeId: String): UserPrefs {
        val existingUserPrefs = _userPrefsMapFlow.value[nodeId]
        if (existingUserPrefs != null) return existingUserPrefs

        val newUserPrefs = PreferenceDataStoreFactory.create {
            userPrefsFile(context, nodeId)
        }.let { UserPrefs(it) }

        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap[nodeId] = newUserPrefs
        _userPrefsMapFlow.value = newUserPrefsMap

        return newUserPrefs
    }

    fun loadInternalPrefsForNodeId(context: Context, nodeId: String): InternalPrefs {
        val existingInternalPrefs = _internalPrefsMapFlow.value[nodeId]
        if (existingInternalPrefs != null) return existingInternalPrefs

        val newInternalPrefs = PreferenceDataStoreFactory.create {
            internalPrefsFile(context, nodeId)
        }.let { InternalPrefs(it) }

        val newInternalPrefsMap = _internalPrefsMapFlow.value.toMutableMap()
        newInternalPrefsMap[nodeId] = newInternalPrefs
        _internalPrefsMapFlow.value = newInternalPrefsMap

        return newInternalPrefs
    }

    private fun unloadUserPrefs(nodeId: String) {
        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap.remove(nodeId)
        _userPrefsMapFlow.value = newUserPrefsMap
    }

    private fun unloadInternalPrefs(nodeId: String) {
        val newInternalPrefsMap = _internalPrefsMapFlow.value.toMutableMap()
        newInternalPrefsMap.remove(nodeId)
        _internalPrefsMapFlow.value = newInternalPrefsMap
    }

    /** Deletes the preferences files for a given node id, and removes the preferences from the map flow. */
    fun deleteNodeUserPrefs(context: Context, nodeId: String): Boolean {
        val userPrefsFile = userPrefsFile(context, nodeId)
        val userPrefsFileDeleted = userPrefsFile.delete()

        if (userPrefsFileDeleted) {
            unloadUserPrefs(nodeId)
        }

        val internalPrefsFile = internalPrefsFile(context, nodeId)
        val internalPrefsFileDeleted = internalPrefsFile.delete()

        if (internalPrefsFileDeleted) {
            unloadInternalPrefs(nodeId)
        }

        return userPrefsFileDeleted && internalPrefsFileDeleted
    }

    fun migratePrefsNodeId(context: Context, nodeId: String) {
        try {
            val userPrefsOldFile = context.dataStoreFile("userprefs.preferences_pb")
            if (userPrefsOldFile.exists()) {
                log.info("migrating prefs: ${userPrefsOldFile.name}")
                val userPrefsNewFile = userPrefsFile(context, nodeId)
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
                val internalPrefsNewFile = internalPrefsFile(context, nodeId)
                internalPrefsOldFile.copyTo(internalPrefsNewFile, overwrite = true)
                internalPrefsOldFile.delete()
            }
        } catch (e: Exception) {
            log.error("error when migrating prefs for node_id=$nodeId")
        }
    }

    private fun userPrefsFile(context: Context, nodeId: String): File = context.dataStoreFile("userprefs_${PublicKey.fromHex(nodeId).hash160().byteVector().toHex()}.preferences_pb")
    private fun internalPrefsFile(context: Context, nodeId: String): File = context.dataStoreFile("internalprefs_${PublicKey.fromHex(nodeId).hash160().byteVector().toHex()}.preferences_pb")
}