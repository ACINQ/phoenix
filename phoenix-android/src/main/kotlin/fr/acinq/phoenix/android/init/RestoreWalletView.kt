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

package fr.acinq.phoenix.android.init

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.RestoreWallet
import fr.acinq.phoenix.data.BitcoinUnit

@Composable
fun RestoreWalletView(
    onSeedWritten: () -> Unit
) {
    val log = logger("RestoreWallet")
    val context = LocalContext.current

    val vm: InitViewModel =
        viewModel(factory = InitViewModel.Factory(controllerFactory, CF::initialization))

    val keyState = produceState<KeyState>(initialValue = KeyState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }

    //val payments = homeViewModel.paymentsFlow.collectAsState().value.values.toList()
    //var filteredWord = remember { mutableStateOf(listOf<String>()) }
    var filteredWord = remember { listOf<String>() }

    when (keyState.value) {
        is KeyState.Absent -> {
            MVIView(CF::restoreWallet) { model, postIntent ->
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    var wordsInput by remember { mutableStateOf("") }

                    Text(stringResource(R.string.restore_instructions))
                    TextInput(
                        text = wordsInput,
                        onTextChange = {
                            wordsInput = it
                            postIntent(RestoreWallet.Intent.FilterWordList(predicate = it))
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )

                    when (model) {

                        is RestoreWallet.Model.Ready -> {
                            /*
                            var showDisclaimer by remember { mutableStateOf(true) }
                            var hasCheckedWarning by remember { mutableStateOf(false) }
                            if (showDisclaimer) {
                                Text(stringResource(R.string.restore_disclaimer_message))
                                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(hasCheckedWarning, onCheckedChange = { hasCheckedWarning = it })
                                    Spacer(Modifier.width(16.dp))
                                    Text(stringResource(R.string.restore_disclaimer_checkbox))
                                }
                                BorderButton(
                                    text = R.string.restore_disclaimer_next,
                                    icon = R.drawable.ic_arrow_next,
                                    onClick = { showDisclaimer = false },
                                    enabled = hasCheckedWarning
                                )
                            } else {
                                Text(stringResource(R.string.restore_instructions))
                                TextInput(
                                    text = wordsInput,
                                    onTextChange = { wordsInput = it },
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp)
                                )
                                BorderButton(
                                    text = R.string.restore_import_button,
                                    icon = R.drawable.ic_check_circle,
                                    onClick = { postIntent(RestoreWallet.Intent.Validate(wordsInput.split(" "))) },
                                    enabled = wordsInput.isNotBlank()
                                )
                            }
                            */
                        }
                        is RestoreWallet.Model.InvalidMnemonics -> {
                            Text(stringResource(R.string.restore_error))
                        }
                        is RestoreWallet.Model.FilteredWordlist -> {
                            filteredWord = model.words
                        }

                        is RestoreWallet.Model.ValidMnemonics -> {
                            val writingState = vm.writingState
                            if (writingState is WritingSeedState.Error) {
                                Text(
                                    stringResource(
                                        id = R.string.autocreate_error,
                                        writingState.e.localizedMessage
                                            ?: writingState.e::class.java.simpleName
                                    )
                                )
                            } else {
                                Text(stringResource(R.string.restore_in_progress))
                                LaunchedEffect(keyState) {
                                    vm.writeSeed(
                                        context,
                                        wordsInput.split(" "),
                                        false,
                                        onSeedWritten
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(id = R.string.restore_in_progress))
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        // Add 5 items
                        itemsIndexed(filteredWord) { index, item ->

                            val apiString = AnnotatedString.Builder()
                            apiString.pushStyle(
                                style = SpanStyle(
                                    //color = Color.Blue,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                            apiString.append(item)
                            Text(
                                modifier = Modifier.clickable(enabled = true) {
                                    val index = index
                                    val index2 = index
                                },
                                text = apiString.toAnnotatedString(),
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#1",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#7",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#2",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#8",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#3",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#9",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#4",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#10",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#5",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#11",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    Row {
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#6",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#12",
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                    }

                    BorderButton(
                        text = R.string.restore_import_button,
                        icon = R.drawable.ic_check_circle,
                        onClick = { postIntent(RestoreWallet.Intent.Validate(wordsInput.split(" "))) },
                        enabled = wordsInput.isNotBlank()
                    )
                }
            }
        }
        KeyState.Unknown -> {
            Text(stringResource(id = R.string.startup_wait))
        }
        else -> {
            // we should not be here
            Text(stringResource(id = R.string.startup_wait))
            LaunchedEffect(true) {
                onSeedWritten()
            }
        }
    }
}