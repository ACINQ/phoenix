/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.components.contact.ContactsPhotoHelper
import fr.acinq.phoenix.android.security.SeedManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Clean up unused photo files for contacts. */
class ContactsPhotoCleaner(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun doWork(): Result {
        log.info("starting $name")
        try {

            // get the active business map, or create it no business is active
            val businessMap = BusinessRepo.businessFlow.value.takeIf { it.isNotEmpty() }
                ?: run {
                    val seedMap = SeedManager.loadAndDecryptAll(applicationContext)
                    if (seedMap.isNullOrEmpty()) {
                        log.info("no seeds available, terminating $name")
                        return Result.success()
                    }

                    seedMap.map { (nodeId, words) ->
                        val res = BusinessRepo.startNewBusiness(words = words, isHeadless = true)
                        if (res is StartBusinessResult.Failure) {
                            log.info("failed to start business for node_id=$nodeId")
                            return Result.failure()
                        }

                        val business = BusinessRepo.businessFlow.value[nodeId]
                        if (business == null) {
                            log.info("failed to access business for node_id=$nodeId")
                            return Result.success()
                        }

                        nodeId to business
                    }.toMap()
                }

            val contactsPhotoDir = ContactsPhotoHelper.contactsDir(applicationContext)
            val toDelete = businessMap.map { (_, business) ->
                val contacts = business.databaseManager.contactsList.filterNotNull().first()
                val contactsPhotoNames = contactsPhotoDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
                contactsPhotoNames.subtract(contacts.map { it.photoUri }.toSet()).filterNotNull()
            }.flatten()

            log.debug("info ${toDelete.size} unused photo file(s)")
            toDelete.forEach { imageName ->
                File(contactsPhotoDir, imageName).takeIf { it.exists() && it.isFile && it.canWrite() }?.delete()
            }

            return Result.success()
        } catch (e: Exception) {
            log.error("error in $name: ", e)
            return Result.failure()
        } finally {
            log.debug("finished $name")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        private const val name = "contacts-photo-cleaner"
        private const val TAG = BuildConfig.APPLICATION_ID + ".ContactPhotoCleaner"

        /** Schedule [ContactsPhotoCleaner] to run roughly every 2 weeks. */
        fun schedule(context: Context) {
            val work = PeriodicWorkRequest.Builder(ContactsPhotoCleaner::class.java, 15, TimeUnit.DAYS, 3, TimeUnit.DAYS).addTag(TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        fun scheduleASAP(context: Context) {
            log.info("scheduling $name once")
            val work = OneTimeWorkRequest.Builder(ContactsPhotoCleaner::class.java).addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }
    }
}

