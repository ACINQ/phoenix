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


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.settings.walletinfo.BalanceRow
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.utils.migrations.ChannelsConsolidationHelper


@Composable
fun ChannelsView(
    onBackClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onConsolidateButtonClick: () -> Unit,
) {
    val log = logger("ChannelsView")

    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val balance by business.balanceManager.balance.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.channelsview_title),
        )
        if (!channelsState.isNullOrEmpty()) {
            LightningBalanceView(balance = balance)
        }
        channelsState?.values?.toList()?.let { channels ->
            if (ChannelsConsolidationHelper.canConsolidate(channels)) {
                CanConsolidateView(onConsolidateButtonClick)
            }
        }
        ChannelsList(channels = channelsState, onChannelClick = onChannelClick)
    }
}

@Composable
private fun LightningBalanceView(
    balance: MilliSatoshi?
) {
    CardHeader(text = stringResource(id = R.string.channelsview_balance))
    Card(
        internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BalanceRow(balance = balance)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(id = R.string.channelsview_balance_about), style = MaterialTheme.typography.subtitle2)
    }
}

@Composable
private fun ChannelsList(
    channels: Map<ByteVector32, LocalChannelInfo>?,
    onChannelClick: (String) -> Unit,
) {
    when (channels) {
        null -> ProgressView(text = stringResource(id = R.string.channelsview_loading_channels))
        else -> {
            if (channels.isEmpty()) {
                Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = stringResource(id = R.string.channelsview_no_channels))
                }
            } else {
                CardHeader(text = stringResource(id = R.string.channelsview_title))
                Card {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(channels.values.toList()) {
                            ChannelLine(channel = it, onClick = { onChannelClick(it.channelId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLine(channel: LocalChannelInfo, onClick: () -> Unit) {
    Row(modifier = Modifier
        .clickable(role = Role.Button, onClickLabel = stringResource(id = R.string.channeldetails_title), onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (channel.isUsable) positiveColor else if (channel.isTerminated) negativeColor else mutedTextColor,
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = channel.stateName,
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

@Composable
private fun CanConsolidateView(
    onConsolidateButtonClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Button(
            text = stringResource(id = R.string.channeldetails_can_consolidate_button),
            icon = R.drawable.ic_merge,
            onClick = onConsolidateButtonClick,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        )
    }
}
