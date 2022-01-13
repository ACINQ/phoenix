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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration

@Composable
fun MutualCloseView() {
    val log = logger("MutualCloseView")
    val nc = navController
    SettingScreen {
        SettingHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.closechannels_mutual_title),
            subtitle = stringResource(id = R.string.closechannels_mutual_subtitle),
        )
        MVIView(CF::closeChannelsConfiguration) { model, postIntent ->
            when (model) {
                is CloseChannelsConfiguration.Model.Loading -> Text(text = stringResource(id = R.string.closechannels_checking_channels))
                is CloseChannelsConfiguration.Model.Ready -> {
                    if (model.channels.isEmpty()) {
                        Card(internalPadding = PaddingValues(16.dp)) {
                            Text(text = stringResource(id = R.string.closechannels_channels_none))
                        }
                    } else {
                        var address by remember { mutableStateOf("") }
                        val balance = Satoshi(model.channels.map { it.balance }.sum()).toMilliSatoshi()
                        Card(internalPadding = PaddingValues(16.dp)) {
                            Text(text = stringResource(id = R.string.closechannels_channels_recap, model.channels.size, balance.toPrettyString(LocalBitcoinUnit.current)))
                        }
                        Card(internalPadding = PaddingValues(16.dp)) {
                            Text(
                                text = stringResource(id = R.string.closechannels_mutual_input_hint),
                                style = MaterialTheme.typography.subtitle1,
                            )
                            TextInput(
                                text = address,
                                onTextChange = { address = it },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Card {
                            SettingButton(
                                text = R.string.closechannels_mutual_button,
                                icon = R.drawable.ic_cross_circle,
                                enabled = address.isNotBlank(),
                                onClick = {
                                    if (address.isNotBlank()) {
                                        postIntent(CloseChannelsConfiguration.Intent.MutualCloseAllChannels(address))
                                    }
                                }
                            )
                        }
                    }
                }
                is CloseChannelsConfiguration.Model.ChannelsClosed -> {
                    Card(internalPadding = PaddingValues(16.dp)) {
                        Text(text = stringResource(R.string.closechannels_message_done, model.channels.size))
                    }
                }
            }
        }
    }
}
