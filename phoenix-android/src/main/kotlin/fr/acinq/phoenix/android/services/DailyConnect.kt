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
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
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
import kotlin.time.Duration.Companion.seconds

/**
 * This worker is scheduled to run roughly every day. It simply connects to the LSP, wait for 1 minute,
 * then shuts down. The purpose is to settle pending payments that may have been missed by the
 * [InflightPaymentsWatcher], to complete closings properly, etc...
 */
class DailyConnect(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    val log = LoggerFactory.getLogger(this::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        log.info("starting $name")
        var business: PhoenixBusiness? = null
        var closeDatabases = true

        // add a delay so this job does not start a node too quickly and collide with the UI startup logic
        // (on some devices, background workers only run when the app is started by the user which causes a race to create
        // a business node and can cause db locks)
        // FIXME rework the node service/business logic to guarantee unicity
        delay(20.seconds)

        try {
            val application = (applicationContext as PhoenixApplication)
            val userPrefs = application.userPrefs

            val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext) as? EncryptedSeed.V2.NoAuth ?: run {
                log.error("aborting $name: unhandled seed type")
                return Result.failure()
            }

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
                val stopJobSignal = MutableStateFlow(false)
                var jobWatchingChannels: Job? = null

                val jobMain = launch {
                    business = PhoenixBusiness(PlatformContext(applicationContext))
                    service.filterNotNull().flatMapLatest { it.state.asFlow() }.collect { state ->
                        when (state) {
                            is NodeServiceState.Init, is NodeServiceState.Running, is NodeServiceState.Error, NodeServiceState.Disconnected -> {
                                log.info("aborting $name: node service in state=${state.name}")
                                closeDatabases = false
                                stopJobSignal.value = true
                            }

                            is NodeServiceState.Off -> {
                                // note: we can't simply launch NodeService from the background, either as a background service (disallowed since android 8) or as a
                                // foreground service (disallowed since android 14)
                                log.info("node service in state=${state.name}, starting an isolated business")

                                jobWatchingChannels = launch {
                                    WorkerHelper.startIsolatedBusiness(application, business!!, encryptedSeed, userPrefs)

                                    business?.connectionsManager?.connections?.first { it.global is Connection.ESTABLISHED }
                                    log.debug("connections established")

                                    business?.peerManager?.channelsFlow?.filterNotNull()?.collectIndexed { index, channels ->
                                        when {
                                            channels.isEmpty() -> {
                                                log.info("completing $name: no channels found")
                                                stopJobSignal.value = true
                                            }
                                            else -> {
                                                log.info("${channels.size} channel(s) found, waiting 60s...")
                                                delay(60_000)
                                                log.debug("completing $name after 60s...")
                                                stopJobSignal.value = true
                                            }
                                        }
                                    }
                                }.also {
                                    it.invokeOnCompletion {
                                        log.debug("terminated job-watching-channels (${it?.localizedMessage})")
                                    }
                                }
                            }
                        }
                    }
                }

                stopJobSignal.first { it }
                log.debug("stop-job signal detected")
                jobWatchingChannels?.cancelAndJoin()
                jobMain.cancelAndJoin()
            }
            return Result.success()
        } catch (e: Exception) {
            log.error("error in $name: ", e)
            return Result.failure()
        } finally {
            business?.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
            business?.stop(closeDatabases = closeDatabases)
            log.info("terminated $name")
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

