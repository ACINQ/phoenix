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
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.utils.Converter.toBasicAbsoluteDateString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.android.utils.shareFile


@Composable
fun CsvExportView(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val vm: CsvExportViewModel = viewModel(factory = CsvExportViewModel.Factory(dbManager = business.databaseManager))
    val startTimestamp = vm.startTimestampMillis
    val endTimestamp = vm.endTimestampMillis

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(R.string.payments_export_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = stringResource(id = R.string.payments_export_instructions))
            Spacer(Modifier.height(16.dp))
            if (startTimestamp != null) {
                CalendarView(
                    label = stringResource(id = R.string.payments_export_start_label),
                    initialTimestampMillis = startTimestamp,
                    onDateSelected = {
                        vm.reset()
                        vm.startTimestampMillis = it
                    },
                    enabled = vm.state !is CsvExportState.Generating,
                )
            }
            Spacer(Modifier.height(8.dp))
            CalendarView(
                label = stringResource(id = R.string.payments_export_end_label),
                initialTimestampMillis = endTimestamp,
                onDateSelected = {
                    vm.reset()
                    // timestamp returned by the calendar is at the start of day
                    vm.endTimestampMillis = it + DateUtils.DAY_IN_MILLIS - 1_000
                },
                enabled = vm.state !is CsvExportState.Generating,
            )
            Spacer(Modifier.height(8.dp))
            SwitchView(
                text = stringResource(id = R.string.payments_export_context_label),
                enabled = vm.state !is CsvExportState.Generating,
                checked = vm.includesOriginDestination,
                onCheckedChange = {
                    vm.reset()
                    vm.includesOriginDestination = it
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            SwitchView(
                text = stringResource(id = R.string.payments_export_description_label),
                enabled = vm.state !is CsvExportState.Generating,
                checked = vm.includesDescription && vm.includesNotes,
                onCheckedChange = {
                    vm.reset()
                    vm.includesDescription = it
                    vm.includesNotes = it
                },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = vm.state) {
                CsvExportState.Init, is CsvExportState.Failed, is CsvExportState.NoData -> {
                    if (startTimestamp == null) {
                        ErrorMessage(header = stringResource(id = R.string.payments_export_no_payments))
                    } else if (startTimestamp > endTimestamp) {
                        ErrorMessage(header = stringResource(id = R.string.payments_export_invalid_timestamps))
                    } else {
                        if (state is CsvExportState.Failed) {
                            ErrorMessage(
                                header = stringResource(id = R.string.payments_export_error),
                                details = state.error.localizedMessage,
                            )
                        } else if (state is CsvExportState.NoData) {
                            ErrorMessage(header = stringResource(id = R.string.payments_export_no_data))
                        }
                        Button(
                            text = stringResource(id = R.string.payments_export_generate_button),
                            icon = R.drawable.ic_build,
                            onClick = { vm.generateCSV(context) },
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(16.dp)
                        )
                    }
                }
                is CsvExportState.Generating -> {
                    ProgressView(text = stringResource(id = R.string.payments_export_in_progress))
                }
                is CsvExportState.Success -> {
                    TextWithIcon(
                        text = stringResource(id = R.string.payments_export_success),
                        icon = R.drawable.ic_check,
                        iconTint = positiveColor,
                        modifier = Modifier.padding(16.dp)
                    )
                    val subject = remember {
                        context.getString(
                            R.string.payments_export_share_subject,
                            startTimestamp?.toBasicAbsoluteDateString() ?: "N/A", endTimestamp.toBasicAbsoluteDateString()
                        )
                    }
                    Button(
                        text = stringResource(id = R.string.btn_copy),
                        icon = R.drawable.ic_copy,
                        onClick = { copyToClipboard(context, data = state.content, dataLabel = subject) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        text = stringResource(R.string.payments_export_share_button),
                        icon = R.drawable.ic_share,
                        onClick = {
                            shareFile(
                                context, data = state.uri,
                                subject = subject,
                                chooserTitle = context.getString(R.string.payments_export_share_title),
                                mimeType = "text/csv"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
