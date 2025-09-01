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
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.security.SeedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * This worker is scheduled to run roughly every day. It simply connects to the LSP, wait for 1 minute,
 * then shuts down. The purpose is to settle pending payments that may have been missed by the
 * [InflightPaymentsWatcher], to complete closings properly, etc...
 */
class DailyConnect(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun doWork(): Result {
        log.info("starting $name")

        if (BusinessManager.businessFlow.first().isNotEmpty()) {
            log.info("there already are active businesses, aborting $name")
            return Result.success()
        }

        val userWallets = SeedManager.loadAndDecryptOrNull(applicationContext)
        if (userWallets.isNullOrEmpty()) {
            log.info("could not load any seed, aborting $name")
            return Result.success()
        }

        try {
            val businessMap = userWallets.map { (walletId, wallet) ->
                val res = BusinessManager.startNewBusiness(words = wallet.words, isHeadless = true)
                if (res is StartBusinessResult.Failure) {
                    log.info("failed to start business for wallet=$walletId")
                    return Result.success()
                }

                val business = BusinessManager.businessFlow.value[walletId]
                if (business == null) {
                    log.info("failed to access business for wallet=$walletId")
                    return Result.success()
                }

                walletId to business
            }.toMap()

            withContext(Dispatchers.Default) {
                val stopJobSignal = MutableStateFlow(false)

                val watchers = businessMap.map { (walletId, business) ->
                    launch {
                        business.appConnectionsDaemon?.forceReconnect()
                        business.connectionsManager.connections.first { it.global is Connection.ESTABLISHED }
                        log.debug("connections established for wallet={}", walletId)

                        business.peerManager.channelsFlow.filterNotNull().collect { channels ->
                            when {
                                channels.isEmpty() -> {
                                    log.info("no channels found for wallet=$walletId")
                                    stopJobSignal.value = true
                                }
                                else -> {
                                    log.info("${channels.size} channel(s) found for wallet=$walletId, waiting 60s...")
                                    delay(60_000)
                                    stopJobSignal.value = true
                                }
                            }
                        }
                    }.also {
                        it.invokeOnCompletion { log.debug("completed watching-channels job for wallet={} ({})", walletId, it?.localizedMessage) }
                    }
                }
                stopJobSignal.first { it }
                log.debug("stop-job signal detected")
                watchers.forEach { it.cancelAndJoin() }
            }

            return Result.success()

        } catch (e: Exception) {
            log.error("error in $name: ", e)
            return Result.failure()
        } finally {
            if (BusinessManager.isHeadless.first()) {
                BusinessManager.stopAllBusinesses()
            }
            log.info("finished $name")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        private val name = "daily-connect-job"
        const val TAG = BuildConfig.APPLICATION_ID + ".DailyConnect"

        /** Schedule [DailyConnect] to run roughly every day. */
        fun schedule(context: Context) {
            log.info("scheduling $name")
            val work = PeriodicWorkRequest.Builder(DailyConnect::class.java, 36, TimeUnit.HOURS, 12, TimeUnit.HOURS).addTag(TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        fun scheduleASAP(context: Context) {
            log.info("scheduling $name once")
            val work = OneTimeWorkRequest.Builder(DailyConnect::class.java).addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }

        /** Cancel all scheduled in-flight payments worker. */
        fun cancel(context: Context): Operation {
            return WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}

