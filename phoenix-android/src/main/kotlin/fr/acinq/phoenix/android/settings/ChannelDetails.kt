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
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.settings.walletinfo.BalanceWithContent
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.LocalChannelInfo


@Composable
fun ChannelDetails(
    onBackClick: () -> Unit,
    channelId: String,
) {
    val log = logger("ChannelDetails")
    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val btcUnit = LocalBitcoinUnit.current

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.channeldetails_title),
        )
        channelsState?.values?.firstOrNull { it.channelId == channelId }?.let { channel ->
            SettingWithCopy(title = stringResource(id = R.string.channeldetails_channel_id), value = channel.channelId)
            SettingWithCopy(title = stringResource(id = R.string.channeldetails_state), value = channel.stateName)
            SettingWithCopy(
                title = stringResource(id = R.string.channeldetails_spendable),
                value = channel.localBalance?.toPrettyString(btcUnit, withUnit = true) ?: stringResource(id = R.string.utils_unknown)
            )
        }
    }
}

@Composable
private fun ChannelDialog(
    channel: LocalChannelInfo
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.channelsview_tab_summary, channel.channelId),
        stringResource(R.string.channelsview_tab_json)
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
            Button(onClick = { copyToClipboard(context, channel.json, context.getString(R.string.channelsview_share_subject)) }, icon = R.drawable.ic_copy)
            Button(onClick = { share(context, channel.json, subject = context.getString(R.string.channelsview_share_subject), context.getString(R.string.channelsview_share_title)) }, icon = R.drawable.ic_share)
            Spacer(modifier = Modifier.weight(1.0f))
        }
    }
}

@Composable
private fun ChannelDialogSummary(
    channel: LocalChannelInfo
) {





    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        ChannelDialogDataRow(
            label = stringResource(id = R.string.channelsview_channel_id),
            value = channel.channelId
        )
        ChannelDialogDataRow(
            label = stringResource(id = R.string.channelsview_state),
            value = channel.stateName
        )
        ChannelDialogDataRow(
            label = stringResource(id = R.string.channelsview_spendable),
            value = channel.localBalance?.toPrettyString(btcUnit, withUnit = true) ?: stringResource(id = R.string.utils_unknown)
        )
        CommitmentInfoView(label = stringResource(id = R.string.channelsview_commitments), commitments = channel.commitmentsInfo)
        CommitmentInfoView(label = stringResource(id = R.string.channelsview_inactive_commitments), commitments = channel.inactiveCommitmentsInfo)
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
                    label = stringResource(id = R.string.channelsview_commitment_funding_tx_index),
                    value = "${commitment.fundingTxIndex}"
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.channelsview_commitment_funding_tx_id),
                    content = { TransactionLinkButton(txId = commitment.fundingTxId) }
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.channelsview_commitment_balance),
                    value = commitment.balanceForSend.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE)
                )
                ChannelDialogDataRow(
                    label = stringResource(id = R.string.channelsview_commitment_funding_capacity),
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
        Text(text = label, fontSize = 14.sp, modifier = Modifier
            .weight(1f)
            .alignByBaseline())
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier
            .weight(2f)
            .alignByBaseline()) {
            content()
        }
    }
}
