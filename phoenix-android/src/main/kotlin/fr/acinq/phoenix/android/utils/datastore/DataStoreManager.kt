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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object DataStoreManager {

    // map of: node_id -> userPrefs for that node_id
    private val _userPrefsMapFlow = MutableStateFlow<Map<String, UserPrefsRepository>>(emptyMap())
    val userPrefsMapFlow: StateFlow<Map<String, UserPrefsRepository>> = _userPrefsMapFlow.asStateFlow()

    fun loadPrefsForNodeId(context: Context, nodeId: String): UserPrefsRepository {
        val existingNodeUserPrefs = _userPrefsMapFlow.value[nodeId]
        if (existingNodeUserPrefs != null) return existingNodeUserPrefs

        val newNodeUserPrefs = PreferenceDataStoreFactory.create {
            File(context.filesDir, userPrefsFileName(nodeId))
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
        val file = File(context.filesDir, userPrefsFileName(nodeId))
        val deleted = file.delete()

        if (deleted) {
            unloadPrefsForNodeId(nodeId)
        }

        return deleted
    }

    private fun userPrefsFileName(nodeId: String) = "userprefs_${nodeId}.preferences_pb"
    private fun internalPrefsFileName(nodeId: String) = "internaldata_${nodeId}.preferences_pb"
}