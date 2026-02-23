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

package fr.acinq.phoenix.android.settings.troubleshoot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.settings.SettingButton


@Composable
fun TroubleshootingView(
    business: PhoenixBusiness,
    walletId: WalletId,
    onBackClick: () -> Unit
) {
    val vm = viewModel<DiagnosticsViewModel>(factory = DiagnosticsViewModel.Factory(application, business, walletId))
    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(R.string.troubleshooting_title)
        )
        ExportLogs(vm)
        ExportDiagnostics(vm)
    }
}

@Composable
private fun ExportDiagnostics(vm: DiagnosticsViewModel) {
    val context = LocalContext.current
    val state by vm.diagnosticExportState.collectAsState()
    CardHeader(text = stringResource(R.string.troubleshooting_diagnostics_header))
    Card {
        SettingButton(
            text = stringResource(id = R.string.troubleshooting_diagnostics_copy_button),
            onClick = { vm.copyDiagnostics() },
            icon = R.drawable.ic_activity,
            enabled = state !is DiagnosticsExportState.Generating
        )
        SettingButton(
            text = when (state) {
                is DiagnosticsExportState.Init, is DiagnosticsExportState.Success -> stringResource(id = R.string.troubleshooting_diagnostics_export_button)
                is DiagnosticsExportState.Generating -> stringResource(id = R.string.troubleshooting_logs_exporting)
                is DiagnosticsExportState.Failure -> stringResource(id = R.string.troubleshooting_diagnostics_error)
            },
            onClick = { vm.shareDiagnostics(context) },
            icon = R.drawable.ic_share,
            enabled = state !is DiagnosticsExportState.Generating
        )
    }
}

@Composable
private fun ExportLogs(
    vm: DiagnosticsViewModel
) {
    val context = LocalContext.current
    val viewState by vm.logsViewState.collectAsState()
    CardHeader(text = stringResource(R.string.troubleshooting_logs_header))
    Card {
        SettingButton(
            text = when (viewState) {
                is LogsExportState.Init -> stringResource(id = R.string.troubleshooting_logs_view_button)
                is LogsExportState.Exporting -> stringResource(id = R.string.troubleshooting_logs_exporting)
                is LogsExportState.Failed -> stringResource(id = R.string.troubleshooting_logs_failed)
            },
            icon = R.drawable.ic_eye,
            enabled = viewState !is LogsExportState.Exporting,
            onClick = { vm.viewLogs(context) }
        )

        val shareState by vm.logsShareState.collectAsState()
        SettingButton(
            text = when (shareState) {
                is LogsExportState.Init -> stringResource(id = R.string.troubleshooting_logs_share_button)
                is LogsExportState.Exporting -> stringResource(id = R.string.troubleshooting_logs_exporting)
                is LogsExportState.Failed -> stringResource(id = R.string.troubleshooting_logs_failed)
            },
            icon = R.drawable.ic_share,
            enabled = shareState !is LogsExportState.Exporting,
            onClick = { vm.shareLogs(context) }
        )
    }
}
