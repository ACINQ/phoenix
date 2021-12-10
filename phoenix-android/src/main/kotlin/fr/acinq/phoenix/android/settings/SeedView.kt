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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.ScreenBody
import fr.acinq.phoenix.android.components.ScreenHeader
import fr.acinq.phoenix.android.mutedTextColor
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class SeedViewState() {
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

    Column {
        ScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.displayseed_title))
        ScreenBody {
            Text(text = annotatedStringResource(id = R.string.displayseed_instructions))
            Spacer(modifier = Modifier.height(24.dp))
            when (val s = state) {
                is SeedViewState.Init -> {
                    BorderButton(onClick = {
                        state = SeedViewState.ReadingSeed
                        scope.launch {
                            val keyState = SeedManager.getSeedState(context)
                            when {
                                keyState is KeyState.Present && keyState.encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                                    val seed = EncryptedSeed.toMnemonics(keyState.encryptedSeed.decrypt())
                                    delay(300)
                                    state = SeedViewState.ShowSeed(seed)
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
                    }, text = R.string.displayseed_authenticate_button, icon = R.drawable.ic_key)
                }
                is SeedViewState.ReadingSeed -> {
                    Text(stringResource(id = R.string.displayseed_loading), modifier = Modifier.padding(12.dp))
                }
                is SeedViewState.ShowSeed -> {
                    SeedDialog(onClose = { state = SeedViewState.Init }, words = s.words)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeedDialog(words: List<String>, onClose: () -> Unit) {
    val log = logger("SeedDialog")
    Dialog(
        onDismiss = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.displayseed_dialog_header),
                style = TextStyle(textAlign = TextAlign.Center)
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
