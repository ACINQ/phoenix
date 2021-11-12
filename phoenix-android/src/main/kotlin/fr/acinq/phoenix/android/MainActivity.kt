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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import fr.acinq.phoenix.android.components.mvi.MockView
import fr.acinq.phoenix.android.service.NodeService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

    val log: Logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val appViewModel by viewModels<AppViewModel> { AppViewModel.Factory(applicationContext) }

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), 1234)
        setContent {
            PhoenixAndroidTheme {
                AppView(appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NodeService::class.java).let { intent ->
            applicationContext.bindService(intent, appViewModel.serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)
        }
    }

    override fun onStop() {
        super.onStop()
        // (application as? PhoenixApplication)?.business?.incrementDisconnectCount()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(appViewModel.serviceConnection)
        } catch (e: Exception) {
            log.error("failed to unbind activity from node service: {}", e.localizedMessage)
        }
        log.info("main activity destroyed")
    }

}

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { PhoenixAndroidTheme { Text("Preview") } }
}
