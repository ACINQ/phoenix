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


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.controllers.config.ElectrumConfiguration
import fr.acinq.phoenix.data.ElectrumConfig
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun ElectrumView() {
    val log = logger("ElectrumView")
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val business = LocalBusiness.current
    val prefElectrumServer = LocalElectrumServer.current
    var showServerDialog by rememberSaveable { mutableStateOf(false) }

    SettingScreen {
        SettingHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.electrum_title),
            subtitle = stringResource(id = R.string.electrum_subtitle)
        )

        Card {
            MVIView(CF::electrumConfiguration) { model, postIntent ->
                if (showServerDialog) {
                    ElectrumServerDialog(
                        initialAddress = prefElectrumServer,
                        onConfirm = { address ->
                            scope.launch {
                                UserPrefs.saveElectrumServer(context, address)
                                postIntent(ElectrumConfiguration.Intent.UpdateElectrumServer(address = address))
                                showServerDialog = false
                            }
                        },
                        onDismiss = {
                            scope.launch {
                                showServerDialog = false
                            }
                        })
                }

                // -- connection detail
                val config = model.configuration
                SettingInteractive(
                    title = stringResource(id = R.string.electrum_server_connection),
                    description = when (model.connection) {
                        is Connection.CLOSED -> if (config is ElectrumConfig.Custom) {
                            stringResource(id = R.string.electrum_not_connected_to_custom, config.server.host)
                        } else {
                            stringResource(id = R.string.electrum_not_connected)
                        }
                        Connection.ESTABLISHING -> if (config is ElectrumConfig.Custom) {
                            stringResource(id = R.string.electrum_connecting_to_custom, config.server.host)
                        } else {
                            stringResource(id = R.string.electrum_connecting)
                        }
                        Connection.ESTABLISHED -> stringResource(id = R.string.electrum_connected, "${model.currentServer?.host}:${model.currentServer?.port}")
                    }
                ) { showServerDialog = true }

                // block height
                if (model.blockHeight > 0) {
                    val height = remember { NumberFormat.getInstance().format(model.blockHeight) }
                    Setting(title = stringResource(id = R.string.electrum_block_height_label), description = height)
                }

                // fee rate
                if (model.feeRate > 0) {
                    Setting(title = stringResource(id = R.string.electrum_fee_rate_label), description = stringResource(id = R.string.electrum_fee_rate, model.feeRate.toString()))
                }
            }

            // xpub
            val xpub = remember { business?.getXpub() ?: "" to "" }
            Setting(title = stringResource(id = R.string.electrum_xpub_label), description = stringResource(id = R.string.electrum_xpub_value, xpub.first, xpub.second))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ElectrumServerDialog(
    initialAddress: ServerAddress?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var useCustomServer by rememberSaveable { mutableStateOf(initialAddress != null) }
    var address by rememberSaveable { mutableStateOf(initialAddress?.run { "$host:$port" } ?: "") }
    var addressError by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = {
                    if (useCustomServer) {
                        if (address.matches("""(.*):(\d*)""".toRegex())) {
                            onConfirm(address)
                        } else {
                            addressError = true
                        }
                    } else {
                        onConfirm("")
                    }
                },
                text = stringResource(id = R.string.btn_save)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            // -- checkbox
            Row(
                modifier = Modifier
                    .clickable { useCustomServer = !useCustomServer }
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = useCustomServer, onCheckedChange = { useCustomServer = it })
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.electrum_dialog_checkbox))
                Spacer(modifier = Modifier.width(24.dp))
            }
            // -- input
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .enableOrFade(useCustomServer)
                    .padding(horizontal = 24.dp)
            ) {
                Text(text = stringResource(id = R.string.electrum_dialog_input), style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                TextInput(
                    modifier = Modifier.fillMaxWidth(),
                    text = address,
                    onTextChange = {
                        addressError = false
                        address = it
                    },
                    enabled = useCustomServer
                )
                if (addressError) {
                    Text("Invalid address, must be <host>:<port>")
                }
            }
            // -- certificate disclaimer
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .enableOrFade(useCustomServer)
                    .background(mutedBgColor())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(stringResource(id = R.string.electrum_dialog_ssl))
            }
        }
    }
}
