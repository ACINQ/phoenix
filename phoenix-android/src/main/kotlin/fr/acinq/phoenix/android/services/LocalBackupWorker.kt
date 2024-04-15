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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.backup.LocalBackupHelper
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LocalBackupWorker(val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun doWork(): Result {
        log.info("starting local-backup-worker")

        return try {
            val application = context as PhoenixApplication
            val business = application.business.filterNotNull().first()
            val keyManager = business.walletManager.keyManager.filterNotNull().first()
            LocalBackupHelper.saveBackupToDisk(context, keyManager)
            log.info("successfully saved backup file to disk")
            Result.success()
        } catch (e: Exception) {
            log.error("error when processing local-backup job: ", e)
            Result.failure()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        const val TAG = BuildConfig.APPLICATION_ID + ".LocalBackupWorker"
        const val PERIODIC_TAG = BuildConfig.APPLICATION_ID + ".LocalBackupWorkerPeriodic"

        /** Schedule a local-backup-worker job every day. */
        fun schedulePeriodic(context: Context) {
            log.info("scheduling periodic local-backup-worker")
            val work = PeriodicWorkRequest.Builder(LocalBackupWorker::class.java, 24, TimeUnit.HOURS, 12, TimeUnit.HOURS).addTag(PERIODIC_TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        /** Schedule a local-backup-worker job to run once. Existing schedules are replaced. */
        fun scheduleOnce(context: Context) {
            log.info("scheduling local-backup once")
            val work = OneTimeWorkRequestBuilder<LocalBackupWorker>().setInitialDelay(10.seconds.toJavaDuration()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }

        /** Cancel all scheduled local-backup-worker. */
        fun cancel(context: Context): Operation {
            return WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}