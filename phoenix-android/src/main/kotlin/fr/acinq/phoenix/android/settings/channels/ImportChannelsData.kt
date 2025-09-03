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

package fr.acinq.phoenix.android.settings.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.utils.channels.ChannelsImportResult

@Composable
fun ImportChannelsData(
    onBackClick: () -> Unit,
) {
    val peerManager = business.peerManager
    val nodeParamsManager = business.nodeParamsManager
    val vm = viewModel<ImportChannelsDataViewModel>(factory = ImportChannelsDataViewModel.Factory(peerManager, nodeParamsManager))

    var dataInput by remember { mutableStateOf("") }

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.channelimport_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = stringResource(id = R.string.channelimport_instructions))
            Spacer(modifier = Modifier.height(24.dp))
            TextInput(
                text = dataInput,
                onTextChange = {
                    if (it != dataInput) {
                        vm.state.value = ImportChannelsDataState.Init
                    }
                    dataInput = it
                },
                staticLabel = stringResource(id = R.string.channelimport_input_label),
                maxLines = 6,
                enabled = vm.state.value !is ImportChannelsDataState.Importing
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val business = business
            when (val state = vm.state.value) {
                ImportChannelsDataState.Init -> {
                    Button(
                        text = stringResource(id = R.string.channelimport_import_button),
                        icon = R.drawable.ic_restore,
                        onClick = { vm.importData(dataInput.trim(), business) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ImportChannelsDataState.Importing -> {
                    ProgressView(text = stringResource(id = R.string.channelimport_importing),)
                }
                is ImportChannelsDataState.Done -> when (val result = state.result) {
                    is ChannelsImportResult.Success -> {
                        Dialog(
                            onDismiss = {},
                            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
                            buttons = null,
                            buttonsTopMargin = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TextWithIcon(
                                    text = stringResource(id = R.string.channelimport_success),
                                    textStyle = MaterialTheme.typography.body2,
                                    icon = R.drawable.ic_check,
                                    iconTint = positiveColor,
                                )
                                Text(text = stringResource(id = R.string.channelimport_success_restart), textAlign = TextAlign.Center)
                            }
                        }
                    }
                    is ChannelsImportResult.Failure -> {
                        ErrorMessage(
                            header = stringResource(id = R.string.channelimport_error_title),
                            details = when (result) {
                                is ChannelsImportResult.Failure.Generic -> result.error.message
                                is ChannelsImportResult.Failure.MalformedData -> stringResource(id = R.string.channelimport_error_malformed)
                                is ChannelsImportResult.Failure.DecryptionError -> stringResource(id = R.string.channelimport_error_decryption)
                                is ChannelsImportResult.Failure.UnknownVersion -> stringResource(id = R.string.channelimport_error_unknown_version, result.version)
                            },
                            alignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}