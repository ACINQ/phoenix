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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.*
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.io.PhoenixAndroidLegacyInfoEvent
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.service.NodeService
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
    private val appViewModel by viewModels<AppViewModel>()
    private var navController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appViewModel.walletState.observe(this) {
            log.debug("wallet state update=${it.name}")
        }

        // reset required status to expected if needed
        lifecycleScope.launch {
            if (PrefsDatastore.getLegacyAppStatus(applicationContext).filterNotNull().first() is LegacyAppStatus.Required) {
                PrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.Required.Expected)
            }
        }

        // migrate legacy data if needed
        lifecycleScope.launch {
            val doDataMigration = PrefsDatastore.getDataMigrationExpected(applicationContext).first()
            if (doDataMigration == true) {
                LegacyMigrationHelper.migrateLegacyPreferences(applicationContext)
                LegacyMigrationHelper.migrateLegacyPayments(applicationContext)
                PrefsDatastore.saveDataMigrationExpected(applicationContext, false)
            }
        }

        // listen to legacy channels events on the peer's event bus
        lifecycleScope.launch {
            val application = (application as PhoenixApplication)
            application.business.peerManager.getPeer().eventsFlow.collect {
                if (it is PhoenixAndroidLegacyInfoEvent) {
                    if (it.info.hasChannels) {
                        log.info("legacy channels have been found")
                        PrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.Required.Expected)
                    } else {
                        log.info("no legacy channels were found")
                        PrefsDatastore.saveStartLegacyApp(applicationContext, LegacyAppStatus.NotRequired)
                    }
                }
            }
        }

        setContent {
            rememberNavController().let {
                navController = it
                PhoenixAndroidTheme(it) {
                    AppView(appViewModel, it)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // force the intent flag to single top, in order to avoid [handleDeepLink] to finish the current activity.
        // this would otherwise clear the app view model, i.e. loose the state which virtually reboots the app
        // TODO: look into detaching the app state from the activity
        intent!!.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val connections = business.connectionsManager.connections.value
        if (connections.electrum !is Connection.ESTABLISHED) {
            lifecycleScope.launch {
                log.debug("resuming app with electrum conn=${connections.electrum}, reconnecting...")
                daemon.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Electrum)
                delay(500)
                daemon.decrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Electrum)
            }
        }
        if (connections.peer !is Connection.ESTABLISHED) {
            lifecycleScope.launch {
                log.info("resuming app with peer conn=${connections.peer}, reconnecting...")
                business.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Peer)
                delay(500)
                business.appConnectionsDaemon?.decrementDisconnectCount(AppConnectionsDaemon.ControlTarget.Peer)
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
        log.info("destroyed main activity")
    }

}
