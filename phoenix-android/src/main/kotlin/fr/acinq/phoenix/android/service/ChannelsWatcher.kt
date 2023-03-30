/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.service

import android.content.Context
import android.text.format.DateUtils
import androidx.work.*
import fr.acinq.lightning.channel.Closing
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.Notifications
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit


/** Worker that monitors channels in the background. Triggers a user-facing notification when an unexpected spending is detected. */
class ChannelsWatcher(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {

            val legacyAppStatus = PrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first()
            if (legacyAppStatus !is LegacyAppStatus.NotRequired) {
                log.info("abort channels-watcher service in state=${legacyAppStatus.name()}")
                InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.Nominal(currentTimestampMillis()))
                return Result.success()
            }

            val business = (applicationContext as? PhoenixApplication)?.business ?: run {
                log.error("phoenix business is not available, channels-watcher job did not run")
                InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.Unknown(currentTimestampMillis()))
                return Result.failure()
            }

            val peer = withTimeout(60_000) {
                business.peerManager.getPeer()
            }

            val channelsBeforeWatching = peer.channels
            if (channelsBeforeWatching.isEmpty()) {
                log.info("no channels found, nothing to watch")
                InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.Nominal(currentTimestampMillis()))
                return Result.success()
            } else {
                log.info("watching ${channelsBeforeWatching.size} channels")
            }

            // connect electrum (and only electrum), and wait for the watcher to catch-up
            val upToDate = withTimeout(ELECTRUM_TIMEOUT_MILLIS) {
                peer.watcher.openUpToDateFlow().first()
            }
            business.appConnectionsDaemon?.decrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Electrum)
            log.info("electrum watcher is up-to-date, timestamp $upToDate (${upToDate.toAbsoluteDateTimeString()})")

            val revokedCommitsBeforeWatching = channelsBeforeWatching.map { (channelId, state) ->
                (state as? Closing)?.revokedCommitPublished?.let { channelId to it }
            }.filterNotNull().toMap()

            val hasUnknownRevokedAfterWatching = peer.channels.any { (channelId, state) ->
                state is Closing && state.revokedCommitPublished.any {
                    val isKnown = revokedCommitsBeforeWatching[channelId]?.contains(it) ?: false
                    if (!isKnown) log.info("found unknown revoked commit for channel=${channelId.toHex()}, tx=${it.commitTx}")
                    !isKnown
                }
            }

            if (hasUnknownRevokedAfterWatching) {
                log.info("new revoked commits found, notifying user")
                InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.RevokedFound(currentTimestampMillis()))
                Notifications.notifyRevokedCommits(applicationContext)
            } else {
                InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.Nominal(currentTimestampMillis()))
                log.info("channels-watcher job completed, no revoked commit found")
            }

            return Result.success()
        } catch (e: Exception) {
            log.error("failed to run channels-watcher job: ", e)
            InternalData.saveChannelsWatcherOutcome(applicationContext, Outcome.Unknown(currentTimestampMillis()))
            return Result.failure()
        } finally {

        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChannelsWatcher::class.java)
        private const val WATCHER_WORKER_TAG = BuildConfig.APPLICATION_ID + ".ChannelsWatcher"
        private val ELECTRUM_TIMEOUT_MILLIS = 5 * 60_000L

        /**
         * Time window in milliseconds in which the last channels watch result can be considered fresh enough that the user
         * does not need to be reminded that phoenix needs a working connection.
         */
        private const val MAX_FRESH_WINDOW = DateUtils.DAY_IN_MILLIS * 3

        /**
         * Time window similar to [MAX_FRESH_WINDOW], but only if the last result was [Outcome.Nominal].
         */
        private const val MAX_FRESH_WINDOW_IF_OK = DateUtils.DAY_IN_MILLIS * 5

        fun schedule(context: Context) {
            log.info("scheduling channels watcher")
            val work = PeriodicWorkRequest.Builder(ChannelsWatcher::class.java, 23, TimeUnit.HOURS, 12, TimeUnit.HOURS)
                .addTag(WATCHER_WORKER_TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WATCHER_WORKER_TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        fun scheduleASAP(context: Context) {
            val work = OneTimeWorkRequest.Builder(ChannelsWatcher::class.java).addTag(WATCHER_WORKER_TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WATCHER_WORKER_TAG, ExistingWorkPolicy.REPLACE, work)
        }
    }

    @Serializable
    sealed class Outcome {
        abstract val timestamp: Long
        @Serializable data class Unknown(override val timestamp: Long): Outcome()
        @Serializable data class Nominal(override val timestamp: Long): Outcome()
        @Serializable data class RevokedFound(override val timestamp: Long): Outcome()
    }
}
