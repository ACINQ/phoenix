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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.*
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.android.utils.PhoenixAndroidTheme
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

    val log: Logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    private var navController: NavHostController? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> appViewModel.lockScreen()
                else -> Unit
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appViewModel.serviceState.observe(this) {
            log.debug("wallet state update=${it.name}")
        }

        intent?.fixUri()

        // lock screen when screen is off
        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, intentFilter)

        setContent {
            navController = rememberNavController()
            val businessState = (application as PhoenixApplication).business.collectAsState()

            navController?.let {
                PhoenixAndroidTheme(it) {
                    when (val business = businessState.value) {
                        null -> LoadingAppView()
                        else -> AppView(business, appViewModel, it)
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        appViewModel.scheduleAutoLock()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // force the intent flag to single top, in order to avoid [handleDeepLink] finish the current activity.
        // this would otherwise clear the app view model, i.e. loose the state which virtually reboots the app
        // TODO: look into detaching the app state from the activity
        log.info("receive new_intent with data=${intent?.data}")
        intent?.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        intent?.fixUri()
        try {
            this.navController?.handleDeepLink(intent)
        } catch (e: Exception) {
            log.warn("could not handle deeplink: {}", e.localizedMessage)
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NodeService::class.java).let { intent ->
            applicationContext.bindService(intent, appViewModel.serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)
        }
    }

    override fun onResume() {
        super.onResume()
        tryReconnect()
    }

    private fun tryReconnect() {
        lifecycleScope.launch {
            (application as? PhoenixApplication)?.business?.value?.let { business ->
                val daemon = business.appConnectionsDaemon ?: return@launch
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
        unregisterReceiver(screenStateReceiver)
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
        if (scheme == "phoenix" && ssp != null && (ssp.startsWith("lnbc") || ssp.startsWith("lntb") || ssp.startsWith("lnurl") || ssp.startsWith("lno"))) {
            this.data = "lightning:$ssp".toUri()
            log.debug("rewritten intent uri from {} to {}", initialUri, intent.data)
        }
    }

}
