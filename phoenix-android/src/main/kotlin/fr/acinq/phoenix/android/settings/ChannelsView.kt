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
import androidx.compose.foundation.text.selection.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.controllers.config.ChannelsConfiguration


@Composable
fun ChannelsView() {
    val log = logger("ChannelsView")
    val nc = navController
    val context = LocalContext.current

    val showChannelDialog = remember { mutableStateOf<ChannelsConfiguration.Model.Channel?>(null) }
    showChannelDialog.value?.let {
        ChannelDialog(
            onDismiss = { showChannelDialog.value = null },
            channel = it,
        )
    }

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.listallchannels_title),
        )
        MVIView(CF::channelsConfiguration) { model, _ ->
            Column(modifier = Modifier.weight(1f)) {
                if (model.channels.isEmpty()) {
                    Card(internalPadding = PaddingValues(16.dp)) {
                        Text(text = stringResource(id = R.string.listallchannels_no_channels))
                    }
                } else {
                    Card {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(model.channels) {
                                ChannelLine(channel = it, onClick = { showChannelDialog.value = it })
                            }
                        }
                    }
                }
            }
            Card {
                Row {
                    Text(text = stringResource(id = R.string.listallchannels_node_id), modifier = Modifier.padding(16.dp))
                    Text(
                        text = model.nodeId, style = MaterialTheme.typography.body2, overflow = TextOverflow.Ellipsis,
                        maxLines = 1, modifier = Modifier
                            .padding(vertical = 16.dp)
                            .weight(1f)
                    )
                    Button(
                        icon = R.drawable.ic_copy,
                        onClick = { copyToClipboard(context, model.nodeId, context.getString(R.string.listallchannels_node_id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLine(channel: ChannelsConfiguration.Model.Channel, onClick: () -> Unit) {
    val balance = channel.localBalance ?: 0.msat
    val capacity = channel.capacity ?: 0.sat
    Row(modifier = Modifier
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (channel.isOk) positiveColor() else negativeColor(),
            modifier = Modifier.size(6.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = when (channel.stateName) {
                Normal::class.simpleName -> stringResource(id = R.string.state_normal)
                Closed::class.simpleName -> stringResource(id = R.string.state_closed)
                Closing::class.simpleName -> stringResource(id = R.string.state_closing)
                Syncing::class.simpleName -> stringResource(id = R.string.state_sync)
                Offline::class.simpleName -> stringResource(id = R.string.state_offline)
                ShuttingDown::class.simpleName -> stringResource(id = R.string.state_shutdown)
                WaitForFundingConfirmed::class.simpleName, WaitForAcceptChannel::class.simpleName,
                WaitForChannelReady::class.simpleName, WaitForFundingSigned::class.simpleName,
                WaitForFundingCreated::class.simpleName -> stringResource(id = R.string.state_wait_confirmed)
                WaitForOpenChannel::class.simpleName -> stringResource(id = R.string.state_wait_open)
                else -> channel.stateName
            },
            modifier = Modifier.weight(1.0f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        AmountView(amount = balance, showUnit = false)
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "/")
        Spacer(modifier = Modifier.width(2.dp))
        AmountView(amount = capacity.toMilliSatoshi(), unitTextStyle = MaterialTheme.typography.caption)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelDialog(
    onDismiss: () -> Unit,
    channel: ChannelsConfiguration.Model.Channel
) {
    val context = LocalContext.current
    val txUrl = txUrl(txId = channel.txId ?: "")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column {
            Card {
                Row(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(id = R.string.listallchannels_channel_id))
                    Spacer(modifier = Modifier.width(8.dp))
                    SelectionContainer {
                        Text(
                            text = channel.id, style = MaterialTheme.typography.body2,
                            overflow = TextOverflow.Ellipsis, maxLines = 1, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Card {
                Column(
                    modifier = Modifier
                        .background(mutedBgColor())
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = channel.json,
                            modifier = Modifier
                                .weight(1.0f)
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState()),
                            style = monoTypo(),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { copyToClipboard(context, channel.json, context.getString(R.string.listallchannels_share_subject)) }, icon = R.drawable.ic_copy)
                    Button(onClick = { share(context, channel.json, subject = context.getString(R.string.listallchannels_share_subject), context.getString(R.string.listallchannels_share_title)) }, icon = R.drawable.ic_share)
                    Button(
                        icon = R.drawable.ic_chain,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(txUrl))) },
                        text = stringResource(id = R.string.listallchannels_funding_tx),
                        onClickLabel = stringResource(id = R.string.listallchannels_funding_tx_desc)
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    Button(onClick = onDismiss, text = stringResource(id = R.string.listallchannels_close))
                }
            }
        }
    }
}
