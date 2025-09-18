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
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.ListWalletState
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.auth.screenlock.CheckScreenLockPinFlow
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.wallet.WalletAvatar
import fr.acinq.phoenix.android.components.wallet.WalletsSelector
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.datastore.getByWalletIdOrDefault
import fr.acinq.phoenix.android.utils.extensions.findActivity
import fr.acinq.phoenix.android.utils.shareFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@Composable
fun StartupView(
    appViewModel: AppViewModel,
    startupViewModel: StartupViewModel,
    onShowIntro: () -> Unit,
    onSeedNotFound: () -> Unit,
    onManualRecoveryClick: () -> Unit,
    onWalletReady: () -> Unit,
) {
    val showIntro = application.globalPrefs.getShowIntro.collectAsState(initial = null)
    if (showIntro.value == true) {
        LaunchedEffect(Unit) { onShowIntro() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {

        when (val listWalletState = appViewModel.listWalletState.value) {
            is ListWalletState.Init -> {
                WalletStartupView(text = stringResource(id = R.string.startup_loading_seed), metadata = null)
            }
            is ListWalletState.Success -> {
                val availableWallets by appViewModel.availableWallets.collectAsState()
                val defaultWallet = globalPrefs.getDefaultWallet.collectAsState(null)
                val startWalletImmediately by appViewModel.startWalletImmediately.collectAsState()

                val availableWalletMetadataPrefs = globalPrefs.getAvailableWalletsMeta.collectAsState(null)
                val availableWalletMetadata = availableWalletMetadataPrefs.value

                // we may already have a desired node id and thus may not need user input to know which wallet to load
                val desiredNodeIdFlow = appViewModel.desiredWalletId.collectAsState()
                val desiredNodeId = desiredNodeIdFlow.value

                val activeWalletFlow = appViewModel.activeWalletInUI.collectAsState()
                val activeWallet = activeWalletFlow.value

                when {
                    availableWallets.isEmpty() -> {
                        LaunchedEffect(Unit) { onSeedNotFound() }
                        WalletStartupView(text = stringResource(R.string.startup_init), metadata = null)
                    }
                    availableWalletMetadata == null || defaultWallet.value == null -> {
                        WalletStartupView(text = stringResource(R.string.startup_preparing), metadata = null)
                    }
                    activeWallet != null -> {
                        WalletStartupView(text = stringResource(R.string.startup_started), metadata = availableWalletMetadata[activeWallet.id])
                        LaunchedEffect(Unit) {
                            onWalletReady()
                        }
                    }
                    else -> {
                        when (val startupState = startupViewModel.state.value) {
                            is StartupViewState.Init -> {
                                var loadingWallet by remember {
                                    mutableStateOf(
                                        when {
                                            !startWalletImmediately -> null
                                            availableWallets.size == 1 -> availableWallets.entries.firstOrNull()?.value
                                            desiredNodeId != null -> availableWallets[desiredNodeId]
                                            startWalletImmediately -> availableWallets[defaultWallet.value]
                                            else -> null
                                        }
                                    )
                                }
                                when (val wallet = loadingWallet) {
                                    null -> {
                                        WalletsSelector(
                                            wallets = availableWallets,
                                            walletsMetadata = availableWalletMetadata,
                                            activeWalletId = null,
                                            onWalletClick = { appViewModel.switchToWallet(it.walletId) ; loadingWallet = it },
                                            canEdit = false,
                                            modifier = Modifier.padding(horizontal = 24.dp),
                                            topContent = {
                                                Spacer(Modifier.height(64.dp))
                                                Text(text = stringResource(R.string.startup_selector_title), style = MaterialTheme.typography.h4)
                                                Spacer(Modifier.height(16.dp))
                                            },
                                            bottomContent = {
                                                Spacer(Modifier.height(128.dp))
                                            }
                                        )
                                    }
                                    else -> {
                                        val metadata = remember { availableWalletMetadata.getByWalletIdOrDefault(wallet.walletId) }
                                        LoadWallet(
                                            userWallet = wallet,
                                            metadata = metadata,
                                            promptScreenLockImmediately = startWalletImmediately,
                                            doLoadWallet = { userWallet ->
                                                startupViewModel.startupNode(walletId = userWallet.walletId, words = userWallet.words, onStartupSuccess = {
                                                    appViewModel.setActiveWallet(walletId = userWallet.walletId, business = it)
                                                    onWalletReady()
                                                })
                                                loadingWallet = null
                                            },
                                            // only show back-to-selector button if there's more than one wallet
                                            goToWalletSelector = availableWallets.takeIf { it.size > 1 }?.let {
                                                { loadingWallet = null; appViewModel.startWalletImmediately.value = false }
                                            }
                                        )
                                    }
                                }
                            }
                            is StartupViewState.StartingBusiness -> {
                                WalletStartupView(text = stringResource(R.string.startup_starting), metadata = availableWalletMetadata[startupState.walletId])
                            }
                            is StartupViewState.BusinessActive -> {
                                WalletStartupView(text = stringResource(R.string.startup_started), metadata = availableWalletMetadata[startupState.walletId])
                            }
                            is StartupViewState.Error -> {
                                StartBusinessError(error = startupState, onTryAgainClick = {
                                    BusinessManager.stopAllBusinesses()
                                    appViewModel.resetToSelector()
                                    startupViewModel.state.value = StartupViewState.Init
                                })
                            }
                        }
                    }
                }
            }
            is ListWalletState.Error -> {
                ListWalletsError(state = listWalletState, onManualRecoveryClick = onManualRecoveryClick)
            }
        }
    }
}

@Composable
private fun WalletStartupView(
    text: String,
    metadata: UserWalletMetadata?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (metadata == null) {
                Image(
                    painter = painterResource(id = R.drawable.ic_phoenix),
                    contentDescription = "phoenix-icon",
                )
            } else {
                WalletAvatar(avatar = metadata.avatar, fontSize = 32.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StartBusinessError(error: StartupViewState.Error, onTryAgainClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ErrorMessage(
            header = stringResource(R.string.startup_error_start_business),
            details = when (error) {
                is StartupViewState.Error.Generic -> error.cause?.message
            },
            alignment = Alignment.CenterHorizontally,
        )
        Spacer(Modifier.height(16.dp))
        HSeparator(width = 50.dp)
        Spacer(Modifier.height(24.dp))
        BorderButton(
            text = stringResource(R.string.startup_error_try_again),
            icon = R.drawable.ic_reset,
            onClick = onTryAgainClick
        )
        Spacer(Modifier.height(8.dp))
        StartErrorShareLogsButton()
    }
}

@Composable
private fun ListWalletsError(state: ListWalletState.Error, onManualRecoveryClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ErrorMessage(
            header = stringResource(id = R.string.startup_error_generic),
            details = when (state) {
                is ListWalletState.Error.Generic -> state.cause?.message
                is ListWalletState.Error.Serialization -> stringResource(id = R.string.startup_error_serialisation)
                is ListWalletState.Error.DecryptionError.GeneralException -> stringResource(id = R.string.startup_error_decryption_general,
                    "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}"
                )
                is ListWalletState.Error.DecryptionError.KeystoreFailure -> stringResource(id = R.string.startup_error_decryption_keystore,
                    "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}" +
                        (state.cause.cause?.localizedMessage?.take(80) ?: "")
                )
            },
            alignment = Alignment.CenterHorizontally,
        )

        HSeparator(width = 50.dp)
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(
            text = stringResource(id = R.string.startup_error_recovery_button),
            icon = R.drawable.ic_key,
            onClick = onManualRecoveryClick
        )
        Spacer(modifier = Modifier.height(16.dp))
        StartErrorShareLogsButton()
    }
}

@Composable
private fun StartErrorShareLogsButton() {
    val context = LocalContext.current
    val authority = remember { "${BuildConfig.APPLICATION_ID}.provider" }

    TransparentFilledButton(
        text = stringResource(id = R.string.logs_share_button),
        icon = R.drawable.ic_share,
        iconTint = MaterialTheme.typography.caption.color,
        onClick = {
            try {
                val logFile = Logging.exportLogFile(context)
                shareFile(
                    context = context,
                    data = FileProvider.getUriForFile(context, authority, logFile),
                    subject = context.getString(R.string.logs_share_subject),
                    chooserTitle = context.getString(R.string.logs_share_title)
                )
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to export logs...", Toast.LENGTH_SHORT).show()
            }
        },
        textStyle = MaterialTheme.typography.caption,
        shape = CircleShape,
    )
}

@Composable
private fun BoxScope.LoadWallet(
    userWallet: UserWallet,
    metadata: UserWalletMetadata,
    promptScreenLockImmediately: Boolean,
    doLoadWallet: (UserWallet) -> Unit,
    goToWalletSelector: (() -> Unit)?
) {
    val context = LocalContext.current

    val isScreenLockRequired = produceState<Boolean?>(initialValue = null, key1 = userWallet) {
        val userPrefs = DataStoreManager.loadUserPrefsForWallet(context, userWallet.walletId)
        val biometricLockEnabled = userPrefs.getLockBiometricsEnabled.first()
        val customPinLockEnabled = userPrefs.getLockPinEnabled.first()

        value = biometricLockEnabled || customPinLockEnabled
    }

    when (isScreenLockRequired.value) {
        null -> {
            WalletStartupView(text = stringResource(R.string.utils_loading_prefs), metadata = metadata)
        }
        true -> {
            WalletStartupView(text = stringResource(id = R.string.lockprompt_title), metadata = metadata)
            ScreenLockPrompt(
                walletId = userWallet.walletId,
                walletName = metadata.nameOrDefault(),
                promptScreenLockImmediately = promptScreenLockImmediately,
                onUnlock = { doLoadWallet(userWallet) },
                onLock = { },
                goToWalletSelector = goToWalletSelector,
            )
        }
        false -> {
            WalletStartupView(text = stringResource(id = R.string.startup_starting), metadata = metadata)
            LaunchedEffect(Unit) {
                doLoadWallet(userWallet)
            }
        }
    }
}

@Composable
private fun BoxScope.ScreenLockPrompt(
    walletId: WalletId,
    walletName: String,
    promptScreenLockImmediately: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    goToWalletSelector: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userPrefs = DataStoreManager.loadUserPrefsForWallet(context, walletId)

    val isBiometricLockEnabledState = userPrefs.getLockBiometricsEnabled.collectAsState(initial = null)
    val isBiometricLockEnabled = isBiometricLockEnabledState.value
    val isCustomPinLockEnabledState = userPrefs.getLockPinEnabled.collectAsState(initial = null)
    val isCustomPinLockEnabled = isCustomPinLockEnabledState.value

    val promptBiometricLock = {
        val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
            setTitle(context.getString(R.string.lockprompt_title))
            setAllowedAuthenticators(BiometricsHelper.authCreds)
        }.build()
        BiometricsHelper.getPrompt(
            activity = context.findActivity(),
            onSuccess = {
                scope.launch { userPrefs.saveLockPinCodeSuccess() }
                onUnlock()
            },
            onFailure = { onLock() },
            onCancel = { }
        ).authenticate(promptInfo)
    }

    var showPinLockDialog by rememberSaveable { mutableStateOf(false) }
    if (showPinLockDialog) {
        CheckScreenLockPinFlow(
            walletId = walletId,
            onCancel = { showPinLockDialog = false },
            onPinValid = { onUnlock() }
        )
    }

    if (goToWalletSelector != null) {
        BackHandler(enabled = !showPinLockDialog) { goToWalletSelector() }
        TransparentFilledButton(
            icon = R.drawable.ic_arrow_back,
            onClick = goToWalletSelector,
            modifier = Modifier.align(Alignment.TopStart),
            padding = PaddingValues(24.dp),
        )
    }

    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isBiometricLockEnabled == true) {
            Button(
                text = stringResource(id = R.string.lockprompt_biometrics_button),
                icon = R.drawable.ic_fingerprint,
                onClick = promptBiometricLock,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                shape = CircleShape,
                padding = PaddingValues(16.dp),
            )
        }
        if (isCustomPinLockEnabled == true) {
            Button(
                text = stringResource(id = R.string.lockprompt_pin_button),
                icon = R.drawable.ic_pin,
                onClick = { showPinLockDialog = true },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                shape = CircleShape,
                padding = PaddingValues(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }

    when {
        isBiometricLockEnabled == true && isCustomPinLockEnabled == false -> {
            LaunchedEffect(Unit) {
                promptBiometricLock()
            }
        }

        isBiometricLockEnabled == false && isCustomPinLockEnabled == true -> {
            LaunchedEffect(Unit) {
                showPinLockDialog = true
            }
        }

        promptScreenLockImmediately -> {
            LaunchedEffect(key1 = isBiometricLockEnabled, key2 = isCustomPinLockEnabled, ) {
                if (isBiometricLockEnabled == true) {
                    promptBiometricLock()
                } else if (isCustomPinLockEnabled == true) {
                    showPinLockDialog = true
                }
            }
        }
        else -> {}
    }
}
