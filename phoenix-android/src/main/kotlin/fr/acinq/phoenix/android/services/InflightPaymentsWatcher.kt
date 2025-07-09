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
import fr.acinq.lightning.channel.states.Syncing
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.data.inFlightPaymentsCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * This worker starts a node to settle any pending in-flight payments. This will prevent payment timeouts
 * (and channels force-close) in case the app is not started regularly and the silent push notifications
 * sent by the ACINQ peer are ignored by the device.
 *
 * Example: devices using GrapheneOS, where FCM is not supported.
 *
 * This service is scheduled whenever there's a pending htlc in a channel.
 * See [LocalChannelInfo.inFlightPaymentsCount].
 */
class InflightPaymentsWatcher(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    val log = LoggerFactory.getLogger(this::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        log.info("starting $name")

        try {
            val application = (applicationContext as PhoenixApplication)
            val internalData = application.internalDataRepository
            val inFlightPaymentsCount = internalData.getInFlightPaymentsCount.first()

            if (inFlightPaymentsCount == 0) {
                log.info("aborting $name: expecting NO in-flight payments")
                return Result.success()
            }

            if (BusinessRepo.businessFlow.first().isNotEmpty()) {
                log.info("there already are active businesses, aborting $name")
                return Result.success()
            }

            val seedMap = SeedManager.loadAndDecryptAll(applicationContext)
            if (seedMap.isNullOrEmpty()) {
                log.info("could not load any seed, aborting $name")
                return Result.success()
            }

            val businessMap = seedMap.map { (nodeId, words) ->
                val res = BusinessRepo.startNewBusiness(words = words, isHeadless = true)
                if (res is StartBusinessResult.Failure) {
                    log.info("failed to start business for node_id=$nodeId")
                    return Result.success()
                }

                val business = BusinessRepo.businessFlow.value[nodeId]
                if (business == null) {
                    log.info("failed to access business for node_id=$nodeId")
                    return Result.success()
                }

                nodeId to business
            }.toMap()

            withContext(Dispatchers.Default) {
                val stopJobs = MutableStateFlow(false)

                val jobTimer = launch {
                    delay(2.minutes)
                    log.info("stopping $name after 2 minutes without resolution - show notification")
                    scheduleOnce(applicationContext)
                    SystemNotificationHelper.notifyInFlightHtlc(applicationContext)
                    stopJobs.value = true
                }

                val watchers = businessMap.map { (nodeId, business) ->
                    launch {
                        business.appConnectionsDaemon?.forceReconnect()
                        business.connectionsManager.connections.first { it.global is Connection.ESTABLISHED }
                        log.debug("watching in-flight payments for node_id=$nodeId")

                        business.peerManager.channelsFlow.filterNotNull().collectIndexed { index, channels ->
                            val paymentsCount = channels.inFlightPaymentsCount()
                            internalData.saveInFlightPaymentsCount(paymentsCount)
                            when {
                                channels.isEmpty() -> {
                                    log.info("no channels found, successfully terminating watcher (#$index)")
                                    stopJobs.value = true
                                }

                                channels.any { it.value.state is Syncing } -> {
                                    log.debug("channels syncing, pausing 10s before next check (#$index)")
                                    delay(10.seconds)
                                }

                                paymentsCount > 0 -> {
                                    log.debug("$paymentsCount payments in-flight, pausing 5s before next check (#$index)...")
                                    delay(5.seconds)
                                }

                                else -> {
                                    log.info("$paymentsCount payments in-flight, successfully completing worker (#$index)...")
                                    stopJobs.value = true
                                }
                            }
                        }
                    }
                }

                stopJobs.first { it }
                log.debug("stop-job signal detected")
                watchers.forEach { it.cancelAndJoin() }
                jobTimer.cancelAndJoin()
            }
            return Result.success()
        } catch (e: Exception) {
            log.error("error in $name: ", e)
            return Result.failure()
        } finally {
            if (BusinessRepo.isHeadless.first()) {
                BusinessRepo.stopAllBusinesses()
            }
            log.info("finished $name...")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        const val name = "inflight-payments-watcher"
        const val TAG = BuildConfig.APPLICATION_ID + ".InflightPaymentsWatcher"

        /** Schedule a in-flight payments watcher job to start every few hours. */
        fun schedulePeriodic(context: Context) {
            log.info("scheduling periodic $name")
            val work = PeriodicWorkRequest.Builder(InflightPaymentsWatcher::class.java, 2, TimeUnit.HOURS, 3, TimeUnit.HOURS).addTag(TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        /** Schedule an in-flight payments job to run once in [delay] from now (by default, 2 hours). Existing schedules are replaced. */
        fun scheduleOnce(context: Context, delay: Duration = 2.hours) {
            log.info("scheduling $name in $delay from now")
            val work = OneTimeWorkRequestBuilder<InflightPaymentsWatcher>().setInitialDelay(delay.toJavaDuration()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }

        /** Cancel all scheduled in-flight payments worker. */
        fun cancel(context: Context): Operation {
            return WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}

