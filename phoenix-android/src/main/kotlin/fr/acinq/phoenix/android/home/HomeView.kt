/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.android.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.compose.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.WalletContext
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import fr.acinq.phoenix.managers.Connections
import fr.acinq.phoenix.managers.WalletBalance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


@Composable
fun HomeView(
    paymentsViewModel: PaymentsViewModel,
    onPaymentClick: (WalletPaymentId) -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    onTorClick: () -> Unit,
    onElectrumClick: () -> Unit,
    onShowSwapInWallet: () -> Unit,
) {
    val log = logger("HomeView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val torEnabledState = UserPrefs.getIsTorEnabled(context).collectAsState(initial = null)
    val connectionsState by paymentsViewModel.connectionsFlow.collectAsState(null)
    val balanceDisplayMode by UserPrefs.getHomeAmountDisplayMode(context).collectAsState(initial = null)


    val connectivityManager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var netwCaps by remember { mutableStateOf<NetworkCapabilities?>(null) }

    LaunchedEffect(key1 = Unit) {
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                netwCaps = connectivityManager.getNetworkCapabilities(network)
            }
            override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
                netwCaps = networkCapabilities
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                netwCaps = null
            }
        })
    }

    var showConnectionsDialog by remember { mutableStateOf(false) }
    if (showConnectionsDialog) {
        ConnectionDialog(connections = connectionsState, onClose = { showConnectionsDialog = false }, onTorClick = onTorClick, onElectrumClick = onElectrumClick, netwCaps)
    }

    val allPaymentsCount by business.paymentsManager.paymentsCount.collectAsState()
    val payments by paymentsViewModel.latestPaymentsFlow.collectAsState()
    val swapInBalance = business.balanceManager.swapInWalletBalance.collectAsState()
    val unconfirmedChannelsBalance = business.balanceManager.unconfirmedChannelPayments.collectAsState()

    // controls for the migration dialog
    val migrationResult = PrefsDatastore.getMigrationResult(context).collectAsState(initial = null).value
    val migrationResultShown = InternalData.getMigrationResultShown(context).collectAsState(initial = null).value

    BackHandler {
        // force the back button to minimize the app
        context.findActivity().moveTaskToBack(false)
    }

    MVIView(CF::home) { model, _ ->
        val balance = remember(model) { model.balance }
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(
                onConnectionsStateButtonClick = {
                    showConnectionsDialog = true
                },
                connectionsState = connectionsState,
                isTorEnabled = torEnabledState.value,
                onTorClick = onTorClick
            )
            Spacer(modifier = Modifier.height(64.dp))
            if (balance == null) {
                ProgressView(text = stringResource(id = R.string.home__balance_loading))
            } else {
                val isAmountRedacted = balanceDisplayMode == HomeAmountDisplayMode.REDACTED
                AmountView(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .padding(horizontal = if (isAmountRedacted) 40.dp else 16.dp),
                    amount = balance,
                    amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 40.sp),
                    unitTextStyle = MaterialTheme.typography.h3.copy(fontWeight = FontWeight.Light, color = MaterialTheme.colors.primary),
                    isRedacted = isAmountRedacted,
                    onClick = { context, inFiat ->
                        val mode = UserPrefs.getHomeAmountDisplayMode(context).firstOrNull()
                        when {
                            inFiat && mode == HomeAmountDisplayMode.BTC -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.REDACTED)
                            mode == HomeAmountDisplayMode.BTC -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.FIAT)
                            mode == HomeAmountDisplayMode.FIAT -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.REDACTED)
                            mode == HomeAmountDisplayMode.REDACTED -> UserPrefs.saveHomeAmountDisplayMode(context, HomeAmountDisplayMode.BTC)
                            else -> Unit
                        }
                    }
                )
            }
            Column(modifier = Modifier.heightIn(min = 54.dp), verticalArrangement = Arrangement.Top) {
                IncomingAmountNotif(
                    swapInBalance = swapInBalance.value,
                    channelConfirmationBalance = unconfirmedChannelsBalance.value,
                    onShowSwapInWallet = onShowSwapInWallet
                )
            }
            PrimarySeparator()
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.weight(1f, fill = true), horizontalAlignment = Alignment.CenterHorizontally) {
                if (payments.isEmpty()) {
                    Text(
                        text = when {
                            swapInBalance.value.total > 0.sat -> stringResource(id = R.string.home__payments_none_incoming)
                            else -> stringResource(id = R.string.home__payments_none)
                        },
                        style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 250.dp)
                    )
                } else {
                    LatestPaymentsList(
                        allPaymentsCount = allPaymentsCount,
                        payments = payments,
                        onPaymentClick = onPaymentClick,
                        onPaymentsHistoryClick = onPaymentsHistoryClick,
                        fetchPaymentDetails = { paymentsViewModel.fetchPaymentDetails(it) },
                        isAmountRedacted = balanceDisplayMode == HomeAmountDisplayMode.REDACTED,
                    )
                }
            }
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            BottomBar(onSettingsClick, onReceiveClick, onSendClick)
        }
    }

    if (migrationResultShown == false && migrationResult != null) {
        MigrationResultDialog(migrationResult) {
            scope.launch { InternalData.saveMigrationResultShown(context, true) }
        }
    }
}

@Composable
fun TopBar(
    onConnectionsStateButtonClick: () -> Unit,
    connectionsState: Connections?,
    onTorClick: () -> Unit,
    isTorEnabled: Boolean?
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(40.dp)
            .clipToBounds()
    ) {
        if (connectionsState?.electrum !is Connection.ESTABLISHED || connectionsState.peer !is Connection.ESTABLISHED) {
            val connectionsTransition = rememberInfiniteTransition()
            val connectionsButtonAlpha by connectionsTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes { durationMillis = 500 },
                    repeatMode = RepeatMode.Reverse
                )
            )
            val electrumConnection = connectionsState?.electrum
            val isBadElectrumCert = electrumConnection != null && electrumConnection is Connection.CLOSED && electrumConnection.isBadCertificate()
            FilledButton(
                text = stringResource(id = if (isBadElectrumCert) R.string.home__connection__bad_cert else R.string.home__connection__connecting),
                icon = if (isBadElectrumCert) R.drawable.ic_alert_triangle else R.drawable.ic_connection_lost,
                iconTint = if (isBadElectrumCert) negativeColor else LocalContentColor.current,
                onClick = onConnectionsStateButtonClick,
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = if (isBadElectrumCert) negativeColor else LocalContentColor.current),
                backgroundColor = MaterialTheme.colors.surface,
                space = 8.dp,
                padding = PaddingValues(8.dp),
                modifier = Modifier.alpha(connectionsButtonAlpha)
            )
        } else if (isTorEnabled == true) {
            if (connectionsState.tor is Connection.ESTABLISHED) {
                FilledButton(
                    text = stringResource(id = R.string.home__connection__tor_active),
                    icon = R.drawable.ic_tor_shield_ok,
                    iconTint = positiveColor,
                    onClick = onTorClick,
                    textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = LocalContentColor.current),
                    backgroundColor = mutedBgColor,
                    space = 8.dp,
                    padding = PaddingValues(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        FilledButton(
            text = stringResource(R.string.home__faq_button),
            icon = R.drawable.ic_help_circle,
            iconTint = LocalContentColor.current,
            onClick = { openLink(context, "https://phoenix.acinq.co/faq") },
            textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
            backgroundColor = MaterialTheme.colors.surface,
            space = 8.dp,
            padding = PaddingValues(8.dp),
        )
    }
}

@Composable
private fun ConnectionDialog(
    connections: Connections?,
    onClose: () -> Unit,
    onTorClick: () -> Unit,
    onElectrumClick: () -> Unit,
    networkCapabilities: NetworkCapabilities?
) {
    val context = LocalContext.current
    val connectivityManager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    Dialog(title = stringResource(id = R.string.conndialog_title), onDismiss = onClose) {
        Column {
            if (connections?.internet != Connection.ESTABLISHED) {
                Text(
                    text = stringResource(id = R.string.conndialog_network),
                    modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
                )
            } else {
                if (connections.electrum != Connection.ESTABLISHED || connections.peer != Connection.ESTABLISHED) {
                    Text(text = stringResource(id = R.string.conndialog_summary_not_ok), Modifier.padding(horizontal = 24.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                HSeparator()
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_internet), connection = connections.internet)
                HSeparator()

                val isTorEnabled = UserPrefs.getIsTorEnabled(context).collectAsState(initial = null).value
                if (isTorEnabled != null && isTorEnabled) {
                    ConnectionDialogLine(label = stringResource(id = R.string.conndialog_tor), connection = connections.tor, onClick = onTorClick)
                    HSeparator()
                }

                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_electrum), connection = connections.electrum, onClick = onElectrumClick)
                HSeparator()
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_lightning), connection = connections.peer)
                HSeparator()
                Spacer(Modifier.height(16.dp))
            }

            networkCapabilities?.let { caps ->
                CompositionLocalProvider(LocalTextStyle provides monoTypo.copy(fontSize = 10.sp)) {
                    Text(text = "tranport: ${caps.transportInfo}")
                    Text(text = "metered background restrictions: ${
                        when (val s = connectivityManager.restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "not restricted"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "restricted"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "not restricted (whitelisted)"
                            else -> s
                        }
                    }")
                    Text(text = "enterprise use: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)}")
                    Text(text = "internet capable: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                    Text(text = "internet connectivity: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                    Text(text = "not congested: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)}")
                    Text(text = "not metered: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}")
                    Text(text = "not restricted: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)}")
                    Text(text = "not roaming: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)}")
                    Text(text = "not suspended: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)}")
                    Text(text = "cellular: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                    Text(text = "ethernet: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")
                    Text(text = "vpn: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}")
                    Text(text = "wifi: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")

                }
            } ?: Text(text = "no network")
        }
    }
}

@Composable
private fun ConnectionDialogLine(
    label: String,
    connection: Connection?,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(id = R.string.conndialog_accessibility_desc, label), onClick = onClick) else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = when (connection) {
                Connection.ESTABLISHING -> orange
                Connection.ESTABLISHED -> positiveColor
                else -> negativeColor
            },
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1.0f))
        Text(
            text = when (connection) {
                Connection.ESTABLISHING -> stringResource(R.string.conndialog_connecting)
                Connection.ESTABLISHED -> stringResource(R.string.conndialog_connected)
                else -> if (connection is Connection.CLOSED && connection.isBadCertificate()) {
                    stringResource(R.string.conndialog_closed_bad_cert)
                } else {
                    stringResource(R.string.conndialog_closed)
                }
            }, style = monoTypo
        )
    }
}

@Composable
private fun IncomingAmountNotif(
    swapInBalance: WalletBalance,
    channelConfirmationBalance: MilliSatoshi,
    onShowSwapInWallet: () -> Unit,
) {
    var showSwapInInfoDialog by remember { mutableStateOf(false) }

    if (showSwapInInfoDialog) {
        OnChainInfoDialog(
            swapInBalance = swapInBalance,
            channelConfirmationBalance = channelConfirmationBalance,
            onDismiss = { showSwapInInfoDialog = false },
            onShowSwapInWallet = onShowSwapInWallet,
        )
    }

    val balance = swapInBalance.total.toMilliSatoshi() + channelConfirmationBalance
    if (balance > 0.msat) {
        FilledButton(
            icon = R.drawable.ic_chain,
            iconTint = MaterialTheme.typography.caption.color,
            text = stringResource(id = R.string.home__onchain_incoming, balance.toPrettyString(preferredAmountUnit, fiatRate, withUnit = true)),
            textStyle = MaterialTheme.typography.caption,
            space = 4.dp,
            padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            backgroundColor = Color.Transparent,
            onClick = { showSwapInInfoDialog = true }
        )
    }
}

@Composable
private fun OnChainInfoDialog(
    onDismiss: () -> Unit,
    swapInBalance: WalletBalance,
    channelConfirmationBalance: MilliSatoshi,
    onShowSwapInWallet: () -> Unit,
) {
    val btcUnit = LocalBitcoinUnit.current
    Dialog(onDismiss = onDismiss, title = stringResource(id = R.string.home__onchain_dialog_title)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(R.string.home__onchain_dialog_header))
            if (swapInBalance.total > 0.sat) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = annotatedStringResource(R.string.home__onchain_dialog_swap_in, swapInBalance.total.toPrettyString(btcUnit, withUnit = true)))
            }
            if (channelConfirmationBalance > 0.msat) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = annotatedStringResource(R.string.home__onchain_dialog_channel_confirming, channelConfirmationBalance.toPrettyString(btcUnit, withUnit = true)))
            }
            Spacer(modifier = Modifier.height(4.dp))
            InlineButton(text = stringResource(id = R.string.home__onchain_dialog_button_to_wallet), onClick = onShowSwapInWallet)
        }
    }
}

@Composable
private fun UnconfirmedChannelsBalanceDialog(
    amount: MilliSatoshi,
    onDismiss: () -> Unit,
) {
    Dialog(onDismiss = onDismiss, title = "${amount.toPrettyString(BitcoinUnit.Sat, withUnit = true)} waiting for channels to confirm") {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = "The incoming amount display and this message are placeholders."
        )
    }
}

@Composable
private fun ColumnScope.LatestPaymentsList(
    allPaymentsCount: Long,
    payments: List<PaymentRowState>,
    onPaymentClick: (WalletPaymentId) -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    fetchPaymentDetails: (WalletPaymentOrderRow) -> Unit,
    isAmountRedacted: Boolean,
) {
    val morePaymentsButton: @Composable () -> Unit = {
        FilledButton(
            text = stringResource(id = R.string.home__payments_more_button),
            icon = R.drawable.ic_chevron_down,
            iconTint = MaterialTheme.typography.caption.color,
            onClick = onPaymentsHistoryClick,
            backgroundColor = Color.Transparent,
            textStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(
            items = payments,
        ) { index, item ->
            if (item.paymentInfo == null) {
                LaunchedEffect(key1 = item.orderRow.identifier) {
                    fetchPaymentDetails(item.orderRow)
                }
                PaymentLineLoading(item.orderRow.id, onPaymentClick)
            } else {
                PaymentLine(item.paymentInfo, onPaymentClick, isAmountRedacted)
            }
            if (payments.isNotEmpty() && allPaymentsCount > PaymentsViewModel.latestPaymentsCount && index == payments.size - 1) {
                Spacer(Modifier.height(16.dp))
                morePaymentsButton()
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun BottomBar(
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colors.surface)
    ) {
        Row {
            Button(
                icon = R.drawable.ic_settings,
                onClick = onSettingsClick,
                iconTint = MaterialTheme.colors.onSurface,
                padding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxHeight()
            )
            VSeparator(PaddingValues(top = 20.dp, bottom = 20.dp))
            Button(
                text = stringResource(id = R.string.menu_receive),
                icon = R.drawable.ic_receive,
                onClick = onReceiveClick,
                iconTint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
            VSeparator(PaddingValues(top = 20.dp, bottom = 20.dp))
            Button(
                text = stringResource(id = R.string.menu_send),
                icon = R.drawable.ic_scan_qr,
                onClick = onSendClick,
                iconTint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
        }
        Row(
            Modifier
                .padding(horizontal = 32.dp)
                .align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            ) { }
        }
    }
}

@Composable
private fun MigrationResultDialog(
    migrationResult: MigrationResult,
    onClose: () -> Unit
) {
    Dialog(title = stringResource(id = R.string.migration_dialog_title), onDismiss = onClose) {
        Text(text = stringResource(id = R.string.migration_dialog_message), Modifier.padding(horizontal = 24.dp))
    }
}
