/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.history

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.utils.Converter.toBasicAbsoluteDateString
import fr.acinq.phoenix.android.utils.Converter.toBasicAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.shareFile


@Composable
fun PaymentsExportView(
    onBackClick: () -> Unit,
) {
    val vm: PaymentsExportViewModel = viewModel(factory = PaymentsExportViewModel.Factory(dbManager = business.databaseManager, walletManager = business.walletManager))
    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(R.string.payments_export_title))
        ExportAsCsvView(vm)
        ExportDatabaseView(vm)
    }
}

@Composable
private fun ExportAsCsvView(
    vm: PaymentsExportViewModel
) {
    val context = LocalContext.current

    val startTimestamp = vm.startTimestampMillis
    val endTimestamp = vm.endTimestampMillis

    CardHeader(text = stringResource(R.string.payments_export_csv_header))
    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = stringResource(id = R.string.payments_export_csv_instructions))
        Spacer(Modifier.height(16.dp))
        if (startTimestamp != null) {
            CalendarView(
                label = stringResource(id = R.string.payments_export_csv_start_label),
                initialTimestampMillis = startTimestamp,
                onDateSelected = {
                    vm.reset()
                    vm.startTimestampMillis = it
                },
                enabled = vm.csvExportState !is CsvExportState.Generating,
            )
        }
        Spacer(Modifier.height(8.dp))
        CalendarView(
            label = stringResource(id = R.string.payments_export_csv_end_label),
            initialTimestampMillis = endTimestamp,
            onDateSelected = {
                vm.reset()
                // timestamp returned by the calendar is at the start of day
                vm.endTimestampMillis = it + DateUtils.DAY_IN_MILLIS - 1_000
            },
            enabled = vm.csvExportState !is CsvExportState.Generating,
        )
        Spacer(Modifier.height(8.dp))
        SwitchView(
            text = stringResource(id = R.string.payments_export_csv_context_label),
            enabled = vm.csvExportState !is CsvExportState.Generating,
            checked = vm.includesOriginDestination,
            onCheckedChange = {
                vm.reset()
                vm.includesOriginDestination = it
            },
            modifier = Modifier.padding(vertical = 4.dp),
        )
        SwitchView(
            text = stringResource(id = R.string.payments_export_csv_description_label),
            enabled = vm.csvExportState !is CsvExportState.Generating,
            checked = vm.includesDescription && vm.includesNotes,
            onCheckedChange = {
                vm.reset()
                vm.includesDescription = it
                vm.includesNotes = it
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = vm.csvExportState) {
                CsvExportState.Init, is CsvExportState.Failed, is CsvExportState.NoData -> {
                    if (startTimestamp == null) {
                        ErrorMessage(header = stringResource(id = R.string.payments_export_csv_no_payments))
                    } else if (startTimestamp > endTimestamp) {
                        ErrorMessage(header = stringResource(id = R.string.payments_export_csv_invalid_timestamps))
                    } else {
                        if (state is CsvExportState.Failed) {
                            ErrorMessage(
                                header = stringResource(id = R.string.payments_export_csv_error),
                                details = state.error.localizedMessage,
                            )
                        } else if (state is CsvExportState.NoData) {
                            ErrorMessage(header = stringResource(id = R.string.payments_export_csv_no_data))
                        }
                        FilledButton(
                            text = stringResource(id = R.string.payments_export_csv_generate_button),
                            icon = R.drawable.ic_build,
                            onClick = { vm.generateCSV(context) },
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(12.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                is CsvExportState.Generating -> {
                    ProgressView(text = stringResource(id = R.string.payments_export_csv_in_progress))
                }
                is CsvExportState.Success -> {
                    Button(
                        text = stringResource(id = R.string.payments_export_csv_share_button),
                        icon = R.drawable.ic_share,
                        iconTint = MaterialTheme.colors.onPrimary,
                        onClick = {
                            shareFile(
                                context = context,
                                data = state.uri,
                                subject = context.getString(
                                    R.string.payments_export_csv_share_subject,
                                    startTimestamp?.toBasicAbsoluteDateString() ?: "", endTimestamp.toBasicAbsoluteDateString()
                                ),
                                chooserTitle = context.getString(R.string.payments_export_csv_share_title),
                                mimeType = "text/csv"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = MaterialTheme.colors.primary,
                        textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary)
                    )
                    Spacer(Modifier.height(8.dp))
                    MutedFilledButton(
                        text = stringResource(R.string.payments_export_csv_copy_button),
                        icon = R.drawable.ic_copy,
                        onClick = { copyToClipboard(context, data = state.content) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportDatabaseView(
    vm: PaymentsExportViewModel
) {
    val context = LocalContext.current
    val nodeParams = business.nodeParamsManager.nodeParams.collectAsState()
    val nodeId = nodeParams.value?.nodeId?.toString()?.take(8)
    val saveDatabaseLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        uri?.let { vm.vacuumDatabase(context, it) }
    }

    CardHeader(text = stringResource(R.string.payments_export_database_header))
    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = stringResource(R.string.payments_export_database_instructions))
        Spacer(Modifier.height(16.dp))

        when (val state = vm.databaseExportState) {
            is DatabaseExportState.Init, is DatabaseExportState.Failed -> {
                if (state is DatabaseExportState.Failed) {
                    ErrorMessage(
                        header = stringResource(R.string.payments_export_database_failed),
                        padding = PaddingValues(0.dp),
                        alignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }
                FilledButton(
                    text = stringResource(R.string.payments_export_database_button),
                    icon = R.drawable.ic_arrow_down_circle,
                    onClick = { saveDatabaseLauncher.launch("phoenix-payments-$nodeId-${currentTimestampMillis().toBasicAbsoluteDateTimeString()}.sqlite") },
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(12.dp),
                    enabled = nodeId != null,
                    shape = RoundedCornerShape(10.dp),
                )
            }
            is DatabaseExportState.Exporting -> {
                ProgressView(text = stringResource(R.string.utils_processing_data), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            is DatabaseExportState.Success -> {
                Button(
                    text = stringResource(id = R.string.payments_export_csv_share_button),
                    icon = R.drawable.ic_share,
                    iconTint = MaterialTheme.colors.onPrimary,
                    onClick = {
                        shareFile(
                            context = context,
                            data = state.uri,
                            subject = context.getString(R.string.payments_export_csv_share_title),
                            chooserTitle = context.getString(R.string.payments_export_csv_share_title),
                            mimeType = "text/csv"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = MaterialTheme.colors.primary,
                    textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary)
                )
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.payments_export_database_success), style = MaterialTheme.typography.subtitle2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
