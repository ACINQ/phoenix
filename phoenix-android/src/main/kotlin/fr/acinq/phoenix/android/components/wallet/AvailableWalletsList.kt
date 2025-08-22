/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.components.wallet

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.components.auth.screenlock.CheckScreenLockPinFlow
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.datastore.getByNodeId
import fr.acinq.phoenix.android.utils.extensions.findActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AvailableWalletsList(
    modifier: Modifier = Modifier,
    wallets: Map<String, UserWallet>,
    walletsMetadata: Map<String, UserWalletMetadata>,
    activeNodeId: String?,
    onWalletClick: (UserWallet) -> Unit,
    loadNodeImmediately: String? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    topContent: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val walletsList = remember(wallets) { wallets.entries.toList() }
    var isClickEnabled by remember { mutableStateOf(true) }
    var unlockNodeId by remember { mutableStateOf<UserWallet?>(null) }

    fun loadWallet(nodeId: String, userWallet: UserWallet) {
        isClickEnabled = false
        scope.launch {
            val userPrefs = DataStoreManager.loadUserPrefsForNodeId(context, nodeId)
            val biometricLockEnabled = userPrefs.getIsScreenLockBiometricsEnabled.first()
            val customPinLockEnabled = userPrefs.getIsScreenLockPinEnabled.first()

            if (biometricLockEnabled || customPinLockEnabled) {
                unlockNodeId = userWallet
            } else {
                onWalletClick(userWallet)
            }
        }
    }

    LaunchedEffect(loadNodeImmediately) {
        loadNodeImmediately?.let { wallets[loadNodeImmediately] }?.let { userWallet ->
            loadWallet(loadNodeImmediately, userWallet)
        }
    }

    LazyColumn(modifier = modifier, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment) {
        topContent?.let {
            item { it.invoke() }
        }
        items(items = walletsList) { (nodeId, userWallet) ->
            AvailableWalletView(
                nodeId = nodeId,
                metadata = walletsMetadata.getByNodeId(nodeId),
                isCurrent = nodeId == activeNodeId,
                onClick = { loadWallet(nodeId, userWallet) },
            )
            Spacer(Modifier.height(8.dp))
        }
        bottomContent?.let {
            item { it.invoke() }
        }
    }

    unlockNodeId?.let { userWallet ->
        ScreenLockPromptDialog(
            onDismiss = { unlockNodeId = null ; isClickEnabled = true },
            nodeId = userWallet.nodeId,
            walletName = walletsMetadata.getByNodeId(userWallet.nodeId).name(),
            onUnlock = { onWalletClick(userWallet) },
            onLock = { },
        )
    }
}

@Composable
private fun ScreenLockPromptDialog(
    onDismiss: () -> Unit,
    nodeId: String,
    walletName: String,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userPrefs = DataStoreManager.loadUserPrefsForNodeId(context, nodeId)

    val isBiometricLockEnabledState = userPrefs.getIsScreenLockBiometricsEnabled.collectAsState(initial = null)
    val isBiometricLockEnabled = isBiometricLockEnabledState.value
    val isCustomPinLockEnabledState = userPrefs.getIsScreenLockPinEnabled.collectAsState(initial = null)
    val isCustomPinLockEnabled = isCustomPinLockEnabledState.value

    val promptBiometricLock = {
        val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
            setTitle(context.getString(R.string.lockprompt_title2, walletName))
            setAllowedAuthenticators(BiometricsHelper.authCreds)
        }.build()
        BiometricsHelper.getPrompt(
            activity = context.findActivity(),
            onSuccess = {
                scope.launch { userPrefs.saveScreenLockPinCodeSuccess() }
                onUnlock()
            },
            onFailure = { onLock() },
            onCancel = { }
        ).authenticate(promptInfo)
    }

    var showPinLockDialog by rememberSaveable { mutableStateOf(false) }
    if (showPinLockDialog) {
        CheckScreenLockPinFlow(
            userPrefs = userPrefs,
            onCancel = { showPinLockDialog = false ; onDismiss() },
            onPinValid = { onUnlock() }
        )
    }

    when {
        isBiometricLockEnabled == null || isCustomPinLockEnabled == null -> {
            // do nothing
        }

        isBiometricLockEnabled && !isCustomPinLockEnabled -> {
            LaunchedEffect(Unit) {
                promptBiometricLock()
            }
        }

        isCustomPinLockEnabled && !isBiometricLockEnabled -> {
            LaunchedEffect(Unit) {
                showPinLockDialog = true
            }
        }

        else -> {
            ModalBottomSheet(
                onDismiss = onDismiss
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.lockprompt_title2, walletName),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (isBiometricLockEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
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
                    if (isCustomPinLockEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
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
                }
            }
        }
    }
}