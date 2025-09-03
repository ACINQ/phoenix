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

package fr.acinq.phoenix.android.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.settings.SettingButton
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.shareFile

private sealed class LogsExportState {
    object Init: LogsExportState()
    object Exporting: LogsExportState()
    object Failed: LogsExportState()
}
@Composable
fun LogsView() {
    val nc = navController
    val context = LocalContext.current
    val authority = remember { "${BuildConfig.APPLICATION_ID}.provider" }

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.logs_title),
        )
        Card {
            var viewLogState by remember { mutableStateOf<LogsExportState>(LogsExportState.Init) }
            SettingButton(
                text = when (viewLogState) {
                    is LogsExportState.Init -> stringResource(id = R.string.logs_view_button)
                    is LogsExportState.Exporting -> stringResource(id = R.string.logs_exporting)
                    is LogsExportState.Failed -> stringResource(id = R.string.logs_failed)

                },
                icon = R.drawable.ic_eye,
                enabled = viewLogState !is LogsExportState.Exporting,
                onClick = {
                    viewLogState = LogsExportState.Exporting
                    try {
                        val logFile = Logging.exportLogFile(context)
                        val uri = FileProvider.getUriForFile(context, authority, logFile)
                        val localViewIntent: Intent = Intent().apply {
                            action = Intent.ACTION_VIEW
                            type = "text/plain"
                            setDataAndType(uri, "text/plain")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val viewIntent = Intent.createChooser(localViewIntent, context.getString(R.string.logs_view_with))
                        context.startActivity(viewIntent)
                        viewLogState = LogsExportState.Init
                    } catch (e: Exception) {
                        viewLogState = LogsExportState.Failed
                    }
                }
            )

            var shareLogState by remember { mutableStateOf<LogsExportState>(LogsExportState.Init) }
            SettingButton(
                text = when (shareLogState) {
                    is LogsExportState.Init -> stringResource(id = R.string.logs_share_button)
                    is LogsExportState.Exporting -> stringResource(id = R.string.logs_exporting)
                    is LogsExportState.Failed -> stringResource(id = R.string.logs_failed)

                },
                icon = R.drawable.ic_share,
                enabled = shareLogState !is LogsExportState.Exporting,
                onClick = {
                    shareLogState = LogsExportState.Exporting
                    try {
                        val logFile = Logging.exportLogFile(context)
                        shareFile(
                            context = context,
                            data = FileProvider.getUriForFile(context, authority, logFile),
                            subject = context.getString(R.string.logs_share_subject),
                            chooserTitle = context.getString(R.string.logs_share_title)
                        )
                        shareLogState = LogsExportState.Init
                    } catch (e: Exception) {
                        shareLogState = LogsExportState.Failed
                    }
                }
            )
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    LogsView()
}