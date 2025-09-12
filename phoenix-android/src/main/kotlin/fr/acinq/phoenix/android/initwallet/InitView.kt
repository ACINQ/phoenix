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

package fr.acinq.phoenix.android.initwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.settings.SettingSwitch
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.settings.electrum.ElectrumServerDialog


@Composable
fun InitNewWallet(
    navController: NavController,
    onCreateWalletClick: () -> Unit,
    onRestoreWalletClick: () -> Unit,
) {
    val initGraphEntry = remember(navController.previousBackStackEntry) { navController.getBackStackEntry(Screen.InitWalletGraph.route) }
    val initViewModel = viewModel<InitViewModel>(factory = InitViewModel.Factory(application), viewModelStoreOwner = initGraphEntry)

    var showInitOptionsDialog by remember { mutableStateOf(false) }

    DefaultScreenLayout(isScrollable = false) {
        Row {
            Spacer(Modifier.weight(1f))
            Button(
                icon = R.drawable.ic_settings,
                onClick = { showInitOptionsDialog = true }
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledButton(
                text = stringResource(id = R.string.initwallet_create),
                icon = R.drawable.ic_fire,
                onClick = onCreateWalletClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            HSeparator(width = 80.dp)
            Spacer(modifier = Modifier.height(16.dp))
            BorderButton(
                text = stringResource(id = R.string.initwallet_restore),
                icon = R.drawable.ic_restore,
                onClick = onRestoreWalletClick
            )
        }
    }

    if (showInitOptionsDialog) {
        InitWalletOptionsDialog(onDismiss = { showInitOptionsDialog = false }, initViewModel = initViewModel)
    }
}

@Composable
private fun InitWalletOptionsDialog(
    onDismiss: () -> Unit,
    initViewModel: InitViewModel,
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        internalPadding = PaddingValues(),
        containerColor = MaterialTheme.colors.background,
    ) {
        val isTorEnabled by initViewModel.isTorEnabled
        val customElectrumAddress by initViewModel.customElectrumServer
        var showCustomServerDialog by remember { mutableStateOf(false) }

        Card {
            SettingSwitch(
                title = stringResource(id = R.string.tor_settings_switch_label),
                icon = R.drawable.ic_tor_shield,
                enabled = true,
                isChecked = isTorEnabled,
                onCheckChangeAttempt = {
                    initViewModel.isTorEnabled.value = it
                }
            )
        }

        Card {
            SettingSwitch(
                title = stringResource(R.string.initwallet_electrum_option),
                description = customElectrumAddress?.let { "${it.server.host}:${it.server.port}"},
                enabled = true,
                isChecked = customElectrumAddress != null,
                onCheckChangeAttempt = { showCustomServerDialog = true }
            )
        }

        Spacer(Modifier.height(8.dp))
        TransparentFilledButton(
            text = stringResource(R.string.btn_close),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))

        if (showCustomServerDialog) {
            ElectrumServerDialog(
                initialConfig = customElectrumAddress,
                onConfirm = {
                    initViewModel.customElectrumServer.value = it
                    showCustomServerDialog = false
                },
                onDismiss = { showCustomServerDialog = false }
            )
        }
    }
}
