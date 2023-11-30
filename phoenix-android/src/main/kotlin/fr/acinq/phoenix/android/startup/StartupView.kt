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

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.security.SeedFileState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.services.NodeServiceState
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.errorOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.findActivity
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors
import fr.acinq.phoenix.android.utils.shareFile
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.legacy.utils.Wallet


@Composable
fun StartupView(
    appVM: AppViewModel,
    onShowIntro: () -> Unit,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val log = logger("StartupView")
    val context = LocalContext.current
    val serviceState by appVM.serviceState.observeAsState()
    val showIntro by application.internalDataRepository.getShowIntro.collectAsState(initial = null)
    val isLockActiveState by UserPrefs.getIsScreenLockActive(context).collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp
        Spacer(modifier = Modifier.height((screenHeight / 2 - 110).coerceAtLeast(150).dp))
        Image(
            painter = painterResource(id = R.drawable.ic_phoenix),
            contentDescription = "phoenix-icon",
        )
        val isScreenLockEnabled = isLockActiveState
        val isScreenLocked by appVM.isScreenLocked
        if (isScreenLockEnabled == null || showIntro == null) {
            // wait for preferences to load
        } else if (showIntro!!) {
            LaunchedEffect(key1 = Unit) { onShowIntro() }
        } else if (isScreenLockEnabled && isScreenLocked) {
            val promptScreenLock = {
                val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                    setTitle(context.getString(R.string.authprompt_title))
                    setAllowedAuthenticators(BiometricsHelper.authCreds)
                }.build()
                BiometricsHelper.getPrompt(
                    activity = context.findActivity(),
                    onSuccess = { appVM.saveIsScreenLocked(false) },
                    onFailure = { appVM.saveIsScreenLocked(true) },
                    onCancel = { log.debug { "cancelled auth prompt" } }
                ).authenticate(promptInfo)
            }
            LaunchedEffect(key1 = true) {
                promptScreenLock()
            }
            BorderButton(
                text = stringResource(id = R.string.startup_manual_unlock_button),
                icon = R.drawable.ic_shield,
                onClick = promptScreenLock
            )
        } else {
            when (val currentState = serviceState) {
                null, is NodeServiceState.Disconnected -> Text(stringResource(id = R.string.startup_binding_service))
                is NodeServiceState.Off -> DecryptSeedAndStartBusiness(appVM = appVM, onKeyAbsent = onKeyAbsent)
                is NodeServiceState.Init -> Text(stringResource(id = R.string.startup_starting))
                is NodeServiceState.Error -> {
                    ErrorMessage(
                        header = stringResource(id = R.string.startup_error_generic),
                        details = currentState.cause.message
                    )
                }
                is NodeServiceState.Running -> {
                    val legacyAppStatus by LegacyPrefsDatastore.getLegacyAppStatus(context).collectAsState(null)
                    when (legacyAppStatus) {
                        LegacyAppStatus.Unknown -> {
                            Text(stringResource(id = R.string.startup_wait_legacy_check))
                        }
                        LegacyAppStatus.NotRequired -> {
                            LaunchedEffect(true) { onBusinessStarted() }
                        }
                        else -> {
                            Text(stringResource(id = R.string.startup_starting))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecryptSeedAndStartBusiness(
    appVM: AppViewModel,
    onKeyAbsent: () -> Unit,
) {
    val context = LocalContext.current
    val log = logger("StartupView")
    val vm = viewModel<StartupViewModel>()

    val legacyAppStatus by LegacyPrefsDatastore.getLegacyAppStatus(context).collectAsState(null)

    val seedFileState = produceState<SeedFileState>(initialValue = SeedFileState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }.value

    when (seedFileState) {
        is SeedFileState.Unknown -> Text(stringResource(id = R.string.startup_wait))
        is SeedFileState.Absent -> LaunchedEffect(true) { onKeyAbsent() }
        is SeedFileState.Error.Unreadable -> Text(stringResource(id = R.string.startup_error_generic, seedFileState.message ?: ""))
        is SeedFileState.Error.UnhandledSeedType -> Text(stringResource(id = R.string.startup_error_generic, "Unhandled seed type"))
        is SeedFileState.Present -> {
            log.debug { "wallet ready to start with legacyAppStatus=${legacyAppStatus?.name()}" }
            val decryptionState by vm.decryptionState
            when (val state = decryptionState) {
                is StartupDecryptionState.Init -> {
                    // first check if we need to start the legacy application
                    when (legacyAppStatus) {
                        null -> Text(stringResource(id = R.string.startup_wait))
                        is LegacyAppStatus.Required -> {
                            Text(stringResource(id = R.string.startup_wait_legacy_check))
                        }
                        LegacyAppStatus.Unknown -> {
                            if (Wallet.getEclairDBFile(context).exists()) {
                                Text(stringResource(id = R.string.startup_wait_legacy_check))
                                log.debug { "found legacy database file while in unknown legacy status; switching to legacy app" }
                                LaunchedEffect(true) {
                                    LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                                }
                            } else {
                                LaunchedEffect(seedFileState.encryptedSeed) {
                                    appVM.service?.let { vm.decryptSeedAndStart(seedFileState.encryptedSeed, it, checkLegacyChannels = true) }
                                }
                            }
                        }
                        LegacyAppStatus.NotRequired -> {
                            LaunchedEffect(seedFileState.encryptedSeed) {
                                appVM.service?.let { vm.decryptSeedAndStart(seedFileState.encryptedSeed, it, checkLegacyChannels = false) }
                            }
                        }
                    }
                }
                is StartupDecryptionState.DecryptingSeed -> {
                    Text(stringResource(id = R.string.startup_checking_seed))
                }
                is StartupDecryptionState.DecryptionSuccess -> {
                    Text(stringResource(id = R.string.startup_unlocked))
                }
                is StartupDecryptionState.DecryptionError -> {
                    DecryptionFailure(vm = vm, state = state)
                }
                is StartupDecryptionState.SeedInputFallback -> {
                    StartupSeedFallback(state = state, checkSeedFallback = { ctx, words ->
                        vm.checkSeedFallback(ctx, words, onSuccess = { seed ->
                            appVM.service!!.startBusiness(seed, requestCheckLegacyChannels = legacyAppStatus is LegacyAppStatus.Unknown)
                        })
                    })
                }
            }
        }
    }
}

@Composable
private fun DecryptionFailure(
    vm: StartupViewModel,
    state: StartupDecryptionState.DecryptionError,
) {
    val context = LocalContext.current
    ErrorMessage(
        header = when (state) {
            is StartupDecryptionState.DecryptionError.UnhandledVersion -> stringResource(id = R.string.startup_unlock_failure_unhandled_version, state.name)
            is StartupDecryptionState.DecryptionError.Other -> stringResource(id = R.string.startup_unlock_failure)
            is StartupDecryptionState.DecryptionError.KeystoreFailure -> stringResource(id = R.string.startup_unlock_failure_keystore)
        },
        details = when (state) {
            is StartupDecryptionState.DecryptionError.UnhandledVersion -> stringResource(id = R.string.startup_unlock_failure_unhandled_version)
            is StartupDecryptionState.DecryptionError.Other -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}"
            is StartupDecryptionState.DecryptionError.KeystoreFailure -> "[${state.cause::class.java.simpleName}] ${state.cause.localizedMessage ?: ""}" +
                    (state.cause.cause?.localizedMessage?.take(80) ?: "")
        },
        alignment = Alignment.CenterHorizontally,
    )
    HSeparator(width = 50.dp)
    Spacer(modifier = Modifier.height(16.dp))
    BorderButton(
        text = stringResource(id = R.string.startup_unlock_failure_fallback),
        icon = R.drawable.ic_key,
        onClick = { vm.decryptionState.value = StartupDecryptionState.SeedInputFallback.Init }
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

@Composable
private fun StartupSeedFallback(
    state: StartupDecryptionState.SeedInputFallback,
    checkSeedFallback: (Context, List<String>) -> Unit,
) {
    var inputValue by remember { mutableStateOf("") }
    val inputWords: List<String> = remember(inputValue) { inputValue.split("\\s+".toRegex()) }
    val isSeedValid: Boolean? = remember(inputValue) {
        if (inputWords.size < 12) {
            null
        } else {
            try {
                MnemonicCode.validate(inputWords.joinToString(" "))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    Card(
        internalPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.startup_fallback_instructions))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inputValue.isNotBlank()) {
                        FilledButton(
                            onClick = { inputValue = "" },
                            icon = R.drawable.ic_cross,
                            backgroundColor = Color.Transparent,
                            iconTint = MaterialTheme.colors.onSurface,
                            padding = PaddingValues(12.dp),
                        )
                    }
                }
            },
            label = {
                if (inputWords.size <= 12) {
                    Text(text = stringResource(id = R.string.startup_fallback_input_label, inputWords.size))
                } else {
                    Text(text = stringResource(id = R.string.startup_fallback_input_label_error))
                }
            },
            isError = isSeedValid == false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.None
            ),
            colors = if (isSeedValid == false) errorOutlinedTextFieldColors() else outlinedTextFieldColors(),
            visualTransformation = VisualTransformation.None,
            maxLines = 3,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is StartupDecryptionState.SeedInputFallback.Init -> {
                when (isSeedValid) {
                    null -> Text(text = stringResource(id = R.string.startup_fallback_incomplete))
                    false -> Text(text = stringResource(id = R.string.startup_fallback_invalid))
                    true -> {
                        val context = LocalContext.current
                        BorderButton(
                            text = stringResource(id = R.string.startup_fallback_unlock_button),
                            icon = R.drawable.ic_check,
                            onClick = { checkSeedFallback(context, inputWords) }
                        )
                    }
                }
            }
            is StartupDecryptionState.SeedInputFallback.CheckingSeed -> {
                Text(text = stringResource(id = R.string.startup_fallback_checking))
            }
            is StartupDecryptionState.SeedInputFallback.Error.Other -> {
                Text(text = stringResource(id = R.string.startup_fallback_error_default))
            }
            is StartupDecryptionState.SeedInputFallback.Error.SeedDoesNotMatch -> {
                Text(text = stringResource(id = R.string.startup_fallback_error_incorrect_seed))
            }
            is StartupDecryptionState.SeedInputFallback.Success.MatchingData -> {
                TextWithIcon(
                    text = stringResource(id = R.string.startup_fallback_success_match),
                    icon = R.drawable.ic_check_circle
                )
            }
            is StartupDecryptionState.SeedInputFallback.Success.WrittenToDisk -> {
                TextWithIcon(
                    text = stringResource(id = R.string.startup_fallback_success_starting),
                    icon = R.drawable.ic_check_circle
                )
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}
