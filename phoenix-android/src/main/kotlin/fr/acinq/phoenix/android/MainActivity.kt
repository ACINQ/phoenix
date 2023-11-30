/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.*
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.io.PhoenixAndroidLegacyInfoEvent
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.android.utils.LegacyMigrationHelper
import fr.acinq.phoenix.android.utils.PhoenixAndroidTheme
import fr.acinq.phoenix.legacy.utils.*
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

    val log: Logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appViewModel.serviceState.observe(this) {
            log.debug("wallet state update=${it.name}")
        }

        intent?.fixUri()

        // reset required status to expected if needed
        lifecycleScope.launch {
            if (LegacyPrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first() is LegacyAppStatus.Required) {
                LegacyPrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.Required.Expected)
            }
        }

        // migrate legacy data if needed
        lifecycleScope.launch {
            val doDataMigration = LegacyPrefsDatastore.getDataMigrationExpected(applicationContext).filterNotNull().first()
            if (doDataMigration) {
                delay(7_000)
                LegacyMigrationHelper.migrateLegacyPayments(applicationContext)
                delay(5_000)
                LegacyPrefsDatastore.saveDataMigrationExpected(applicationContext, false)
            }
        }

        // listen to legacy channels events on the peer's event bus
        lifecycleScope.launch {
            val application = (application as PhoenixApplication)
            application.business.peerManager.getPeer().eventsFlow.collect {
                if (it is PhoenixAndroidLegacyInfoEvent) {
                    if (it.info.hasChannels) {
                        log.info("legacy channels have been found")
                        LegacyPrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.Required.Expected)
                    } else {
                        log.info("no legacy channels were found")
                        LegacyPrefsDatastore.savePrefsMigrationExpected(applicationContext, false)
                        LegacyPrefsDatastore.saveDataMigrationExpected(applicationContext, false)
                        LegacyPrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.NotRequired)
                    }
                }
            }
        }

        setContent {
            navController = rememberNavController()
            navController?.let {
                PhoenixAndroidTheme(it) {
                    AppView(appViewModel, it)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // force the intent flag to single top, in order to avoid [handleDeepLink] finish the current activity.
        // this would otherwise clear the app view model, i.e. loose the state which virtually reboots the app
        // TODO: look into detaching the app state from the activity
        log.info("receive new_intent=$intent")
        intent?.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent?.fixUri()
        this.navController?.handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NodeService::class.java).let { intent ->
            applicationContext.bindService(intent, appViewModel.serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)
        }
    }

    override fun onResume() {
        super.onResume()
        val business = (application as? PhoenixApplication)?.business
        val daemon = business?.appConnectionsDaemon
        if (daemon != null) {
            tryReconnect(business, daemon)
        }
    }

    private fun tryReconnect(business: PhoenixBusiness, daemon: AppConnectionsDaemon) {
        lifecycleScope.launch {
            val isDataMigrationExpected = LegacyPrefsDatastore.getDataMigrationExpected(applicationContext).filterNotNull().first()
            if (isDataMigrationExpected) {
                log.debug("ignoring tryReconnect when data migration is in progress")
            } else {
                val connections = business.connectionsManager.connections.value
                if (connections.electrum !is Connection.ESTABLISHED) {
                    lifecycleScope.launch {
                        log.info("resuming app with electrum conn=${connections.electrum}, forcing reconnection...")
                        daemon.forceReconnect(AppConnectionsDaemon.ControlTarget.Electrum)
                    }
                }
                if (connections.peer !is Connection.ESTABLISHED) {
                    lifecycleScope.launch {
                        log.info("resuming app with peer conn=${connections.peer}, forcing reconnection...")
                        daemon.forceReconnect(AppConnectionsDaemon.ControlTarget.Peer)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(appViewModel.serviceConnection)
        } catch (e: Exception) {
            log.error("failed to unbind activity from node service: {}", e.localizedMessage)
        }
        log.debug("destroyed main activity")
    }

    /**
     * There are many apps launching LN-themed URIs, and they use all kind of pattern. This methods fixes them so they are standard
     * and can deeplink to the appropriate screen of the application.
     *
     * For example, instead of `phoenix:<invoice>`, use `lightning:<invoice>`.
     *
     * Note: [Intent] fields are mutable.
     */
    private fun Intent.fixUri() {
        val initialUri = this.data
        val scheme = initialUri?.scheme
        val ssp = initialUri?.schemeSpecificPart
        if (scheme == "phoenix" && ssp != null && (ssp.startsWith("lnbc") || ssp.startsWith("lntb"))) {
            this.data = "lightning:$ssp".toUri()
            log.debug("rewritten intent uri from $initialUri to ${intent.data}")
        }
    }

}
