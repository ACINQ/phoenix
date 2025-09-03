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
import fr.acinq.lightning.channel.states.Closing
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.data.WatchTowerOutcome
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

        val watchResult = userWallets.map { (walletId, wallet) ->
            watchWallet(walletId, wallet.words)
        }

        if (BusinessManager.isHeadless.first()) {
            BusinessManager.stopAllBusinesses()
        }

        return if (watchResult.all { it }) {
            log.info("finished $name, watchers have all terminated successfully")
            Result.success()
        } else {
            log.info("finished $name, one or more watchers encountered an error")
            Result.failure()
        }
    }

    /** Watches channels for a given node id ; return false if an error occurred. */
    private suspend fun watchWallet(walletId: WalletId, words: List<String>): Boolean {

        val res = BusinessManager.startNewBusiness(words = words, isHeadless = true)
        val internalPrefs = DataStoreManager.loadInternalPrefsForWallet(applicationContext, walletId)
        if (res is StartBusinessResult.Failure) {
            log.info("failed to start business for wallet=$walletId")
            internalPrefs.saveChannelsWatcherOutcome(Outcome.Unknown(currentTimestampMillis()))
            return true
        }

        val business = BusinessManager.businessFlow.value[walletId]
        if (business == null) {
            log.info("failed to access business for wallet=$walletId")
            internalPrefs.saveChannelsWatcherOutcome(Outcome.Unknown(currentTimestampMillis()))
            return true
        }

        val notificationsManager = business.notificationsManager
        try {

            business.appConnectionsDaemon!!.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Peer)
            business.appConnectionsDaemon!!.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Http)

            val peer = withTimeout(5_000) {
                business.peerManager.getPeer()
            }

            val channelsAtBoot = peer.bootChannelsFlow.filterNotNull().first()
            if (channelsAtBoot.isEmpty()) {
                log.info("no channels found, nothing to watch")
                internalPrefs.saveChannelsWatcherOutcome(Outcome.Nominal(currentTimestampMillis()))
                return true
            } else {
                log.info("watching ${channelsAtBoot.size} channel(s)")
            }

            // connect electrum (and only electrum), and wait for the watcher to catch-up
            withTimeout(ELECTRUM_TIMEOUT_MILLIS) {
                business.electrumWatcher.openUpToDateFlow().first()
            }
            log.info("electrum watcher is up-to-date")
            business.appConnectionsDaemon?.decrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Electrum)

            val revokedCommitsBeforeWatching = channelsAtBoot.map { (channelId, state) ->
                (state as? Closing)?.revokedCommitPublished?.let { channelId to it }
            }.filterNotNull().toMap()

            log.info("there were initially ${revokedCommitsBeforeWatching.size} channel(s) with revoked commitments")
            log.info("checking for new revoked commitments on ${peer.channels.size} channel(s)")
            val unknownRevokedAfterWatching = peer.channels.filter { (channelId, state) ->
                state is Closing && state.revokedCommitPublished.any {
                    val isKnown = revokedCommitsBeforeWatching[channelId]?.contains(it) ?: false
                    if (!isKnown) {
                        log.warn("found unknown revoked commit for channel=${channelId.toHex()}, tx=${it.commitTx}")
                    }
                    !isKnown
                }
            }.keys

            if (unknownRevokedAfterWatching.isNotEmpty()) {
                log.warn("new revoked commits found, notifying user")
                notificationsManager.saveWatchTowerOutcome(WatchTowerOutcome.RevokedFound(channels = unknownRevokedAfterWatching))
                internalPrefs.saveChannelsWatcherOutcome(Outcome.RevokedFound(currentTimestampMillis()))
                SystemNotificationHelper.notifyRevokedCommits(applicationContext)
            } else {
                log.info("no revoked commit found, channels-watcher job completed successfully")
                notificationsManager.saveWatchTowerOutcome(WatchTowerOutcome.Nominal(channelsWatchedCount = peer.channels.size))
                internalPrefs.saveChannelsWatcherOutcome(Outcome.Nominal(currentTimestampMillis()))
            }

            return true
        } catch (e: Exception) {
            log.error("failed to run channels-watcher job: ", e)
            notificationsManager.saveWatchTowerOutcome(WatchTowerOutcome.Unknown())
            internalPrefs.saveChannelsWatcherOutcome(Outcome.Unknown(currentTimestampMillis()))
            return false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChannelsWatcher::class.java)
        private val name = "channels-watcher-job"
        const val TAG = BuildConfig.APPLICATION_ID + ".ChannelsWatcher"
        private const val ELECTRUM_TIMEOUT_MILLIS = 5 * 60_000L

        fun schedule(context: Context) {
            log.info("scheduling channels watcher")
            val work = PeriodicWorkRequest.Builder(ChannelsWatcher::class.java, 36, TimeUnit.HOURS, 12, TimeUnit.HOURS).addTag(TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        fun scheduleASAP(context: Context) {
            val work = OneTimeWorkRequest.Builder(ChannelsWatcher::class.java).addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }

        fun cancel(context: Context): Operation {
            return WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

    }

    @Serializable
    sealed class Outcome {
        abstract val timestamp: Long

        @Serializable
        data class Unknown(override val timestamp: Long) : Outcome()

        @Serializable
        data class Nominal(override val timestamp: Long) : Outcome()

        @Serializable
        data class RevokedFound(override val timestamp: Long) : Outcome()
    }
}
