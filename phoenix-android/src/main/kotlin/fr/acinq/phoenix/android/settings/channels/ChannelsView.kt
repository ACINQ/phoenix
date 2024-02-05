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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.LocalChannelInfo


@Composable
fun ChannelsView(
    onBackClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onImportChannelsDataClick: () -> Unit,
) {
    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val balance by business.balanceManager.balance.collectAsState()
    val inboundLiquidity = channelsState?.values?.mapNotNull { it.availableForReceive }?.sum()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            content = {
                var showAdvancedMenuPopIn by remember { mutableStateOf(false) }
                Text(text = stringResource(id = R.string.channelsview_title))
                Spacer(modifier = Modifier.weight(1f))
                Box(contentAlignment = Alignment.TopEnd) {
                    DropdownMenu(expanded = showAdvancedMenuPopIn, onDismissRequest = { showAdvancedMenuPopIn = false }) {
                        DropdownMenuItem(onClick = onImportChannelsDataClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(
                                text = stringResource(R.string.channelsview_menu_import_channels),
                                style = MaterialTheme.typography.body1,
                            )
                        }
                    }
                    Button(
                        icon = R.drawable.ic_menu_dots,
                        iconTint = MaterialTheme.colors.onSurface,
                        padding = PaddingValues(12.dp),
                        onClick = { showAdvancedMenuPopIn = true }
                    )
                }
            }
        )
        if (!channelsState?.values?.filter { it.isUsable }.isNullOrEmpty()) {
            LightningBalanceView(balance = balance, inboundLiquidity = inboundLiquidity)
        }
        ChannelsList(channels = channelsState, onChannelClick = onChannelClick)
    }
}

@Composable
private fun LightningBalanceView(
    balance: MilliSatoshi?,
    inboundLiquidity: MilliSatoshi?,
) {
    var showHelp by remember { mutableStateOf(false) }
    CardHeader(text = stringResource(id = R.string.channelsview_header))
    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = { showHelp = !showHelp }) {
        if (balance != null && inboundLiquidity != null) {
            val balanceVsInbound = remember(balance, inboundLiquidity) {
                (balance.msat.toFloat() / (balance.msat + inboundLiquidity.msat))
                    .coerceIn(0.1f, 0.9f)// unreadable otherwise
                    .takeUnless { it.isNaN() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(1.dp),
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 2.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.channelsview_balance),
                    style = MaterialTheme.typography.body2,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(id = R.string.channelsview_inbound),
                    style = MaterialTheme.typography.body2,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(1.dp),
                    color = MaterialTheme.colors.primary.copy(alpha = ProgressIndicatorDefaults.IndicatorBackgroundOpacity),
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 2.dp)
                ) {}
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (balanceVsInbound != null) {
                LinearProgressIndicator(
                    progress = balanceVsInbound,
                    modifier = Modifier
                        .height(8.dp)
                        .fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Column {
                    AmountWithFiatBelow(amount = balance)
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    AmountWithFiatBelow(amount = inboundLiquidity)
                }
            }

            if (showHelp) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = annotatedStringResource(id = R.string.channelsview_balance_about), style = MaterialTheme.typography.subtitle2)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = annotatedStringResource(id = R.string.channelsview_inbound_about), style = MaterialTheme.typography.subtitle2)
            }

        } else {
            ProgressView(text = stringResource(id = R.string.channelsview_loading_channels))
        }
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
