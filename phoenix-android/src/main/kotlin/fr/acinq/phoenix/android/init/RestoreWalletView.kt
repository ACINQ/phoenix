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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.RestoreWallet

@Composable
fun RestoreWalletView(
    onSeedWritten: () -> Unit
) {
    InitScreen {

        val nc = navController
        InitHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.restore_title),
            subtitle = stringResource(id = R.string.restore_instructions)
        )

        val log = logger("RestoreWallet")
        val context = LocalContext.current

        val vm: InitViewModel =
            viewModel(factory = InitViewModel.Factory(controllerFactory, CF::initialization))

        val keyState = produceState<KeyState>(initialValue = KeyState.Unknown, true) {
            value = SeedManager.getSeedState(context)
        }

        var filteredWords = remember { listOf<String>() }
        val selectedWords = vm.selectedWords

        when (keyState.value) {
            is KeyState.Absent -> {
                MVIView(CF::restoreWallet) { model, postIntent ->
                    Column(
                        modifier = Modifier
                            .padding(start = 24.dp, bottom = 24.dp, end = 24.dp)
                            .fillMaxWidth()
                    ) {

                        var wordsInput by remember { mutableStateOf("") }

                        TextInput(
                            text = wordsInput,
                            onTextChange = {
                                wordsInput = if (it.contains(" ")) {
                                    if (filteredWords.count() > 0) {
                                        vm.addWord(filteredWords[0])
                                    }
                                    ""
                                } else {
                                    it
                                }
                                postIntent(RestoreWallet.Intent.FilterWordList(predicate = wordsInput))
                            },
                            enabled = vm.selectedWords.count() < 12,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        )

                        when (model) {

                            is RestoreWallet.Model.Ready -> {}
                            is RestoreWallet.Model.InvalidMnemonics -> {
                                Text(stringResource(R.string.restore_error))
                            }
                            is RestoreWallet.Model.FilteredWordlist -> {
                                filteredWords = model.words
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
                                            vm.selectedWords.toList(),
                                            false,
                                            onSeedWritten
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            items(filteredWords) {

                                val apiString = AnnotatedString.Builder()
                                apiString.pushStyle(
                                    style = SpanStyle(
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                                apiString.append(it)
                                Text(
                                    modifier = Modifier.clickable(enabled = true) {
                                        if (selectedWords.count() < 12) {
                                            vm.addWord(it)
                                            wordsInput = ""
                                            postIntent(RestoreWallet.Intent.FilterWordList(predicate = wordsInput))
                                        }
                                    },
                                    text = apiString.toAnnotatedString(),

                                    )
                            }
                        }

                        Column {

                            Spacer(Modifier.height(24.dp))
                            HSeparator()
                            Spacer(Modifier.height(24.dp))

                            Row {
                                Column(Modifier.weight(1f)) {

                                    for (index in 0..5) {
                                        WordRow(
                                            wordNumber = index + 1,
                                            word = if (selectedWords.count() > index) selectedWords[index] else "",
                                            crossVisible = selectedWords.count() > index,
                                            onCrossClick = {
                                                vm.removeRangeWords(
                                                    index,
                                                    selectedWords.count()
                                                )
                                            }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }

                                Column(Modifier.weight(1f)) {

                                    for (index in 6..11) {
                                        WordRow(
                                            wordNumber = index + 1,
                                            word = if (selectedWords.count() > index) selectedWords[index] else "",
                                            crossVisible = selectedWords.count() > index,
                                            onCrossClick = {
                                                vm.removeRangeWords(
                                                    index,
                                                    selectedWords.count()
                                                )
                                            }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            BorderButton(
                                text = R.string.restore_import_button,
                                icon = R.drawable.ic_check_circle,
                                onClick = {
                                    postIntent(RestoreWallet.Intent.Validate(selectedWords.toList()))
                                },
                                enabled = selectedWords.count() == 12
                            )
                        }
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
}

@Composable
fun WordRow(
    wordNumber: Int,
    word: String,
    crossVisible: Boolean,
    onCrossClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = String.format("#%02d", wordNumber),
            style = MaterialTheme.typography.subtitle2
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = word,
            style = MaterialTheme.typography.h5
        )
        Spacer(modifier = Modifier.weight(1f))

        if (crossVisible) {
            Image(
                modifier = Modifier.clickable(
                    onClick = onCrossClick
                ),
                painter = painterResource(id = R.drawable.ic_cross),
                contentDescription = "",

                )
            Spacer(Modifier.width(8.dp))
        }
    }
}
