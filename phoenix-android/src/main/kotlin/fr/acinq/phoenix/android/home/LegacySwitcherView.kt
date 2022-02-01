/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.LegacyAppStatus
import fr.acinq.phoenix.legacy.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LegacySwitcherView(
    onLegacyFinished: () -> Unit
) {
    val log = logger("LegacySwitcherView")
    val app = application
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val legacyAppStatus by app.legacyAppStatus.observeAsState(initial = null)

    log.debug { "legacy view with legacyAppStatus=${legacyAppStatus}" }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(id = R.string.legacyswitch_title))
        Text(text = "[${legacyAppStatus}]", style = MaterialTheme.typography.caption)
        when (legacyAppStatus) {
            null -> {
                // do nothing
            }
            LegacyAppStatus.INIT -> {
                app.legacyAppStatus.value = LegacyAppStatus.EXPECTED
            }
            LegacyAppStatus.RUNNING -> {
                // just wait
            }
            LegacyAppStatus.EXPECTED -> {
                LaunchedEffect(key1 = true) {
                    scope.launch {
                        delay(1000)
                        if (app.legacyAppStatus.value == LegacyAppStatus.EXPECTED) {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                            )
                            app.legacyAppStatus.value = LegacyAppStatus.RUNNING
                        }
                    }
                }
            }
            LegacyAppStatus.FINISHED -> {
                LaunchedEffect(key1 = true) {
                    scope.launch {
                        delay(1000)
                        onLegacyFinished()
                    }
                }
            }
            LegacyAppStatus.INTERRUPTED -> {
                BorderButton(text = R.string.legacyswitch_restart, onClick = { app.legacyAppStatus.value = LegacyAppStatus.INIT })
            }
        }
    }
}