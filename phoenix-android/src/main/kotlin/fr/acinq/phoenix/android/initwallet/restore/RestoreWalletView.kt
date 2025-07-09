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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
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
    val vm = viewModel<RestoreWalletViewModel>(factory = RestoreWalletViewModel.Factory())

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
                                DisclaimerView(onClickNext = { vm.state = RestoreWalletState.SeedInput.Pending })
                            }

                            is RestoreWalletState.SeedInput -> {
                                SeedInputView(state, vm, onRestoreDone)
                            }
                        }
                    }

                    SeedFileState.Unknown -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Text(stringResource(id = R.string.startup_preparing))
                        }
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
                    details = when (writingState) {
                        is WritingSeedState.Error.Generic -> writingState.cause.localizedMessage ?: writingState.cause::class.java.simpleName
                        is WritingSeedState.Error.SeedAlreadyExist -> "A wallet already exists"
                    },
                    alignment = Alignment.CenterHorizontally,
                )
            }
        }
    }
}
