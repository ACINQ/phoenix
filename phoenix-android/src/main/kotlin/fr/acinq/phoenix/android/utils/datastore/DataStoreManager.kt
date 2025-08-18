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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.io.File

object DataStoreManager {

    val log = LoggerFactory.getLogger(this::class.java)

    // map of: node_id -> userPrefs for that node_id
    private val _userPrefsMapFlow = MutableStateFlow<Map<String, UserPrefsRepository>>(emptyMap())
    val userPrefsMapFlow: StateFlow<Map<String, UserPrefsRepository>> = _userPrefsMapFlow.asStateFlow()

    fun loadPrefsForNodeId(context: Context, nodeId: String): UserPrefsRepository {
        val existingNodeUserPrefs = _userPrefsMapFlow.value[nodeId]
        if (existingNodeUserPrefs != null) return existingNodeUserPrefs

        val newNodeUserPrefs = PreferenceDataStoreFactory.create {
            userPrefsFile(context, nodeId)
        }.let { UserPrefsRepository(it) }

        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap[nodeId] = newNodeUserPrefs
        _userPrefsMapFlow.value = newUserPrefsMap

        return newNodeUserPrefs
    }

    fun unloadPrefsForNodeId(nodeId: String) {
        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap.remove(nodeId)
        _userPrefsMapFlow.value = newUserPrefsMap
    }

    /**
     * Deletes a user's DataStore file and removes it from the map.
     */
    fun deleteNodeUserPrefs(context: Context, nodeId: String): Boolean {
        val file = userPrefsFile(context, nodeId)
        val deleted = file.delete()

        if (deleted) {
            unloadPrefsForNodeId(nodeId)
        }

        return deleted
    }

    fun migratePrefsNodeId(context: Context, nodeId: String) {
        try {
            val oldFile = context.dataStoreFile("userprefs.preferences_pb")
            if (oldFile.exists()) {
                log.info("migrating prefs: ${oldFile.name}")
                val newFile = userPrefsFile(context, nodeId)
                oldFile.copyTo(newFile, overwrite = true)
                oldFile.delete()
            }
        } catch (e: Exception) {
            log.error("error when migrating prefs for node_id=$nodeId")
        }
    }

    private fun userPrefsFile(context: Context, nodeId: String): File = context.dataStoreFile("userprefs_${PublicKey.fromHex(nodeId).hash160().byteVector().toHex()}.preferences_pb")
    private fun internalPrefsFile(context: Context, nodeId: String): File = context.dataStoreFile("internaldata_${PublicKey.fromHex(nodeId).hash160().byteVector().toHex()}.preferences_pb")
}