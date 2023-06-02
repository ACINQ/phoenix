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

package fr.acinq.phoenix.android.settings.displayseed


import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.safeLet
import kotlinx.coroutines.launch


@Composable
fun DisplaySeedView() {
    val log = logger("DisplaySeedView")
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isBackupDone by InternalData.isManualSeedBackupDone(context).collectAsState(initial = null)
    val isDisclaimerRead by InternalData.isSeedLossDisclaimerRead(context).collectAsState(initial = null)
    val showBackupNotice by InternalData.showSeedBackupNotice(context).collectAsState(initial = false)

    val vm = viewModel<DisplaySeedViewModel>()

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.displayseed_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = annotatedStringResource(id = R.string.displayseed_instructions))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = vm.state.value) {
                is DisplaySeedViewModel.ReadingSeedState.Init -> {
                    Button(
                        text = stringResource(R.string.displayseed_authenticate_button),
                        icon = R.drawable.ic_key,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.readSeed(SeedManager.getSeedState(context)) }
                    )
                }
                is DisplaySeedViewModel.ReadingSeedState.ReadingSeed -> {
                    ProgressView(text = stringResource(id = R.string.displayseed_loading))
                }
                is DisplaySeedViewModel.ReadingSeedState.Decrypted -> {
                    SeedDialog(onDismiss = { vm.state.value = DisplaySeedViewModel.ReadingSeedState.Init }, words = state.words)
                }
                is DisplaySeedViewModel.ReadingSeedState.Error -> {
                    ErrorMessage(
                        header = stringResource(id = R.string.displayseed_error_details),
                        details = state.message,
                        alignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }

        if (showBackupNotice) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                WarningMessage(
                    header = stringResource(id = R.string.displayseed_backup_notice_header),
                    details = stringResource(id = R.string.displayseed_backup_notice_details),
                    alignment = Alignment.CenterHorizontally
                )
            }
        }

        CardHeader(text = stringResource(id = R.string.displayseed_backup_title))
        Card {
            safeLet(isBackupDone, isDisclaimerRead) { backupChecked, disclaimerChecked ->
                Checkbox(
                    text = stringResource(id = R.string.displayseed_backup_checkbox),
                    padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    checked = backupChecked,
                    onCheckedChange = {
                        scope.launch { InternalData.saveManualSeedBackupDone(context, it) }
                    },
                )
                Checkbox(
                    text = stringResource(id = R.string.displayseed_loss_disclaimer_checkbox),
                    padding = PaddingValues(16.dp),
                    checked = disclaimerChecked,
                    onCheckedChange = {
                        scope.launch { InternalData.saveSeedLossDisclaimerRead(context, it) }
                    }
                )
            } ?: ProgressView(text = stringResource(id = R.string.displayseed_loading_prefs))
        }
    }
}

@Composable
private fun SeedDialog(words: List<String>, onDismiss: () -> Unit) {
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
        onDismiss = onDismiss
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
            val indexColor = mutedTextColor
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
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(id = R.string.displayseed_derivation_path),
                style = TextStyle(textAlign = TextAlign.Center, color = mutedTextColor)
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
