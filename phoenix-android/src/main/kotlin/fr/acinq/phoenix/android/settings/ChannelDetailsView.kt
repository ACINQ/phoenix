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
import androidx.compose.foundation.text.selection.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.*
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.walletPaymentId


@Composable
fun ChannelDetailsView(
    onBackClick: () -> Unit,
    channelId: String?,
) {
    val log = logger("ChannelDetailsView")
    val channelsState = business.peerManager.channelsFlow.collectAsState()

    DefaultScreenLayout(isScrollable = true) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.channeldetails_title),
        )
        when (val channels = channelsState.value) {
            null -> ProgressView(text = stringResource(id = R.string.channelsview_loading_channels))
            else -> when (val channel = channels.values.firstOrNull { it.channelId == channelId }) {
                null -> NoChannelFound()
                else -> ChannelSummaryView(channel = channel)
            }
        }
    }
}

@Composable
private fun NoChannelFound() {
    Card (internalPadding = PaddingValues(16.dp)) {
        Text(text = stringResource(id = R.string.channeldetails_not_found))
        // maybe look in the db for closed channels?
    }
}

@Composable
private fun ChannelSummaryView(
    channel: LocalChannelInfo
) {
    val btcUnit = LocalBitcoinUnit.current
    var showJsonDialog by remember { mutableStateOf(false) }

    if (showJsonDialog) {
        JsonDialog(onDismiss = { showJsonDialog = false }, json = channel.json)
    }

    Card {
        SettingWithCopy(title = stringResource(id = R.string.channeldetails_channel_id), value = channel.channelId)
        Setting(title = stringResource(id = R.string.channeldetails_state), description = channel.stateName)
        Setting(
            title = stringResource(id = R.string.channeldetails_spendable),
            description = channel.localBalance?.toPrettyString(btcUnit, withUnit = true) ?: stringResource(id = R.string.utils_unknown)
        )
        SettingInteractive(
            title = stringResource(id = R.string.channeldetails_json),
            icon = R.drawable.ic_curly_braces,
            iconTint = MaterialTheme.colors.primary,
            onClick = { showJsonDialog = true }
        )
    }

    if (channel.commitmentsInfo.isNotEmpty()) {
        CardHeader(text = stringResource(id = R.string.channeldetails_commitments))
        Card {
            channel.commitmentsInfo.forEach {
                CommitmentDetailsView(commitment = it)
            }
        }
    }

    if (channel.inactiveCommitmentsInfo.isNotEmpty()) {
        CardHeader(text = stringResource(id = R.string.channeldetails_inactive_commitments))
        Card {
            channel.inactiveCommitmentsInfo.forEach {
                CommitmentDetailsView(commitment = it)
            }
        }
    }
}

@Composable
private fun CommitmentDetailsView(
    commitment: LocalChannelInfo.CommitmentInfo
) {
    val btcUnit = LocalBitcoinUnit.current
    val paymentsManager = business.paymentsManager
    val linkedPayments by produceState<List<WalletPayment>>(initialValue = emptyList()) {
        value = paymentsManager.listPaymentsForTxId(ByteVector32.fromValidHex(commitment.fundingTxId))
    }

    SettingWithDecoration(
        title = "Index ${commitment.fundingTxIndex}",
        description = {
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_funding_tx_id), modifier = Modifier.alignByBaseline())
                Spacer(modifier = Modifier.width(4.dp))
                TransactionLinkButton(txId = commitment.fundingTxId, modifier = Modifier.alignByBaseline())
            }
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_balance))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commitment.balanceForSend.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE),
                    style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_funding_capacity))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commitment.fundingAmount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE),
                    style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            if (linkedPayments.isNotEmpty()) {
                Row {
                    Text(text = stringResource(id = R.string.channeldetails_commitment_linked_payments), modifier = Modifier.alignByBaseline())
                    Spacer(modifier = Modifier.width(4.dp))
                    val navController = navController
                    Column(modifier = Modifier.alignByBaseline()) {
                        linkedPayments.forEach { payment ->
                            InlineButton(
                                text = when {
                                    payment is IncomingPayment && payment.origin is IncomingPayment.Origin.Invoice -> "pay-to-open"
                                    payment is IncomingPayment && payment.origin is IncomingPayment.Origin.OnChain -> "swap-in"
                                    payment is SpliceOutgoingPayment -> "swap-out"
                                    payment is SpliceCpfpOutgoingPayment -> "cpfp"
                                    else -> "other"
                                },
                                onClick = {
                                    navigateToPaymentDetails(navController, payment.walletPaymentId(), isFromEvent = false)
                                },
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
        decoration = null
    )
}

@Composable
private fun JsonDialog(
    onDismiss: () -> Unit,
    json: String,
) {
    val context = LocalContext.current
    Dialog(
        onDismiss = onDismiss,
        buttonsTopMargin = 0.dp,
        buttons = {
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, json, context.getString(R.string.channeldetails_share)) }
            )
            Button(
                icon = R.drawable.ic_share,
                onClick = { share(context, json, subject = context.getString(R.string.channeldetails_share_subject), chooserTitle = context.getString(R.string.channeldetails_share_title)) }
            )
            Spacer(modifier = Modifier.weight(1.0f))
            Button(text = stringResource(id = R.string.btn_close), onClick = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .background(mutedBgColor)
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 350.dp)
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
}
