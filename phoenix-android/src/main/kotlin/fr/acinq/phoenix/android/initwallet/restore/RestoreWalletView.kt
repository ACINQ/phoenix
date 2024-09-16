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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Checkbox
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.initwallet.WritingSeedState
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.security.SeedFileState
import fr.acinq.phoenix.android.security.SeedManager

@Composable
fun RestoreWalletView(
    onRestoreDone: () -> Unit,
) {
    val nc = navController
    val context = LocalContext.current
    val vm = viewModel<RestoreWalletViewModel>()

    val seedFileState = produceState<SeedFileState>(initialValue = SeedFileState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }

    when (val writingState = vm.writingState) {
        is WritingSeedState.Init -> {
            DefaultScreenLayout {
                DefaultScreenHeader(
                    onBackClick = { nc.popBackStack() },
                    title = stringResource(id = R.string.restore_title),
                )

                when (seedFileState.value) {
                    is SeedFileState.Absent -> {
                        when (val state = vm.state) {
                            is RestoreWalletState.Disclaimer -> {
                                DisclaimerView(onClickNext = { vm.state = RestoreWalletState.InputSeed })
                            }

                            is RestoreWalletState.InputSeed -> {
                                SeedInputView(
                                    vm = vm,
                                    onConfirmClick = { vm.checkSeedAndLocalFiles(context, onSeedWritten = onRestoreDone) }
                                )
                            }

                            is RestoreWalletState.RestoreBackup -> {
                                RestorePaymentsBackupView(
                                    words = state.words,
                                    vm = vm,
                                    onBackupRestoreDone = {
                                        vm.writeSeed(
                                            context = context,
                                            mnemonics = vm.mnemonics.filterNotNull(),
                                            isNewWallet = false,
                                            onSeedWritten = onRestoreDone
                                        )
                                    }
                                )
                            }
                        }
                    }

                    SeedFileState.Unknown -> {
                        Text(stringResource(id = R.string.startup_wait))
                    }

                    else -> {
                        // we should not be here
                        LaunchedEffect(true) {
                            onRestoreDone()
                        }
                    }
                }
            }
        }
        is WritingSeedState.Writing, is WritingSeedState.WrittenToDisk -> {
            BackHandler {}
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ProgressView(text = stringResource(id = R.string.restore_in_progress))
            }
        }
        is WritingSeedState.Error -> {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ErrorMessage(
                    header = stringResource(id = R.string.autocreate_error),
                    details = writingState.e.localizedMessage ?: writingState.e::class.java.simpleName,
                    alignment = Alignment.CenterHorizontally,
                )
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
            text = stringResource(R.string.utils_ack),
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
