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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.*
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.io.PhoenixAndroidLegacyInfoEvent
import fr.acinq.phoenix.android.components.mvi.MockView
import fr.acinq.phoenix.android.service.NodeService
import fr.acinq.phoenix.android.utils.LegacyMigrationHelper
import fr.acinq.phoenix.android.utils.PhoenixAndroidTheme
import fr.acinq.phoenix.android.utils.datastore.LegacyPrefsMigration
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
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
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), 1234)
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
                LegacyMigrationHelper.migrateLegacyPayments(applicationContext)
                LegacyPrefsMigration.doMigration(applicationContext)
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(appViewModel.serviceConnection)
        } catch (e: Exception) {
            log.error("failed to unbind activity from node service: {}", e.localizedMessage)
        }
        log.info("destroyed main kmp activity")
    }

}

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { PhoenixAndroidTheme(rememberNavController()) { Text("Preview") } }
}
