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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.ColumnScreen
import fr.acinq.phoenix.android.components.RowHeader
import fr.acinq.phoenix.android.components.SettingButton
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.logger

@Composable
fun LogsView() {
    val log = logger("LogsView")
    val nc = navController
    val context = LocalContext.current

    ColumnScreen {
        RowHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.logs_title),
            subtitle = stringResource(id = R.string.logs_subtitle)
        )
        Card {

            SettingButton(
                text = R.string.logs_view_button,
                icon = R.drawable.ic_eye,
                onClick = {

                    val logFile = Logging.getLastLogFile(context)
                    val authority = "fr.acinq.phoenix.android.provider"

                    val uri = FileProvider.getUriForFile(context, authority, logFile)
                    val localViewIntent: Intent = Intent().apply {
                        action = Intent.ACTION_VIEW
                        type = "text/plain"
                        setDataAndType(uri, "text/plain")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val viewIntent = Intent.createChooser(localViewIntent, context.getString(R.string.logs_view_with))
                    context.startActivity(viewIntent)
                }
            )
            SettingButton(
                text = R.string.logs_share_button,
                icon = R.drawable.ic_share,

                onClick = {

                    val logFile = Logging.getLastLogFile(context)
                    val authority = "fr.acinq.phoenix.android.provider"

                    val uri = FileProvider.getUriForFile(context, authority, logFile)
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
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