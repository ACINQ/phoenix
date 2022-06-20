/*
 * Copyright 2021 ACINQ SAS
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


import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class SeedViewState() {
    object Init : SeedViewState()
    object ReadingSeed : SeedViewState()
    data class ShowSeed(val words: List<String>) : SeedViewState()
    data class Error(val message: String) : SeedViewState()
}

@Composable
fun SeedView() {
    val log = logger("SeedView")
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SeedViewState>(SeedViewState.Init) }

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.displayseed_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = annotatedStringResource(id = R.string.displayseed_instructions))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card {
            when (val s = state) {
                is SeedViewState.Init -> {
                    SettingButton(text = R.string.displayseed_authenticate_button, icon = R.drawable.ic_key) {
                        state = SeedViewState.ReadingSeed
                        scope.launch {
                            val keyState = SeedManager.getSeedState(context)
                            when {
                                keyState is KeyState.Present && keyState.encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                                    val words = EncryptedSeed.toMnemonics(keyState.encryptedSeed.decrypt())
                                    delay(300)
                                    state = SeedViewState.ShowSeed(words)
                                }
                                keyState is KeyState.Error.Unreadable -> {
                                    state = SeedViewState.Error(context.getString(R.string.displayseed_error_details, keyState.message ?: "n/a"))
                                }
                                else -> {
                                    log.info { "unable to read seed in state=$keyState" }
                                    // TODO: handle errors
                                }
                            }
                        }
                    }
                }
                is SeedViewState.ReadingSeed -> {
                    Row (modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconWithText(icon = R.drawable.ic_key, text = stringResource(id = R.string.displayseed_loading))
                    }
                }
                is SeedViewState.ShowSeed -> {
                    SeedDialog(onDismiss = { state = SeedViewState.Init }, words = s.words)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeedDialog(words: List<String>, onDismiss: () -> Unit) {
    val log = logger("SeedDialog")
    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.displayseed_dialog_header),
                style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (words.isEmpty()) {
                Text("reading seed...")
            } else {
                val groupedWords: List<Pair<String, String>> = remember(words) {
                    words.mapIndexed { i, w ->
                        if (i + (words.size / 2) < words.size) {
                            words[i] to words[i + (words.size / 2)]
                        } else {
                            null
                        }
                    }.filterNotNull()
                }
                val typo = MaterialTheme.typography.body1
                val indexColor = mutedTextColor()
                val indexStyle = remember(typo) { typo.copy(fontSize = 12.sp, textAlign = TextAlign.End, color = indexColor) }
                val wordStyle = remember(typo) { typo.copy(fontWeight = FontWeight.Bold) }

                groupedWords.forEachIndexed { index, wordPair ->
                    Row(
                        modifier = Modifier
                            .wrapContentHeight()
                            .widthIn(max = 300.dp)
                    ) {
                        Cell(text = "#${index + 1}", modifier = Modifier.width(24.dp), textStyle = indexStyle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Cell(text = wordPair.first, modifier = Modifier.width(100.dp), textStyle = wordStyle)
                        Spacer(modifier = Modifier.width(8.dp))
                        Cell(text = "#${index + words.size / 2 + 1}", modifier = Modifier.width(24.dp), textStyle = indexStyle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Cell(text = wordPair.second, modifier = Modifier.width(100.dp), textStyle = wordStyle)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(id = R.string.displayseed_derivation_path),
                style = TextStyle(textAlign = TextAlign.Center, color = mutedTextColor())
            )
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.body1
) {
    Text(
        text = text,
        modifier = modifier.alignBy(FirstBaseline),
        style = textStyle
    )
}
