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


import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.ctrl.config.ChannelsConfiguration

@Composable
fun ChannelsView() {
    val log = logger()
    val nc = navController

    val showChannelDialog = remember { mutableStateOf<ChannelsConfiguration.Model.Channel?>(null) }
    showChannelDialog.value?.run {
        ChannelDialog(
            onDismiss = { showChannelDialog.value = null },
            channel = this,
        )
    }
    ScreenHeader(
        onBackClick = { nc.popBackStack() },
        title = stringResource(id = R.string.listallchannels_title),
    )
    MVIView(CF::channelsConfiguration) { model, _ ->
        if (model.channels.isEmpty()) {
            ScreenBody {
                Text(text = stringResource(id = R.string.listallchannels_no_channels))
            }
        } else {
            ScreenBody(padding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(model.channels) {
                        ChannelLine(channel = it, onClick = { showChannelDialog.value = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLine(channel: ChannelsConfiguration.Model.Channel, onClick: () -> Unit) {
    val balance = Satoshi(channel.commitments?.first ?: 0L).toMilliSatoshi()
    val capacity = Satoshi(channel.commitments?.second ?: 0L).toMilliSatoshi()
    Row(modifier = Modifier
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (channel.isOk) positiveColor() else negativeColor(),
            modifier = Modifier.size(6.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = channel.stateName,
            modifier = Modifier.weight(1.0f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        AmountView(amount = balance, showUnit = false)
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "/")
        Spacer(modifier = Modifier.width(2.dp))
        AmountView(amount = capacity, unitTextStyle = MaterialTheme.typography.caption)
    }
}

@Composable
private fun ChannelDialog(onDismiss: () -> Unit, channel: ChannelsConfiguration.Model.Channel) {
    val context = LocalContext.current
    Dialog(
        onDismiss = onDismiss,
        buttons = {
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { copyToClipboard(context, channel.json, "channel data") }, icon = R.drawable.ic_copy)
                Button(onClick = { share(context, channel.json, subject = "") }, icon = R.drawable.ic_share)
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.txUrl))) }, text = stringResource(id = R.string.listallchannels_funding_tx))
                Spacer(modifier = Modifier.weight(1.0f))
                Button(onClick = onDismiss, text = stringResource(id = R.string.listallchannels_close))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
        ) {
            Text(
                text = channel.json,
                modifier = Modifier
                    .background(mutedBgColor())
                    .padding(8.dp)
                    .weight(1.0f)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState()),
                style = monoTypo(),
            )

        }
    }
}
