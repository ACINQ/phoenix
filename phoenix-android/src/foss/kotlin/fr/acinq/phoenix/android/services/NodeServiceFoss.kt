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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper


sealed class HeadlessActions {
    abstract val name: String
    sealed class Start : HeadlessActions() {
        data object WithWakeLock : Start() {
            override val name: String = "START_WITH_WAKELOCK"
        }
        data object NoWakeLock : Start() {
            override val name: String = "START_NO_WAKELOCK"
        }
    }
    data object Stop : HeadlessActions() {
        override val name: String = "STOP"
    }

    companion object {
        fun read(name: String?) : HeadlessActions? {
            return when (name) {
                Start.NoWakeLock.name -> Start.NoWakeLock
                Start.WithWakeLock.name -> Start.WithWakeLock
                Stop.name -> Stop
                else -> null
            }
        }
    }
}

/**
 * Implementation of [NodeService] without Google-based FCM support. Instead, to receive payments in the
 * background, the user must first foreground this service manually and let the app run headless.
 *
 * Uses a [ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING] foreground type.
 *
 * See https://github.com/robertohuertasm/endless-service
 */
class NodeServiceFoss : NodeService() {

    override suspend fun monitorFcmToken(business: PhoenixBusiness) {} // NOOP
    override fun refreshFcmToken() {} // NOOP
    override fun deleteFcmToken() {} // NOOP
    override fun isFcmAvailable(context: Context): Boolean = false

    var wakeLock: PowerManager.WakeLock? = null
        private set

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val origin = intent?.getStringExtra(EXTRA_ORIGIN)
        when (val action = HeadlessActions.read(intent?.action)) {
            is HeadlessActions.Start -> {
                log.info("--- start headless mode ---")
                val (notif, serviceType) = when (state.value) {
                    is NodeServiceState.Off -> {
                        try {
                            val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
                            val seed = encryptedSeed!!.decrypt()
                            log.debug("successfully decrypted seed in the background, starting wallet...")
                            startBusiness(seed, requestCheckLegacyChannels = false)
                            SystemNotificationHelper.getHeadlessNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                        } catch (e: Exception) {
                            log.error("failed to decrypt seed: {}", e.localizedMessage)
                            SystemNotificationHelper.getHeadlessFailureNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                        }
                    }
                    else -> {
                        SystemNotificationHelper.getHeadlessNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                    }
                }
                isHeadless = true
                startForeground(notif, serviceType)
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (action is HeadlessActions.Start.WithWakeLock) {
                    log.info("--- acquire wakelock ---")
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoenixService::lock")
                        .apply { acquire() }
                }
                return START_STICKY
            }
            is HeadlessActions.Stop -> {
                log.info("--- stop headless mode ---")
                isHeadless = false
                if (wakeLock?.isHeld == true) {
                    log.info("--- release wakelock ---")
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                return START_NOT_STICKY
            }
            else -> {
                log.info("--- unhandled start-command [ intent=${intent?.action} origin=$origin in state=${state.value} ] ---")
                return START_NOT_STICKY
            }
        }
    }

}