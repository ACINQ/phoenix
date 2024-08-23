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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.utils.negativeColor

@Composable
fun ColumnScope.SeedInputView(
    vm: RestoreWalletViewModel,
    onConfirmClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var filteredWords by remember { mutableStateOf(emptyList<String>()) }
    val enteredWords = vm.mnemonics.filterNot { it.isNullOrBlank() }

    val isSeedValid = remember(enteredWords) {
        if (enteredWords.size != 12) {
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
                        onInputChange = { filteredWords = vm.filterWordsMatching(it) },
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
            onRemoveWordFrom = { vm.removeWordsFromMnemonic(it) }
        )
    }


    Spacer(modifier = Modifier.height(16.dp))

    BorderButton(
        text = stringResource(id = R.string.btn_next),
        icon = R.drawable.ic_arrow_next,
        onClick = {
            focusManager.clearFocus()
            onConfirmClick()
        },
        enabled = isSeedValid == true,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
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