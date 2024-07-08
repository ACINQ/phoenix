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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class NodeServiceGoogle : NodeService() {

    override fun onBind(intent: Intent?): IBinder {
        isHeadless = false
        notificationManager.cancel(SystemNotificationHelper.HEADLESS_NOTIF_ID)
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isHeadless = true
        return super.onUnbind(intent)
    }

    override suspend fun monitorFcmToken(business: PhoenixBusiness) {
        val token = internalData.getFcmToken.filterNotNull().first()
        business.connectionsManager.connections.first { it.peer == Connection.ESTABLISHED }
        business.registerFcmToken(token)
    }

    override fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                log.warn("fetching FCM registration token failed: ${task.exception?.localizedMessage}")
                return@OnCompleteListener
            }
            task.result?.let { serviceScope.launch { internalData.saveFcmToken(it) } }
        })
    }

    override fun deleteFcmToken() {
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            if (task.isSuccessful) refreshFcmToken()
        }
    }

    override fun isFcmAvailable(context: Context): Boolean {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    // See [scheduleShutdown].
    private val shutdownHandler = Handler(Looper.getMainLooper())

    /**
     * Schedules business shutdown and removal of service from the foreground if needed.
     * After receiving a FCM wake-up message, the service is put in the foreground and then keeps running
     * for a while (typically 2 minutes). Then it shuts down automatically.
     *
     * @param delayMillis default is 2 minutes. Should always be less than 3 minutes, to avoid [onTimeout] since
     *      foreground mode is using the `SHORT_SERVICE` foreground type.
     */
    private fun scheduleShutdown(delayMillis: Long = 2 * 60 * 1000L) {
        shutdownHandler.postDelayed(Runnable {
            if (isHeadless) {
                log.debug("reached scheduled shutdown...")
                if (receivedInBackground.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                notificationManager.cancel(SystemNotificationHelper.HEADLESS_NOTIF_ID)
                shutdown()
            }
        }, delayMillis)
    }

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val origin = intent?.getStringExtra(EXTRA_ORIGIN)
        val reason = intent?.getStringExtra(EXTRA_REASON)
        log.info("--- start service from intent [ intent=$intent, flag=$flags, startId=$startId ] reason=$reason origin=$origin in state=${state.value} ---")

        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
        val (notif, serviceType) = when (state.value) {
            is NodeServiceState.Off -> {
                try {
                    val seed = encryptedSeed!!.decrypt()
                    log.debug("successfully decrypted seed in the background, starting wallet...")
                    startBusiness(seed, requestCheckLegacyChannels = false)
                    SystemNotificationHelper.getHeadlessNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                } catch (e: Exception) {
                    log.error("failed to decrypt seed: {}", e.localizedMessage)
                    SystemNotificationHelper.getHeadlessFailureNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                }
            }
            else -> {
                SystemNotificationHelper.getHeadlessNotification(applicationContext) to ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                // TODO force reconnect if disconnected!
            }
        }

        startForeground(notif, serviceType)
        shutdownHandler.removeCallbacksAndMessages(null)
        scheduleShutdown()

        if (origin == ORIGIN_FCM) {
            isHeadless = true
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return START_NOT_STICKY
    }
}