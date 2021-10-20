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
import kotlinx.coroutines.ExperimentalCoroutinesApi


@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

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
        // (application as? PhoenixApplication)?.business?.decrementDisconnectCount()
    }

    override fun onStop() {
        super.onStop()
        // (application as? PhoenixApplication)?.business?.incrementDisconnectCount()
    }

}

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { PhoenixAndroidTheme { Text("Preview") } }
}
