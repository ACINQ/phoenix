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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration

@Composable
fun ForceCloseView(
    onBackClick: () -> Unit
) {
    val log = logger("ForceCloseView")
    var showConfirmationDialog by remember { mutableStateOf(false) }

    MVIView(CF::closeChannelsConfiguration) { model, postIntent ->
        DefaultScreenLayout {
            DefaultScreenHeader(
                onBackClick = onBackClick,
                title = stringResource(id = R.string.forceclose_title),
            )
            when (model) {
                is CloseChannelsConfiguration.Model.Loading -> {
                    Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.mutualclose_loading))
                    }
                }
                is CloseChannelsConfiguration.Model.Ready -> {
                    Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = annotatedStringResource(id = R.string.forceclose_instructions))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.forceclose_address_label))
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            TextWithIcon(text = model.address, icon = R.drawable.ic_chain, textStyle = monoTypo().copy(fontSize = 14.sp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        FilledButton(
                            text = if (model.channels.isEmpty()) {
                                stringResource(id = R.string.forceclose_no_channels)
                            } else {
                                stringResource(id = R.string.forceclose_button)
                            },
                            icon = R.drawable.ic_cross_circle,
                            backgroundColor = negativeColor(),
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = model.channels.isNotEmpty(),
                            onClick = { showConfirmationDialog = true }
                        )
                    }
                    if (showConfirmationDialog) {
                        ConfirmDialog(
                            message = stringResource(R.string.mutualclose_confirm),
                            onDismiss = { showConfirmationDialog = false },
                            onConfirm = {
                                postIntent(CloseChannelsConfiguration.Intent.ForceCloseAllChannels)
                                showConfirmationDialog = false
                            }
                        )
                    }
                }
                is CloseChannelsConfiguration.Model.ChannelsClosed -> {
                    Card(internalPadding = PaddingValues(16.dp)) {
                        Text(text = stringResource(R.string.mutualclose_done, model.channels.size))
                    }
                }
            }
        }
    }
}