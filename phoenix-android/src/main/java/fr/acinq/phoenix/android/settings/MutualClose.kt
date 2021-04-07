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


import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.InputText
import fr.acinq.phoenix.android.components.ScreenBody
import fr.acinq.phoenix.android.components.ScreenHeader
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration

@Composable
fun MutualCloseView() {
    val log = logger()
    val nc = navController
    ScreenHeader(
        onBackClick = { nc.popBackStack() },
        title = stringResource(id = R.string.closechannels_mutual_title),
    )
    ScreenBody {
        MVIView(CF::closeChannelsConfiguration) { model, postIntent ->
            when (model) {
                is CloseChannelsConfiguration.Model.Loading -> Text(text = stringResource(id = R.string.closechannels_checking_channels))
                is CloseChannelsConfiguration.Model.Ready -> {
                    var address by remember { mutableStateOf("") }
                    val balance = Satoshi(model.channels.map { it.balance }.sum()).toMilliSatoshi()
                    Text(text = stringResource(id = R.string.closechannels_channels_recap, model.channels.size, balance.toPrettyString(LocalBitcoinUnit.current)))
                    Spacer(Modifier.height(24.dp))
                    InputText(
                        text = address,
                        onTextChange = { address = it },
                        maxLines = 3,
                        label = { Text(text = stringResource(R.string.closechannels_mutual_input_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    BorderButton(
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
                is CloseChannelsConfiguration.Model.ChannelsClosed -> {
                    Text(text = stringResource(R.string.closechannels_message_done, model.channels.size))
                }
            }
        }
    }
}
