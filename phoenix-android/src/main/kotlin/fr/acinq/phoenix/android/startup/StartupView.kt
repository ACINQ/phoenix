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

package fr.acinq.phoenix.android.startup

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.internalData
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.shareFile


@Composable
fun StartupView(
    startupViewModel: StartupViewModel,
    onShowIntro: () -> Unit,
    onSeedNotFound: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val showIntro by internalData.getShowIntro.collectAsState(initial = null)

    if (showIntro == true) {
        LaunchedEffect(Unit) { onShowIntro() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_phoenix),
            contentDescription = "phoenix-icon",
        )

        when (val state = startupViewModel.state.value) {
            is StartupViewState.Init -> {
                Text(text = stringResource(id = R.string.startup_init))
            }
            StartupViewState.SeedNotFound -> {
                Text(text = stringResource(R.string.startup_loading_seed))
                LaunchedEffect(Unit) { onSeedNotFound() }
            }
            is StartupViewState.LoadingSeed -> {
                Text(text = stringResource(R.string.startup_loading_seed))
            }
            is StartupViewState.StartingBusiness -> {
                Text(text = stringResource(R.string.startup_starting))
            }

            is StartupViewState.BusinessActive -> {
                Text(text = stringResource(R.string.startup_started))
                LaunchedEffect(true) { onBusinessStarted() }
            }

            is StartupViewState.Error -> {
                StartupError(state = state, onFallbackClick = { startupViewModel.state.value = StartupViewState.SeedRecovery.Init })
            }

            is StartupViewState.SeedRecovery -> {
                StartupRecoveryView(state = state, onRecoveryClick = startupViewModel::recoverSeed, onReset = { startupViewModel.state.value = StartupViewState.SeedRecovery.Init })
            }
        }
    }
}

@Composable
private fun StartupError(state: StartupViewState.Error, onFallbackClick: () -> Unit) {
    val context = LocalContext.current
    ErrorMessage(
        header = when (state) {
            is StartupViewState.Error.Generic -> stringResource(id = R.string.startup_error_generic)
            is StartupViewState.Error.DecryptionError.GeneralException -> stringResource(id = R.string.startup_error_decryption_general)
            is StartupViewState.Error.DecryptionError.KeystoreFailure -> stringResource(id = R.string.startup_error_decryption_keystore)
        },
        details = when (state) {
            is StartupViewState.Error.Generic -> state.cause?.message
            is StartupViewState.Error.DecryptionError.GeneralException -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}"
            is StartupViewState.Error.DecryptionError.KeystoreFailure -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}" +
                    (state.cause.cause?.localizedMessage?.take(80) ?: "")
        },
        alignment = Alignment.CenterHorizontally,
    )

    HSeparator(width = 50.dp)
    Spacer(modifier = Modifier.height(16.dp))
    if (state is StartupViewState.Error.DecryptionError) {
        BorderButton(
            text = stringResource(id = R.string.startup_error_recovery_button),
            icon = R.drawable.ic_key,
            onClick = onFallbackClick
        )
        Spacer(modifier = Modifier.height(8.dp))
        val navController = navController
        BorderButton(
            text = stringResource(id = R.string.menu_settings),
            icon = R.drawable.ic_settings,
            onClick = { navController.navigate(Screen.Settings.route) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        val authority = remember { "${BuildConfig.APPLICATION_ID}.provider" }
        Button(
            text = stringResource(id = R.string.logs_share_button),
            onClick = {
                try {
                    val logFile = Logging.exportLogFile(context)
                    shareFile(
                        context = context,
                        data = FileProvider.getUriForFile(context, authority, logFile),
                        subject = context.getString(R.string.logs_share_subject),
                        chooserTitle = context.getString(R.string.logs_share_title)
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to export logs...", Toast.LENGTH_SHORT).show()
                }
            },
            textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.typography.subtitle2.color),
            shape = CircleShape,
        )
    } else {
        Text(text = stringResource(R.string.startup_error_try_again), textAlign = TextAlign.Center)
    }
}
