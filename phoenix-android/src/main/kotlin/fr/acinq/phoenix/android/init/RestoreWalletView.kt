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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.SeedFileState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.init.RestoreWallet

sealed class RestoreWalletViewState {
    object Disclaimer : RestoreWalletViewState()
    object Restore : RestoreWalletViewState()
}

@Composable
fun RestoreWalletView(
    onSeedWritten: () -> Unit
) {
    val log = logger("RestoreWalletView")
    val nc = navController
    val context = LocalContext.current
    val vm: InitViewModel = viewModel(factory = InitViewModel.Factory(controllerFactory, CF::initialization))

    val seedFileState = produceState<SeedFileState>(initialValue = SeedFileState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.restore_title),
        )
        when (seedFileState.value) {
            is SeedFileState.Absent -> {
                when (val state = vm.restoreWalletState) {
                    is RestoreWalletViewState.Disclaimer -> {
                        DisclaimerView(onClickNext = { vm.restoreWalletState = RestoreWalletViewState.Restore })
                    }
                    is RestoreWalletViewState.Restore -> {
                        SeedInputView(vm = vm, onSeedWritten = onSeedWritten)
                    }
                }
            }
            SeedFileState.Unknown -> {
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
private fun DisclaimerView(
    onClickNext: () -> Unit
) {
    var hasCheckedWarning by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(stringResource(R.string.restore_disclaimer_message))
        }
        Checkbox(
            text = stringResource(R.string.restore_disclaimer_checkbox),
            checked = hasCheckedWarning,
            onCheckedChange = { hasCheckedWarning = it },
        )
        BorderButton(
            text = stringResource(id = R.string.restore_disclaimer_next),
            icon = R.drawable.ic_arrow_next,
            onClick = (onClickNext),
            enabled = hasCheckedWarning,
        )
    }
}

@Composable
private fun SeedInputView(
    vm: InitViewModel,
    onSeedWritten: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var filteredWords by remember { mutableStateOf(emptyList<String>()) }
    val writingState = vm.writingState
    val enteredWords = vm.mnemonics.filterNot { it.isNullOrBlank() }

    MVIView(CF::restoreWallet) { model, postIntent ->

        val isSeedValid = remember(enteredWords.size, model) {
            if (model is RestoreWallet.Model.InvalidMnemonics) {
                false
            } else if (enteredWords.size != 12) {
                null
            } else {
                try {
                    MnemonicCode.validate(vm.mnemonics.joinToString(" "))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        when (model) {
            is RestoreWallet.Model.Ready -> {}
            is RestoreWallet.Model.InvalidMnemonics -> {}
            is RestoreWallet.Model.FilteredWordlist -> {
                filteredWords = model.words
            }
            is RestoreWallet.Model.ValidMnemonics -> {
                LaunchedEffect(model.seed) {
                    vm.writeSeed(
                        context = context,
                        mnemonics = model.mnemonics,
                        isNewWallet = false,
                        onSeedWritten = onSeedWritten
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                internalPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.restore_instructions))
                Column(
                    modifier = Modifier.heightIn(min = 100.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (isSeedValid) {
                        null -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            WordInputView(
                                wordIndex = enteredWords.size + 1,
                                filteredWords = filteredWords,
                                onInputChange = { postIntent(RestoreWallet.Intent.FilterWordList(it)) },
                                onWordSelected = { vm.appendWordToMnemonic(it) },
                            )
                        }
                        false -> {
                            WarningMessage(
                                header = stringResource(id = R.string.restore_seed_invalid),
                                details = stringResource(id = R.string.restore_seed_invalid_details),
                                alignment = Alignment.CenterHorizontally,
                            )
                        }
                        true -> {
                            SuccessMessage(
                                header = stringResource(id = R.string.restore_seed_valid),
                                details = stringResource(id = R.string.restore_seed_valid_details),
                                alignment = Alignment.CenterHorizontally,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                WordsTable(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp)
                        .widthIn(max = 350.dp),
                    words = vm.mnemonics.toList(),
                    onRemoveWordFrom = {
                        if (writingState !is WritingSeedState.Writing) {
                            vm.removeWordsFromMnemonic(it)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            when (writingState) {
                is WritingSeedState.Error -> {
                    ErrorMessage(
                        header = stringResource(id = R.string.autocreate_error),
                        details = writingState.e.localizedMessage ?: writingState.e::class.java.simpleName,
                        alignment = Alignment.CenterHorizontally,
                    )
                }
                is WritingSeedState.Init -> {
                    BorderButton(
                        text = stringResource(id = R.string.restore_import_button),
                        icon = R.drawable.ic_check_circle,
                        onClick = {
                            focusManager.clearFocus()
                            postIntent(RestoreWallet.Intent.Validate(vm.mnemonics.filterNotNull()))
                        },
                        enabled = isSeedValid == true,
                    )
                }
                is WritingSeedState.Writing, is WritingSeedState.WrittenToDisk -> {
                    ProgressView(text = stringResource(R.string.restore_in_progress))
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun WordInputView(
    wordIndex: Int,
    filteredWords: List<String>,
    onInputChange: (String) -> Unit,
    onWordSelected: (String) -> Unit,
) {
    var inputValue by remember { mutableStateOf("") }
    OutlinedTextField(
        value = inputValue,
        onValueChange = { newValue ->
            if (newValue.endsWith(" ") && filteredWords.isNotEmpty()) {
                // hitting space acts like completing the input - we select the first word available
                filteredWords.firstOrNull()?.let { onWordSelected(it) }
                inputValue = ""
            } else {
                inputValue = newValue
            }
            onInputChange(inputValue.trim())
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.None
        ),
        visualTransformation = VisualTransformation.None,
        label = {
            Text(
                text = stringResource(R.string.restore_input_label, wordIndex),
                style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary)
            )
        },
        maxLines = 1,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // Section showing the list of filtered words. Clicking on a word acts like completing the text input.
    if (filteredWords.isEmpty()) {
        Text(
            text = if (inputValue.length > 2) stringResource(id = R.string.restore_input_invalid) else "",
            style = MaterialTheme.typography.body1.copy(color = negativeColor),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredWords) {
                Clickable(
                    enabled = wordIndex <= 12,
                    onClick = {
                        onWordSelected(it)
                        onInputChange("")
                        inputValue = ""
                    },
                ) {
                    Text(text = it, style = MaterialTheme.typography.body1.copy(textDecoration = TextDecoration.Underline), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun WordsTable(
    modifier: Modifier,
    words: List<String?>,
    onRemoveWordFrom: (Int) -> Unit,
) {
    Row(modifier) {
        Column(modifier = Modifier.weight(1f)) {
            words.take(6).forEachIndexed { index, word ->
                WordRow(
                    wordNumber = index + 1,
                    word = word,
                    onRemoveWordClick = { onRemoveWordFrom(index) }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            words.subList(6, 12).forEachIndexed { index, word ->
                WordRow(
                    wordNumber = index + 6 + 1,
                    word = word,
                    onRemoveWordClick = { onRemoveWordFrom(index + 6) }
                )
            }
        }
    }
}

@Composable
private fun WordRow(
    wordNumber: Int,
    word: String?,
    onRemoveWordClick: () -> Unit
) {
    Clickable(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        enabled = !word.isNullOrBlank(),
        onClick = onRemoveWordClick,
        internalPadding = PaddingValues(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format("#%02d -", wordNumber),
                style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                modifier = Modifier.alignBy(FirstBaseline)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = word ?: "...",
                style = if (word != null) MaterialTheme.typography.body2 else MaterialTheme.typography.caption,
                modifier = Modifier.alignBy(FirstBaseline)
            )
            if (!word.isNullOrBlank()) {
                Spacer(Modifier.weight(1f))
                PhoenixIcon(resourceId = R.drawable.ic_cross, tint = MaterialTheme.colors.primary)
            }
        }
    }
}
