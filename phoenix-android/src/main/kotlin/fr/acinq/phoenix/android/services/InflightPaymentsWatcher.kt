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
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * The purpose of this worker is to settle in-flight payments whenever possible. This will help prevent
 * timeouts (and channels force-close) on apps that run on devices that cannot receive push notifications
 * from the peer when Phoenix is closed/in the background.
 *
 * Example: devices using GrapheneOS, where FCM is not supported.
 *
 * This service runs on all systems without conditions, because it's light, and connecting regularly to
 * the peer is good practice.
 */
class InflightPaymentsWatcher(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    val log = LoggerFactory.getLogger(this::class.java)

    override suspend fun doWork(): Result {
        log.info("starting in-flight-payments check job")

        val legacyAppStatus = LegacyPrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first()
        if (legacyAppStatus !is LegacyAppStatus.NotRequired) {
            log.info("aborting in-flight-payments check job, legacy_status=${legacyAppStatus.name()}")
            return Result.success()
        }

        val internalData = (applicationContext as PhoenixApplication).internalDataRepository
        val inFlightPaymentsCount = internalData.getInFlightPaymentsCount.first()

        if (inFlightPaymentsCount == 0) {
            log.info("expecting NO in-flight payments, terminating job...")
            return Result.success()
        } else {
            log.info("expecting $inFlightPaymentsCount in-flight payments, starting NodeService...")
            val serviceFlow = MutableStateFlow<NodeService?>(null)
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(component: ComponentName, bind: IBinder) {
                    log.info("connected to NodeService")
                    serviceFlow.value = (bind as NodeService.NodeBinder).getService()
                }

                override fun onServiceDisconnected(component: ComponentName) {
                    log.info("disconnected from NodeService")
                    serviceFlow.value = null
                }
            }

            Intent(applicationContext, NodeService::class.java).let { intent ->
                applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }

            val service = serviceFlow.filterNotNull().first()
            when (val state = service.state.value) {
                is NodeServiceState.Init, is NodeServiceState.Running,  -> {
                    log.info("node in state=${state.name}, skipping redundant in-flight payments check")
                    return Result.success()
                }
                is NodeServiceState.Off -> {
                    log.info("node service in state=${state.name}, starting business on our own...")
                    // note: we can't simply launch NodeService, either as a background service (disallowed since android 8) or as a
                    // foreground service (disallowed since android 14)
                    return runNodeOnce(internalData)
                }
                is NodeServiceState.Error, null, NodeServiceState.Disconnected -> {
                    log.info("node in state=${state?.name}, unable to check in-flight payments ; scheduling another worker...")
                    scheduleOnce(applicationContext)
                    return Result.failure()
                }
            }
        }
    }

    private suspend fun startBusiness(mnemonics: ByteArray): PhoenixBusiness {
        // retrieve preferences before starting business
        val business = PhoenixBusiness(PlatformContext(applicationContext))
        val electrumServer = UserPrefs.getElectrumServer(applicationContext).first()
        val isTorEnabled = UserPrefs.getIsTorEnabled(applicationContext).first()
        val liquidityPolicy = UserPrefs.getLiquidityPolicy(applicationContext).first()
        val trustedSwapInTxs = LegacyPrefsDatastore.getMigrationTrustedSwapInTxs(applicationContext).first()
        val preferredFiatCurrency = UserPrefs.getFiatCurrency(applicationContext).first()

        // preparing business
        val seed = business.walletManager.mnemonicsToSeed(EncryptedSeed.toMnemonics(mnemonics))
        business.walletManager.loadWallet(seed)
        business.appConfigurationManager.updateElectrumConfig(electrumServer)
        business.appConfigurationManager.updatePreferredFiatCurrencies(
            AppConfigurationManager.PreferredFiatCurrencies(primary = preferredFiatCurrency, others = emptySet())
        )

        // start business
        business.start(
            StartupParams(
                requestCheckLegacyChannels = false,
                isTorEnabled = isTorEnabled,
                liquidityPolicy = liquidityPolicy,
                trustedSwapInTxs = trustedSwapInTxs.map { TxId(it) }.toSet()
            )
        )

        // start the swap-in wallet watcher
        business.peerManager.getPeer().startWatchSwapInWallet()
        return business
    }

    private suspend fun runNodeOnce(internalData: InternalDataRepository): Result {
        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
        val mnemonics = if (encryptedSeed is EncryptedSeed.V2.NoAuth) encryptedSeed.decrypt() else {
            log.error("cannot decrypt seed=${encryptedSeed?.name()}, aborting in-flight payment worker")
            return Result.failure()
        }
        log.debug("successfully decrypted seed in the background, starting wallet...")

        if (LegacyPrefsDatastore.getPrefsMigrationExpected(applicationContext).first() == true) {
            log.error("legacy data migration is required, aborting in-flight payment worker")
            return Result.failure()
        }

        val business = startBusiness(mnemonics)

        business.connectionsManager.connections.first { it.global is Connection.ESTABLISHED }

        log.info("connections established, watching channels for in-flight payments...")

        val job = business.peerManager.channelsFlow.filterNotNull().collectIndexed { index, channels ->
            val paymentsCount = channels.inFlightPaymentsCount()
            internalData.saveInFlightPaymentsCount(paymentsCount)
            when {
                channels.isEmpty() || channels.any { it.value.state is Syncing } -> {
                    if (index < 10) {
                        log.info("channels empty, pausing watcher for 10s (attempt #$index)")
                        delay(10_000)
                    } else {
                        log.info("no channels found, terminating watcher successfully")

                        return@collectIndexed
                    }
                }
                paymentsCount > 0 -> {
                    if (index < 20) {
                        log.info("$paymentsCount payments in-flight, waiting 10s for resolution (attempt #$index)...")
                        delay(10_000)
                    } else {
                        log.info("$paymentsCount payments in-flight, no resolution yet. Notifying user.")

                        return@collectIndexed
                    }
                }
                else -> {
                    log.info("$paymentsCount payments in-flight, successfully terminating worker (attempt #$index)...")
                    return@collectIndexed
                }
            }
        }
        log.info("terminating node...")
        business.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
        business.stop()

        return Result.success()
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        private const val TAG = BuildConfig.APPLICATION_ID + ".InflightPaymentsWatcher"

        /** Schedule a in-flight payments watcher job to start every few hours. */
        fun schedulePeriodic(context: Context) {
            log.info("scheduling periodic in-flight-payments watcher")
            val work = PeriodicWorkRequest.Builder(InflightPaymentsWatcher::class.java, 2, TimeUnit.HOURS, 3, TimeUnit.HOURS).addTag(TAG)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, work.build())
        }

        /** Schedule an in-flight payments job to run once in [delay] from now (by default, 2 hours). Existing schedules are replaced. */
        fun scheduleOnce(context: Context, delay: Duration = 2.hours) {
            log.info("scheduling ${this::class.java.name} in $delay from now")
            val work = OneTimeWorkRequestBuilder<InflightPaymentsWatcher>().setInitialDelay(delay.toJavaDuration()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, work)
        }

        /** Cancel all scheduled in-flight payments worker. */
        fun cancel(context: Context): Operation {
            return WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}

