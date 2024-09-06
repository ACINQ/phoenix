/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.MainActivityFoss
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.SwitchView
import fr.acinq.phoenix.android.components.settings.SettingSwitch
import fr.acinq.phoenix.android.internalData
import fr.acinq.phoenix.android.services.HeadlessActions
import fr.acinq.phoenix.android.services.NodeServiceFoss
import fr.acinq.phoenix.android.utils.findActivity
import kotlinx.coroutines.launch

@Composable
fun ManageHeadlessView(appViewModel: AppViewModel) {
    CardHeader(text = stringResource(id = R.string.background_header))
    Card(modifier = Modifier.fillMaxWidth()) {
        val service = appViewModel.service as NodeServiceFoss?
        val activity = LocalContext.current.findActivity() as MainActivityFoss
        val scope = rememberCoroutineScope()
        val internalPrefs = internalData

        var showConfirmDialog by remember { mutableStateOf(false) }

        SettingSwitch(
            title = {
                Text(
                    text = stringResource(id = R.string.background_mode_title),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f)
                )
            },
            subtitle = when (service?.isHeadless) {
                null -> null
                true -> if (service.wakeLock?.isHeld == true) {
                    { Text(stringResource(id = R.string.background_mode_on_wakelock), style = MaterialTheme.typography.subtitle2) }
                } else {
                    { Text(stringResource(id = R.string.background_mode_on_no_wakelock), style = MaterialTheme.typography.subtitle2) }
                }
                false -> {
                    { Text(text = stringResource(id = R.string.background_mode_off), style = MaterialTheme.typography.subtitle2) }
                }
            },
            icon = R.drawable.ic_phone_tech,
            enabled = service?.isHeadless != null,
            isChecked = service?.isHeadless ?: false,
            onCheckChangeAttempt = { runHeadless ->
                scope.launch {
                    if (runHeadless) {
                        showConfirmDialog = true
                    } else {
                        scope.launch {
                            activity.headlessServiceAction(HeadlessActions.Stop)
                            internalPrefs.saveBackgroundServiceModeDisabled()
                        }
                    }
                }
            }
        )

        if (showConfirmDialog) {
            ConfirmHeadlessDialog(
                onDismiss = { showConfirmDialog = false },
                onConfirm = { withWakeLock ->
                    scope.launch {
                        activity.headlessServiceAction(if (withWakeLock) HeadlessActions.Start.WithWakeLock else HeadlessActions.Start.NoWakeLock)
                        internalPrefs.saveBackgroundServiceModeEnabled(withWakeLock)
                        showConfirmDialog = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmHeadlessDialog(
   onDismiss: () -> Unit,
   onConfirm: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
    ) {
        var withWakeLock by remember { mutableStateOf(true) }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .sizeIn(minHeight = 400.dp, maxHeight = 700.dp)
                .padding(horizontal = 24.dp),
        ) {
            Text(text = stringResource(id = R.string.background_mode_dialog_title), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.background_mode_dialog_desc))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.background_mode_dialog_perfs_desc))
            Spacer(modifier = Modifier.height(12.dp))
            SwitchView(
                text = stringResource(id = R.string.background_mode_dialog_wakelock_title),
                textStyle = MaterialTheme.typography.body2,
                description = stringResource(id = R.string.background_mode_dialog_wakelock_desc),
                checked = withWakeLock,
                onCheckedChange = { withWakeLock = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.background_mode_dialog_alternative_title), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.background_mode_dialog_alternative_desc))
            Spacer(modifier = Modifier.height(32.dp))
            FilledButton(
                text = stringResource(id = R.string.btn_start),
                icon = R.drawable.ic_start,
                onClick = { onConfirm(withWakeLock) },
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledButton(
                text = stringResource(id = R.string.btn_cancel),
                icon = R.drawable.ic_check,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                backgroundColor = Color.Transparent,
                textStyle = MaterialTheme.typography.button
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}