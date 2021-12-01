/*
 * Copyright 2020 ACINQ SAS
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
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.InputText
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.Initialization
import fr.acinq.phoenix.controllers.init.RestoreWallet


@Composable
fun InitWallet(
    onCreateWalletClick: () -> Unit,
    onRestoreWalletClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledButton(
            text = R.string.initwallet_create,
            icon = R.drawable.ic_fire,
            onClick = onCreateWalletClick
        )
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(
            text = R.string.initwallet_restore,
            icon = R.drawable.ic_restore,
            onClick = onRestoreWalletClick
        )
    }
}

@Composable
fun CreateWalletView(
    appVM: AppViewModel,
    onSeedWritten: () -> Unit
) {
    val log = logger("CreateWallet")
    val context = LocalContext.current
    if (!keyState.isReady()) {
        MVIView(CF::initialization) { model, postIntent ->
            val entropy = remember { Lightning.randomBytes(16) }
            LaunchedEffect(key1 = entropy) {
                log.info { "generating wallet" }
                postIntent(Initialization.Intent.GenerateWallet(entropy))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (model) {
                    is Initialization.Model.Ready -> {
                        Text("Generating wallet...")
                    }
                    is Initialization.Model.GeneratedWallet -> {
                        LaunchedEffect(keyState) {
                            log.info { "a new wallet has been generated, writing to disk..." }
                            appVM.writeSeed(context, model.mnemonics)
                            log.info { "seed successfully written to disk" }
                            onSeedWritten()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreWalletView(
    appVM: AppViewModel,
    onSeedWritten: () -> Unit
) {
    val log = logger("RestoreWallet")
    val context = LocalContext.current
    if (appVM.keyState.isReady()) {
        onSeedWritten()
    } else {
        MVIView(CF::restoreWallet) { model, postIntent ->
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                var wordsInput by remember { mutableStateOf("") }
                when (model) {
                    is RestoreWallet.Model.Ready -> {
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
                            InputText(
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
                    }
                    is RestoreWallet.Model.InvalidMnemonics -> {
                        Text(stringResource(R.string.restore_error))
                    }
                    is RestoreWallet.Model.ValidMnemonics -> {
                        Text(stringResource(R.string.restore_in_progress))
                        LaunchedEffect(keyState) {
                            log.info { "restored wallet seed is valid" }
                            appVM.writeSeed(context, wordsInput.split(" "))
                            log.info { "seed successfully written to disk" }
                            onSeedWritten()
                        }
                    }
                    else -> {
                        Text("Please hold...")
                    }
                }
            }
        }
    }
}
