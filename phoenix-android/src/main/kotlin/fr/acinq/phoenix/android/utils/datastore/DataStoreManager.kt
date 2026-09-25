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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Owns the per-wallet [UserPrefs] and [InternalPrefs] datastore instances.
 *
 * DataStore forbids two live instances backed by the same file: the second one throws
 * IllegalStateException the first time its flow is collected -- lazily, on a datastore-internal
 * coroutine, so the crash cannot be caught and its stack trace points nowhere useful.
 *
 * Looking up an instance and creating it must therefore happen atomically. These accessors are
 * called from both the UI and the WorkManager watchers, which do run concurrently. Building a
 * datastore does no I/O, so the critical section is cheap and safe to enter from the main thread.
 */
object DataStoreManager {

    private val log = LoggerFactory.getLogger(this::class.java)

    // maps of: (wallet_id -> userPrefs) and (wallet_id -> internalPrefs)
    private val userPrefsMap = mutableMapOf<WalletId, UserPrefs>()
    private val internalPrefsMap = mutableMapOf<WalletId, InternalPrefs>()

    /** Parent of every per-wallet job; cancelling it would tear down all prefs datastores. */
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)

    /**
     * Map of per-wallet jobs; helps us take down datastores for a specific wallet, instead of having to cancel the [supervisor] which
     * would affect datastores for all other wallets. See [deleteWalletPrefs].
     */
    private val prefsDatastoreJobs = mutableMapOf<WalletId, Job>()

    /** Must only be called while holding the @synchronized object monitor. */
    private fun getPrefsScopeForWallet(id: WalletId): CoroutineScope {
        val job = prefsDatastoreJobs.getOrPut(id) { SupervisorJob(supervisor) }
        return CoroutineScope(scope.coroutineContext + job)
    }

    @Synchronized
    fun loadUserPrefsForWallet(context: Context, walletId: WalletId): UserPrefs {
        return userPrefsMap.getOrPut(walletId) {
            log.debug("creating user prefs datastore for wallet={}", walletId)
            PreferenceDataStoreFactory.create(scope = getPrefsScopeForWallet(walletId)) {
                userPrefsFile(context, walletId)
            }.let { UserPrefs(it) }
        }
    }

    @Synchronized
    fun loadInternalPrefsForWallet(context: Context, walletId: WalletId): InternalPrefs {
        return internalPrefsMap.getOrPut(walletId) {
            log.debug("creating internal prefs datastore for wallet={}", walletId)
            PreferenceDataStoreFactory.create(scope = getPrefsScopeForWallet(walletId)) {
                internalPrefsFile(context, walletId)
            }.let { InternalPrefs(it) }
        }
    }

    /**
     * Closes the datastores for [id] and drops them from the caches, then deletes the underlying files.
     * Note that order matters. First close, then delete files; otherwise the datastore may be resurrected later.
     */
    suspend fun deleteWalletPrefs(context: Context, id: WalletId): Boolean {
        val removedWalletJob = synchronized(this) {
            userPrefsMap.remove(id)
            internalPrefsMap.remove(id)
            prefsDatastoreJobs.remove(id) // returns the job
        }
        if (removedWalletJob != null) {
            log.debug("closing prefs datastores for wallet={}", id)
            removedWalletJob.cancelAndJoin() // cancelling the job tears down the datastores
        }

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