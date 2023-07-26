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

package fr.acinq.phoenix.android.settings.channels

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.utils.migrations.ChannelsConsolidationHelper
import fr.acinq.phoenix.utils.migrations.ChannelsConsolidationResult

@Composable
fun ChannelsConsolidationView(
    onBackClick: () -> Unit,
) {
    val loggerFactory = business.loggerFactory
    val chain = business.chain
    val peerManager = business.peerManager
    val vm = viewModel<ChannelsConsolidationViewModel>(factory = ChannelsConsolidationViewModel.Factory(loggerFactory, chain, peerManager))

    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val ignoreDust by remember { mutableStateOf(false) }

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.consolidation_title))
        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = stringResource(id = R.string.consolidation_instructions))
        }
        val channels = channelsState?.values?.toList()
        if (channels.isNullOrEmpty() || !ChannelsConsolidationHelper.canConsolidate(channels)) {
            Text(text = stringResource(id = R.string.consolidation_not_needed))
        } else {
            ConsolidationSteps(state = vm.state.value, onConsolidationClick = { vm.consolidate(ignoreDust) })
        }
    }
}

@Composable
private fun ConsolidationSteps(
    state: ChannelsConsolidationState,
    onConsolidationClick: () -> Unit,
) {
    when (state) {
        ChannelsConsolidationState.Init -> {
            Card {
                Button(
                    text = stringResource(id = R.string.consolidation_button),
                    onClick = onConsolidationClick,
                )
            }
        }
        ChannelsConsolidationState.InProgress -> {
            Card {
                ProgressView(text = stringResource(id = R.string.consolidation_in_progress))
            }
        }
        is ChannelsConsolidationState.Done -> {
            when (val result = state.result) {
                is ChannelsConsolidationResult.Success -> {
                    Card {
                        Text(text = stringResource(id = R.string.consolidation_success, result.channels.size))
                    }
                }
                is ChannelsConsolidationResult.Failure -> {
                    Card {
                        ErrorMessage(
                            header = stringResource(id = R.string.consolidation_failure_title),
                            details = when (result) {
                                is ChannelsConsolidationResult.Failure.Generic -> {
                                    result.error.message ?: result.error::class.java.simpleName
                                }
                                is ChannelsConsolidationResult.Failure.ChannelsBeingCreated -> {
                                    stringResource(id = R.string.consolidation_failure_channels_being_created)
                                }
                                is ChannelsConsolidationResult.Failure.DustChannels -> {
                                    stringResource(id = R.string.consolidation_failure_channels_dust, result.dustChannels.size, result.allChannels)
                                }
                                is ChannelsConsolidationResult.Failure.InvalidClosingScript -> {
                                    stringResource(id = R.string.consolidation_failure_invalid_address)
                                }
                                is ChannelsConsolidationResult.Failure.NoChannelsAvailable -> {
                                    stringResource(id = R.string.consolidation_failure_no_channels)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
