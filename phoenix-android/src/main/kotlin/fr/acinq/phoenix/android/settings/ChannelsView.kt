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


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.LocalChannelInfo


@Composable
fun ChannelsView() {
    val log = logger("ChannelsView")
    val nc = navController
    val context = LocalContext.current

    val showChannelDialog = remember { mutableStateOf<LocalChannelInfo?>(null) }
    showChannelDialog.value?.let {
        ChannelDialog(
            onDismiss = { showChannelDialog.value = null },
            channel = it,
        )
    }

    val channelsState = business.peerManager.channelsFlow.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.listallchannels_title),
        )
        when (val channels = channelsState.value) {
            null -> {
                Card {
                    ProgressView(text = "loading channels...")
                }
            }
            else -> {
                Column(modifier = Modifier.weight(1f)) {
                    if (channels.isEmpty()) {
                        Card(internalPadding = PaddingValues(16.dp)) {
                            Text(text = stringResource(id = R.string.listallchannels_no_channels))
                        }
                    } else {
                        Card {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(channels.values.toList()) {
                                    ChannelLine(channel = it, onClick = { showChannelDialog.value = it })
                                }
                            }
                        }
                    }
                }
            }
        }
        val nodeParams by business.nodeParamsManager.nodeParams.collectAsState()
        val nodeId = nodeParams?.nodeId?.toString()
        if (nodeId != null) {
            Card {
                Row {
                    Text(text = stringResource(id = R.string.listallchannels_node_id), modifier = Modifier.padding(16.dp))
                    Text(
                        text = nodeId, style = MaterialTheme.typography.body2, overflow = TextOverflow.Ellipsis,
                        maxLines = 1, modifier = Modifier
                            .padding(vertical = 16.dp)
                            .weight(1f)
                    )
                    Button(
                        icon = R.drawable.ic_copy,
                        onClick = { copyToClipboard(context, nodeId, context.getString(R.string.listallchannels_node_id)) },
                    )
                }
            }
        }

    }
}

@Composable
private fun ChannelLine(channel: LocalChannelInfo, onClick: () -> Unit) {
    Row(modifier = Modifier
        .clickable(role = Role.Button, onClickLabel = stringResource(id = R.string.listallchannels_dialog_label), onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (channel.isUsable) positiveColor else if (channel.isTerminated) negativeColor else mutedTextColor,
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
        channel.localBalance?.let {
            AmountView(amount = it, showUnit = false)
        } ?: Text("--")
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "/")
        Spacer(modifier = Modifier.width(2.dp))
        channel.currentFundingAmount?.let {
            AmountView(amount = it.toMilliSatoshi(), unitTextStyle = MaterialTheme.typography.caption)
        } ?: Text("--")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelDialog(
    onDismiss: () -> Unit,
    channel: LocalChannelInfo
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var currentTab by remember { mutableStateOf(0) }
        val tabs = listOf(
            stringResource(R.string.listallchannels_tab_summary, channel.channelId),
            stringResource(R.string.listallchannels_tab_json)
        )
        Card {
            TabRow(
                selectedTabIndex = currentTab,
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        text = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = index == currentTab,
                        onClick = { currentTab = index },
                    )
                }
            }
            when (currentTab) {
                1 -> ChannelDialogJson(json = channel.json)
                else -> ChannelDialogSummary(channel = channel)
            }
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { copyToClipboard(context, channel.json, context.getString(R.string.listallchannels_share_subject)) }, icon = R.drawable.ic_copy)
                Button(onClick = { share(context, channel.json, subject = context.getString(R.string.listallchannels_share_subject), context.getString(R.string.listallchannels_share_title)) }, icon = R.drawable.ic_share)
                Spacer(modifier = Modifier.weight(1.0f))
                Button(onClick = onDismiss, text = stringResource(id = R.string.listallchannels_close))
            }
        }
    }
}

@Composable
private fun ChannelDialogSummary(
    channel: LocalChannelInfo
) {
    val btcUnit = LocalBitcoinUnit.current
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        ChannelDialogDataRow(
            label = stringResource(id = R.string.listallchannels_channel_id),
            value = channel.channelId
        )
        ChannelDialogDataRow(
            label = stringResource(id = R.string.listallchannels_state),
            value = channel.stateName
        )
        ChannelDialogDataRow(
            label = stringResource(id = R.string.listallchannels_spendable),
            value = channel.localBalance?.toPrettyString(btcUnit, withUnit = true) ?: stringResource(id = R.string.utils_unknown)
        )
        CommitmentInfoView(label = stringResource(id = R.string.listallchannels_commitments), commitments = channel.commitmentsInfo)
        CommitmentInfoView(label = stringResource(id = R.string.listallchannels_inactive_commitments), commitments = channel.inactiveCommitmentsInfo)
    }
}

@Composable
private fun CommitmentInfoView(
    label: String,
    commitments: List<LocalChannelInfo.CommitmentInfo>
) {
    val btcUnit = LocalBitcoinUnit.current
    ChannelDialogDataRow(
        label = label,
        value = ""
    )
    Column(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        commitments.forEach { commitment ->
            Column(
                modifier = Modifier
                    .padding(start = 20.dp)
                    .background(mutedBgColor)
                    .padding(start = 6.dp) // small border on the left
                    .background(MaterialTheme.colors.surface)
                    .padding(start = 12.dp)
            ) {
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.listallchannels_commitment_funding_tx_index),
                    value = "${commitment.fundingTxIndex}"
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.listallchannels_commitment_funding_tx_id),
                    content = {
                        WebLink(text = commitment.fundingTxId, url = txUrl(txId = commitment.fundingTxId), maxLines = 1, fontSize = 14.sp)
                    }
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.listallchannels_commitment_balance),
                    value = commitment.balanceForSend.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE)
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.listallchannels_commitment_funding_capacity),
                    value = commitment.fundingAmount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE)
                )
            }
        }
    }
}

@Composable
private fun ChannelDialogJson(
    json: String
) {
    Column(
        modifier = Modifier
            .background(mutedBgColor)
            .fillMaxWidth()
            .heightIn(max = 350.dp)
    ) {
        SelectionContainer {
            Text(
                text = json,
                modifier = Modifier
                    .weight(1.0f)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState()),
                style = monoTypo,
            )
        }
    }
}

@Composable
private fun ChannelDialogDataRow(
    label: String,
    value: String,
) {
    ChannelDialogDataRow(label = label) {
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChannelDialogDataRow(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(2f)) {
            content()
        }
    }
}
