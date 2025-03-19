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

package fr.acinq.phoenix.android.initwallet.restore

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.MutedFilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.feedback.InfoMessage

@Composable
fun RestorePaymentsDbButton(restorePaymentDbState: RestorePaymentsDbState, onImportDbClick: (uri: Uri) -> Unit) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportDbClick(it) } }
    )

    when (restorePaymentDbState) {
        is RestorePaymentsDbState.Init -> {
            Clickable(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextWithIcon(text = stringResource(R.string.restore_payments_button), icon = R.drawable.ic_plus_circle, textStyle = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(4.dp))
                    Text(text = stringResource(R.string.restore_payments_button_details), style = MaterialTheme.typography.subtitle2)
                }
            }
        }
        is RestorePaymentsDbState.Importing -> {
            Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressView(text = stringResource(R.string.utils_loading_data), padding = PaddingValues(0.dp))
            }
        }
        is RestorePaymentsDbState.Failure -> {
            Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                InfoMessage(
                    header = stringResource(R.string.restore_payments_error_header),
                    details = when (restorePaymentDbState) {
                        is RestorePaymentsDbState.Failure.Error -> restorePaymentDbState.e.localizedMessage
                        is RestorePaymentsDbState.Failure.InvalidSeed -> null // the invalid seed case is already handled in the parent's view
                        is RestorePaymentsDbState.Failure.CannotDecryptDatabase -> stringResource(R.string.restore_payments_error_encryption)
                        is RestorePaymentsDbState.Failure.UnresolvedDatabaseFile -> stringResource(R.string.restore_payments_error_writing)
                        is RestorePaymentsDbState.Failure.CannotWriteDatabaseFile -> stringResource(R.string.restore_payments_error_writing)
                    },
                    padding = PaddingValues(0.dp), alignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                MutedFilledButton(
                    text = stringResource(R.string.restore_payments_error_button),
                    icon = R.drawable.ic_swap,
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is RestorePaymentsDbState.Success -> {
            Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.restore_payments_success),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = restorePaymentDbState.fileName,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                MutedFilledButton(
                    text = stringResource(R.string.restore_payments_success_button),
                    icon = R.drawable.ic_swap,
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}