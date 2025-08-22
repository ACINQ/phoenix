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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.ListWalletState
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.wallet.AvailableWalletsList
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.shareFile


@Composable
fun StartupView(
    appViewModel: AppViewModel,
    startupViewModel: StartupViewModel,
    onShowIntro: () -> Unit,
    onSeedNotFound: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val showIntro = application.globalPrefs.getShowIntro.collectAsState(initial = null)
    if (showIntro.value == true) {
        LaunchedEffect(Unit) { onShowIntro() }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        when (val listWalletState = appViewModel.listWalletState.value) {
            is ListWalletState.Init -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_phoenix),
                    contentDescription = "phoenix-icon",
                )
                Text(text = stringResource(id = R.string.startup_loading_seed))
            }
            is ListWalletState.Success -> {
                val availableWallets by appViewModel.availableWallets.collectAsState()
                val defaultNodeId = globalPrefs.getDefaultNodeId.collectAsState(null)
                // the default wallet should be immediately started only *once* ; to keep track of that, we use a state in the app VM
                val startDefaultImmediately by remember { mutableStateOf(appViewModel.startDefaultImmediately.value) }
                LaunchedEffect(Unit) {
                    if (appViewModel.startDefaultImmediately.value) {
                        appViewModel.startDefaultImmediately.value = false
                    }
                }

                // we may already have a desired node id and thus may not need user input to know which wallet to load
                val desiredNodeIdFlow = appViewModel.desiredNodeId.collectAsState()
                val desiredNodeId = desiredNodeIdFlow.value

                val businessMap by BusinessManager.businessFlow.collectAsState()
                val desiredBusiness = businessMap[desiredNodeId]
                val desiredNodeWallet = availableWallets[desiredNodeId]

                when {
                    desiredBusiness != null -> {
                        PhoenixStartupIcon()
                        Text(text = stringResource(R.string.startup_started))
                        LaunchedEffect(Unit) { onBusinessStarted() }
                    }
                    desiredNodeId != null && desiredNodeWallet != null -> {
                        PhoenixStartupIcon()
                        Text(text = stringResource(R.string.startup_starting))
                        LaunchedEffect(Unit) {
                            startupViewModel.startupNode(desiredNodeId, desiredNodeWallet.words, onStartupSuccess = {
                                onBusinessStarted()
                            })
                        }
                    }
                    else -> {
                        val availableWalletMetadataPrefs = globalPrefs.getAvailableWalletsMeta.collectAsState(null)
                        val availableWalletMetadata = availableWalletMetadataPrefs.value

                        when {
                            availableWallets.isEmpty() -> {
                                LaunchedEffect(Unit) { onSeedNotFound() }
                            }
                            availableWalletMetadata == null -> {
                                PhoenixStartupIcon()
                                Text(text = stringResource(R.string.utils_loading_prefs))
                            }
                            else -> {
                                // TODO if default is set => launch default
                                when (val startupState = startupViewModel.state.value) {
                                    is StartupViewState.Init -> {
                                        AvailableWalletsList(
                                            wallets = availableWallets,
                                            walletsMetadata = availableWalletMetadata,
                                            activeNodeId = null,
                                            loadNodeImmediately = if (startDefaultImmediately) defaultNodeId.value else null,
                                            onWalletClick = { userWallet ->
                                                startupViewModel.startupNode(nodeId = userWallet.nodeId, words = userWallet.words, onStartupSuccess = {
                                                    appViewModel.switchToWallet(userWallet.nodeId)
                                                    onBusinessStarted()
                                                })
                                            },
                                            modifier = Modifier.padding(horizontal = 24.dp),
                                            topContent = {
                                                Spacer(Modifier.height(64.dp))
                                                Text(text = "Select a wallet", style = MaterialTheme.typography.h4)
                                                Spacer(Modifier.height(16.dp))
                                            },
                                            bottomContent = {
                                                Spacer(Modifier.height(128.dp))
                                            }
                                        )
                                    }
                                    is StartupViewState.StartingBusiness -> {
                                        PhoenixStartupIcon()
                                        Text(text = stringResource(R.string.startup_starting))
                                    }
                                    is StartupViewState.BusinessActive -> {
                                        PhoenixStartupIcon()
                                        Text(text = stringResource(R.string.startup_started))
                                    }
                                    is StartupViewState.Error -> {
                                        PhoenixStartupIcon()
                                        StartBusinessError(nodeId = startupState.nodeId, error = startupState)
                                    }
                                    is StartupViewState.SeedRecovery -> {
                                        StartupRecoveryView(state = startupState, onRecoveryClick = startupViewModel::recoverSeed, onReset = { startupViewModel.state.value = StartupViewState.SeedRecovery.Init })
                                        TODO("handle that in a separate screen/state")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ListWalletState.Error -> {
                ListWalletsError(state = listWalletState, onFallbackClick = { startupViewModel.state.value = StartupViewState.SeedRecovery.Init })
            }
        }
    }
}

@Composable
private fun PhoenixStartupIcon() {
    Image(
        painter = painterResource(id = R.drawable.ic_phoenix),
        contentDescription = "phoenix-icon",
    )
}

@Composable
private fun StartBusinessError(nodeId: String, error: StartupViewState.Error) {
    ErrorMessage(
        header= "The wallet could not start",
        details = when (error) {
            is StartupViewState.Error.Generic -> error.cause?.message
        },
        alignment = Alignment.CenterHorizontally,
    )
}

@Composable
private fun ListWalletsError(state: ListWalletState.Error, onFallbackClick: () -> Unit) {
    val context = LocalContext.current
    ErrorMessage(
        header = when (state) {
            is ListWalletState.Error.Generic -> stringResource(id = R.string.startup_error_generic)
            is ListWalletState.Error.DecryptionError.GeneralException -> stringResource(id = R.string.startup_error_decryption_general)
            is ListWalletState.Error.DecryptionError.KeystoreFailure -> stringResource(id = R.string.startup_error_decryption_keystore)
        },
        details = when (state) {
            is ListWalletState.Error.Generic -> state.cause?.message
            is ListWalletState.Error.DecryptionError.GeneralException -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}"
            is ListWalletState.Error.DecryptionError.KeystoreFailure -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}" +
                    (state.cause.cause?.localizedMessage?.take(80) ?: "")
        },
        alignment = Alignment.CenterHorizontally,
    )

    HSeparator(width = 50.dp)
    Spacer(modifier = Modifier.height(16.dp))
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
}
