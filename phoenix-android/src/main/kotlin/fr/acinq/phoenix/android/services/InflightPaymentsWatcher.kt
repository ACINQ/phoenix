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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.channel.states.Syncing
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.utils.MnemonicLanguage
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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
        log.info("starting in-flight-payments watcher")
        var business: PhoenixBusiness? = null
        var closeDatabases = true

        try {

            val application = (applicationContext as PhoenixApplication)
            val internalData = application.internalDataRepository
            val userPrefs = application.userPrefs
            val inFlightPaymentsCount = internalData.getInFlightPaymentsCount.first()

            if (inFlightPaymentsCount == 0) {
                log.info("aborting $name: expecting NO in-flight payments")
                return Result.success()
            } else {

                // check various preferences -- this job may abort early
                val legacyAppStatus = LegacyPrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first()
                if (legacyAppStatus !is LegacyAppStatus.NotRequired) {
                    log.warn("aborting $name: legacy_status=${legacyAppStatus.name()}")
                    return Result.success()
                }

                if (LegacyPrefsDatastore.getPrefsMigrationExpected(applicationContext).first() == true) {
                    log.warn("aborting $name: legacy data migration is required")
                    return Result.failure()
                }

                val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext) as? EncryptedSeed.V2.NoAuth ?: run {
                    log.error("aborting $name: unhandled seed type")
                    return Result.failure()
                }

                log.info("expecting $inFlightPaymentsCount in-flight payments, binding to service and starting process...")

                // connect to [NodeService] to monitor the state of the main app business
                val service = MutableStateFlow<NodeService?>(null)
                val serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(component: ComponentName, bind: IBinder) {
                        service.value = (bind as NodeService.NodeBinder).getService()
                    }

                    override fun onServiceDisconnected(component: ComponentName) {
                        service.value = null
                    }
                }
                Intent(applicationContext, NodeService::class.java).let { intent ->
                    applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }

                // Start the monitoring process. If the main app starts, we interrupt this job to prevent concurrent access.
                withContext(Dispatchers.Default) {
                    business = PhoenixBusiness(PlatformContext(applicationContext))
                    val stopJobs = MutableStateFlow(false)
                    var jobChannelsWatcher: Job? = null

                    val jobStateWatcher = launch {
                        service.filterNotNull().flatMapLatest { it.state.asFlow() }.collect { state ->
                            when (state) {
                                is NodeServiceState.Init, is NodeServiceState.Running, is NodeServiceState.Error, NodeServiceState.Disconnected -> {
                                    log.info("interrupting $name: node service in state=${state.name}")
                                    closeDatabases = false
                                    stopJobs.value = true
                                    scheduleOnce(applicationContext)
                                }

                                is NodeServiceState.Off -> {
                                    // note: we can't simply launch NodeService, either as a background service (disallowed since android 8) or as a
                                    // foreground service (disallowed since android 14)
                                    log.info("node service in state=${state.name}, starting an isolated business")

                                    jobChannelsWatcher = launch {
                                        WorkerHelper.startIsolatedBusiness(application, business!!, encryptedSeed, userPrefs)

                                        business?.connectionsManager?.connections?.first { it.global is Connection.ESTABLISHED }
                                        log.debug("connections established, watching channels for in-flight payments...")

                                        business?.peerManager?.channelsFlow?.filterNotNull()?.collectIndexed { index, channels ->
                                            val paymentsCount = channels.inFlightPaymentsCount()
                                            internalData.saveInFlightPaymentsCount(paymentsCount)
                                            when {
                                                channels.isEmpty() -> {
                                                    log.info("no channels found, successfully terminating watcher (#$index)")
                                                    stopJobs.value = true
                                                }

                                                channels.any { it.value.state is Syncing } -> {
                                                    log.debug("channels syncing, pausing 10s before next check (#$index)")
                                                    delay(10_000)
                                                }

                                                paymentsCount > 0 -> {
                                                    log.debug("$paymentsCount payments in-flight, pausing 5s before next check (#$index)...")
                                                    delay(5_000)
                                                }

                                                else -> {
                                                    log.info("$paymentsCount payments in-flight, successfully completing worker (#$index)...")
                                                    stopJobs.value = true
                                                }
                                            }
                                        }
                                    }.also {
                                        it.invokeOnCompletion {
                                            log.debug("terminated job watching channels (${it?.localizedMessage})")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val jobTimer = launch {
                        delay(120_000)
                        log.info("stopping $name after 2 minutes without resolution - show notification")
                        scheduleOnce(applicationContext)
                        SystemNotificationHelper.notifyInFlightHtlc(applicationContext)
                        stopJobs.value = true
                    }

                    stopJobs.first { it }
                    log.debug("stop-job signal detected")
                    jobChannelsWatcher?.cancelAndJoin()
                    jobStateWatcher.cancelAndJoin()
                    jobTimer.cancelAndJoin()
                }
                return Result.success()
            }
        } catch (e: Exception) {
            log.error("error in $name: ", e)
            return Result.failure()
        } finally {
            business?.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
            business?.stop(closeDatabases = closeDatabases)
            log.info("terminated $name...")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        val name = "inflight-payments-watcher"
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

