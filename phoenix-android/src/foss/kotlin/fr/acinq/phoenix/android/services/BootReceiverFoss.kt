/*
 * Copyright 2019 ACINQ SAS
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.legacy.internalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * This receiver is started when the device has booted, and schedules background jobs.
 */
class BootReceiverFoss : BroadcastReceiver() {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            ChannelsWatcher.schedule(context)
            InflightPaymentsWatcher.scheduleOnce(context)

            val internalPrefs = (context as? PhoenixApplication)?.internalDataRepository
            val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
            scope.launch {
                internalPrefs?.getBackgroundServiceMode?.first()?.let { (isEnabled, isWakelock) ->
                    if (isEnabled) {
                        val action = if (isWakelock) HeadlessActions.Start.WithWakeLock else HeadlessActions.Start.NoWakeLock
                        log.info("starting foreground service with action=$action")
                        Intent(context, NodeServiceFoss::class.java).apply {
                            this.action = action.name
                            putExtra(NodeService.EXTRA_ORIGIN, NodeService.ORIGIN_HEADLESS)
                        }
                        context.startForegroundService(intent)
                    }
                }
            }
        }
    }
}
