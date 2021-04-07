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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.ElectrumConfig

@Composable
fun ElectrumView() {
    val log = logger()
    val nc = navController
    val context = LocalContext.current

    ScreenHeader(
        onBackClick = { nc.popBackStack() },
        title = stringResource(id = R.string.electrum_title),
        subtitle = stringResource(id = R.string.electrum_subtitle)
    )
    ScreenBody(padding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        MVIView(CF::electrumConfiguration) { model, postIntent ->
            val showServerDialog = remember { mutableStateOf(false) }
            if (showServerDialog.value) {
                ElectrumServerDialog(
                    onConfirm = {
                        postIntent(ElectrumConfiguration.Intent.UpdateElectrumServer(it))
                        Prefs.saveElectrumServer(context, it)
                        showServerDialog.value = false
                    },
                    onCancel = { showServerDialog.value = false })
            }
            val connection = model.connection
            val config = model.configuration
            val title = when {
                connection == Connection.CLOSED && config is ElectrumConfig.Random -> stringResource(id = R.string.electrum_not_connected)
                connection == Connection.CLOSED && config is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_not_connected_to_custom, config.server.host)
                connection == Connection.ESTABLISHING && config is ElectrumConfig.Random -> stringResource(id = R.string.electrum_connecting)
                connection == Connection.ESTABLISHING && config is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_connecting_to_custom, config.server.host)
                connection == Connection.ESTABLISHED && config != null -> stringResource(id = R.string.electrum_connected, config.server.host)
                else -> stringResource(id = R.string.electrum_not_connected)
            }
            val description = when (config) {
                is ElectrumConfig.Random -> stringResource(id = R.string.electrum_server_desc_random)
                is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_server_desc_custom)
                else -> null
            }
            Setting(title = title, description = description, onClick = { showServerDialog.value = true })
            if (model.blockHeight > 0) {
                Setting(title = stringResource(id = R.string.electrum_block_height_label), description = model.blockHeight.toString())
            }
            if (model.feeRate > 0) {
                Setting(title = stringResource(id = R.string.electrum_fee_rate_label), description = stringResource(id = R.string.electrum_fee_rate, model.feeRate.toString()))
            }
        }
        val xpub = business.getXpub() ?: "" to ""
        Setting(title = stringResource(id = R.string.electrum_xpub_label), description = stringResource(id = R.string.electrum_xpub_value, xpub.first, xpub.second))
    }
}

@Composable
private fun ElectrumServerDialog(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val prefElectrumServer = Prefs.getElectrumServer(LocalContext.current)
    var useCustomServer by remember { mutableStateOf(prefElectrumServer != null) }
    var address by remember { mutableStateOf(prefElectrumServer?.run { "$host:$port" } ?: "") }
    Dialog(
        onDismiss = onCancel,
        buttons = {
            Button(onClick = { onCancel() }, text = stringResource(id = R.string.btn_cancel), padding = PaddingValues(8.dp))
            Button(onClick = { onConfirm(if (useCustomServer) address else "") }, text = stringResource(id = R.string.btn_ok), padding = PaddingValues(8.dp))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.padding(horizontal = 24.dp)) {
                Checkbox(checked = useCustomServer, onCheckedChange = { useCustomServer = it })
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = stringResource(id = R.string.electrum_dialog_checkbox))
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .enableOrFade(useCustomServer)
                    .padding(horizontal = 24.dp)
            ) {
                Text(text = stringResource(id = R.string.electrum_dialog_input), style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                InputText(text = address, onTextChange = { address = it }, enabled = useCustomServer)
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mutedBgColor())
                    .enableOrFade(useCustomServer)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(stringResource(id = R.string.electrum_dialog_ssl))
            }
        }
    }
}

@Composable
fun Setting(modifier: Modifier = Modifier, title: String, description: String?, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(modifier)
            .padding(start = 50.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.subtitle2)
        Text(description ?: "", style = MaterialTheme.typography.caption)
    }
}