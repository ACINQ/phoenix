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
import android.content.ServiceConnection
import android.os.IBinder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory

/**
 * This worker is executed regularly to settle any possible in-flight payments. This will help prevent
 * timeouts (and channels force-close) on apps that run on devices that cannot be awoken remotely, for
 * example, devices using GrapheneOS where FCM is not supported.
 */
class InflightPaymentsWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun doWork(): Result {
        log.info("starting inflight-payments check job")

        val legacyAppStatus = LegacyPrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first()
        if (legacyAppStatus !is LegacyAppStatus.NotRequired) {
            log.info("aborting inflight-payments check job, legacy_status=${legacyAppStatus.name()}")
            return Result.success()
        }

        return try {
            val serviceFlow = MutableStateFlow<NodeService?>(null)
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(component: ComponentName, bind: IBinder) {
                    log.debug("connected to NodeService")
                    serviceFlow.value = (bind as NodeService.NodeBinder).getService()
                }

                override fun onServiceDisconnected(component: ComponentName) {
                    log.debug("disconnected from NodeService")
                    serviceFlow.value = null
                }
            }

            val service = serviceFlow.filterNotNull().first()
            when (val state = service.state.value) {
                is NodeServiceState.Running -> {
                    log.info("node is already running, abort...")
                }
                is NodeServiceState.Init -> {
                    log.info("node is starting, abort...")
                }
                is NodeServiceState.Error, is NodeServiceState.Off -> {
                    log.info("node service should be started...")
                }
                null, NodeServiceState.Disconnected -> {
                    log.warn("node service is $state, abort...")
                }
            }

            Result.success()
        } catch (e: Exception) {
            log.error("failed to run channels-watcher job: ", e)
            Result.failure()
        }
    }
}