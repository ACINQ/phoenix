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

package fr.acinq.phoenix.legacy.background

import akka.actor.ActorSystem
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import fr.acinq.eclair.CheckElectrumSetup
import fr.acinq.eclair.WatchListener
import fr.acinq.eclair.db.Databases
import fr.acinq.phoenix.legacy.BuildConfig
import fr.acinq.phoenix.legacy.MainActivity
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.utils.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import scala.Option
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * Background job watching the node's channels to detect cheating attempts. A notification will be shown in that case.
 */
class LegacyChannelsWatcher(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

  private val system = ActorSystem.apply("channels-watcher-system")
  private var setup: CheckElectrumSetup? = null

  private fun cleanup() {
    if (!system.isTerminated) {
      system.shutdown()
      log.debug("system shutdown requested...")
      system.awaitTermination()
      log.info("termination completed")
    }
    setup?.run {
      try {
        nodeParams().db().channels().close() // eclair.sqlite
        nodeParams().db().network().close() // network.sqlite
        nodeParams().db().audit().close() // audit.sqlite
      } catch (t: Throwable) {
        log.error("could not close at least one database connection: ", t)
      }
    }
  }

  override suspend fun doWork(): Result {
    log.info("channels watcher has started")
    try {
      val legacyAppStatus = PrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first()
      if (legacyAppStatus !is LegacyAppStatus.Required) {
        log.info("abort legacy channels-watcher service in state=${legacyAppStatus.name()}")
        Prefs.saveWatcherAttemptOutcome(applicationContext, WatchListener.`Ok$`.`MODULE$`)
        return Result.success()
      }

      if (!Wallet.getEclairDBFile(applicationContext).exists()) {
        log.info("no eclair db file yet...")
        Prefs.saveWatcherAttemptOutcome(applicationContext, WatchListener.`Ok$`.`MODULE$`)
        return Result.success()
      }

      val result = startElectrumCheck(applicationContext)
      log.info("check has completed with result {}", result)
      if (result is WatchListener.`NotOk$`) {
        log.warn("cheating attempt detected, app must be started ASAP!")
        showNotification(applicationContext, true)
      }
      Prefs.saveWatcherAttemptOutcome(applicationContext, result)
      return Result.success()
    } catch (t: Throwable) {
      Prefs.saveWatcherAttemptOutcome(applicationContext, WatchListener.`Unknown$`.`MODULE$`)
      log.error("channels watcher has failed: ", t)
      return Result.failure()
    } finally {
      cleanup()
    }
  }

  private fun startElectrumCheck(context: Context): WatchListener.WatchResult {
    Class.forName("org.sqlite.JDBC")
    CheckElectrumSetup(Wallet.getDatadir(context), Wallet.getOverrideConfig(context), Option.empty<Databases>(), system).run {
      setup = this
      if (nodeParams().db().channels().listLocalChannels().isEmpty) {
        log.info("no active channels found")
        return WatchListener.`Ok$`.`MODULE$`
      } else {
        // if there is no network connectivity, return failure
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return if (activeNetwork == null || !activeNetwork.isConnected) {
          if (!isLastCheckFresh(context)) {
            log.warn("last watcher result is stale and we cannot watch the chain due to no connection...")
            showNotification(context, false)
          }
          WatchListener.`Unknown$`.`MODULE$`
        } else {
          Await.result<WatchListener.WatchResult>(check(), Duration.apply(3, TimeUnit.MINUTES))
        }
      }
    }
  }

  private fun isLastCheckFresh(context: Context): Boolean {
    val lastOutcome = Prefs.getWatcherLastAttemptOutcome(context)
    val delaySinceCheck = System.currentTimeMillis() - lastOutcome.second
    log.debug("last watcher outcome was ${Transcriber.relativeTime(context, lastOutcome.second)} with result=${lastOutcome.first}")
    return when {
      lastOutcome.first == null -> {
        log.debug("channels watcher has never run, stale outcome")
        false
      }
      delaySinceCheck < MAX_FRESH_WINDOW -> true
      delaySinceCheck < MAX_FRESH_WINDOW_IF_OK && lastOutcome.first == WatchListener.`Ok$`.`MODULE$` -> true
      else -> false
    }
  }

  private fun showNotification(context: Context, isAlert: Boolean) {
    val startIntent = Intent(context, MainActivity::class.java)
    startIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    val title = context.getString(if (isAlert) R.string.notif_watcher_cheating_title else R.string.notif_watcher_connection_title)
    val message = context.getString(if (isAlert) R.string.notif_watcher_cheating_message else R.string.notif_watcher_connection_message)
    val builder = NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_ID__CHANNELS_WATCHER)
      .setSmallIcon(R.drawable.ic_phoenix_outline)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setContentIntent(PendingIntent.getActivity(context, Constants.NOTIF_ID__CHANNELS_WATCHER, startIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
      .setOngoing(isAlert)
      .setPriority(if (isAlert) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
    NotificationManagerCompat.from(context).notify(Constants.NOTIF_ID__CHANNELS_WATCHER, builder.build())
  }

  companion object {
    private val log = LoggerFactory.getLogger(LegacyChannelsWatcher::class.java)
    const val WATCHER_WORKER_TAG = BuildConfig.LIBRARY_PACKAGE_NAME + ".ChannelsWatcher"

    /**
     * Time window in milliseconds in which the last channels watch result can be considered fresh enough that the user
     * does not need to be reminded that phoenix needs a working connection.
     */
    private const val MAX_FRESH_WINDOW = DateUtils.DAY_IN_MILLIS * 3

    /**
     * Time window similar to [MAX_FRESH_WINDOW], but only if the last result was OK.
     */
    private const val MAX_FRESH_WINDOW_IF_OK = DateUtils.DAY_IN_MILLIS * 5

    fun schedule(context: Context) {
      log.info("scheduling channels watcher")
      val work = PeriodicWorkRequest.Builder(LegacyChannelsWatcher::class.java, 23, TimeUnit.HOURS, 12, TimeUnit.HOURS)
        .addTag(WATCHER_WORKER_TAG)
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(WATCHER_WORKER_TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
    }

    fun scheduleASAP(context: Context) {
      val work = OneTimeWorkRequest.Builder(LegacyChannelsWatcher::class.java).addTag(WATCHER_WORKER_TAG).build()
      WorkManager.getInstance(context).enqueueUniqueWork(WATCHER_WORKER_TAG, ExistingWorkPolicy.REPLACE, work)
    }
  }
}
