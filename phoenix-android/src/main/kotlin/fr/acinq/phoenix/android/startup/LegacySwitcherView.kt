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

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.MainActivity
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LegacySwitcherView(
    onProceedNormally: () -> Unit
) {
    val log = logger("LegacySwitcherView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val legacyAppStatus by LegacyPrefsDatastore.getLegacyAppStatus(context).collectAsState(initial = null)
    log.debug { "legacy switcher with legacyAppStatus=${legacyAppStatus}" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(id = R.string.legacyswitch_title))
        when (legacyAppStatus) {
            null -> {
                // do nothing
            }
            LegacyAppStatus.Required.Expected -> {
                LaunchedEffect(key1 = true) {
                    scope.launch {
                        LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.InitStart)
                    }
                }
            }
            LegacyAppStatus.Required.InitStart -> {
                LaunchedEffect(key1 = true) {
                    scope.launch {
                        if (legacyAppStatus == LegacyAppStatus.Required.InitStart) {
                            LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Running)
                            log.info { "switching to legacy app" }
                            context.startActivity(Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            })
                            delay(200)
                            (context as? Activity)?.finish()
                        }
                    }
                }
            }
            LegacyAppStatus.Required.Running -> {
                // just wait
            }
            LegacyAppStatus.Required.Interrupted -> {
                BorderButton(text = stringResource(id = R.string.legacyswitch_restart), onClick = {
                    scope.launch {
                        LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                    }
                })
            }
            LegacyAppStatus.NotRequired -> onProceedNormally()
            LegacyAppStatus.Unknown -> onProceedNormally()
        }
    }
}