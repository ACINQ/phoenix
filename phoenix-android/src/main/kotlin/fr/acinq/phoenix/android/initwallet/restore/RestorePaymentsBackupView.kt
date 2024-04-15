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

package fr.acinq.phoenix.android.initwallet.restore

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage

@Composable
fun RestorePaymentsBackupView(
    words: List<String>,
    vm: RestoreWalletViewModel,
    onBackupRestoreDone: () -> Unit,
) {
    val context = LocalContext.current

    BackHandler { /* Disable back button */ }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                vm.restorePaymentsBackup(context, words = words, uri = uri, onBackupRestoreDone = onBackupRestoreDone)
            } else {
                vm.restoreBackupState = RestoreBackupState.Done.Failure.UnresolvedContent
            }
        }
    )

    Card(
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "You can manually restore your payments history by importing a Phoenix backup file, if one exists.")
        Text(text = "Look for a phoenix.bak file in your Documents folder. Note that older versions of Phoenix (before v2.3.0) did not generate backups.")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val state = vm.restoreBackupState
        when (state) {
            is RestoreBackupState.Init, is RestoreBackupState.Done.Failure -> {
                if (state is RestoreBackupState.Done.Failure) {
                    ErrorMessage(
                        modifier = Modifier.fillMaxWidth(),
                        header = "This file cannot be restored",
                        details = when (state) {
                            is RestoreBackupState.Done.Failure.UnresolvedContent -> "The file cannot be opened, or is empty."
                            is RestoreBackupState.Done.Failure.CannotWriteFiles -> "Cannot load this backup into Phoenix."
                            is RestoreBackupState.Done.Failure.ContentDoesNotMatch -> "This backup does not match the wallet being restored."
                            is RestoreBackupState.Done.Failure.CannotDecrypt -> "This file cannot be decrypted. It does not match the wallet being restored."
                            is RestoreBackupState.Done.Failure.Error -> state.e.message
                        },
                        alignment = Alignment.CenterHorizontally
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                FilledButton(
                    text = "Browse local files",
                    icon = R.drawable.ic_inspect,
                    onClick = {
                        vm.restoreBackupState = RestoreBackupState.Checking.LookingForFile
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(16.dp)
                )
            }
            is RestoreBackupState.Checking.LookingForFile -> {
                ProgressView(text = "Looking for file...")
            }
            is RestoreBackupState.Checking.Decrypting -> {
                ProgressView(text = "Decrypting file...")
            }
            RestoreBackupState.Done.BackupRestored -> {
                SuccessMessage(header = "Backup has been successfully restored")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            text = "Skip and proceed to wallet",
            icon = R.drawable.ic_cancel,
            onClick = onBackupRestoreDone,
            modifier = Modifier.fillMaxWidth(),
            enabled = state is RestoreBackupState.Init || state is RestoreBackupState.Done.Failure,
            shape = CircleShape
        )
    }
}