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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import fr.acinq.phoenix.managers.Connections
import kotlinx.coroutines.launch


@Composable
fun HomeView(
    homeViewModel: HomeViewModel,
    onPaymentClick: (WalletPaymentId) -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    val log = logger("HomeView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionsState by homeViewModel.connectionsFlow.collectAsState(null)

    var showConnectionsDialog by remember { mutableStateOf(false) }
    if (showConnectionsDialog) {
        ConnectionDialog(connections = connectionsState, onClose = { showConnectionsDialog = false })
    }

    val payments = homeViewModel.paymentsFlow.collectAsState().value.values.toList()

    // controls for the migration dialog
    val migrationResult = PrefsDatastore.getMigrationResult(context).collectAsState(initial = null).value
    val migrationResultShown = InternalData.getMigrationResultShown(context).collectAsState(initial = null).value

    MVIView(homeViewModel) { model, _ ->
        val balance = remember(model) { model.balance }
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(
                onConnectionsStateButtonClick = {
                    showConnectionsDialog = true
                },
                connectionsState = connectionsState
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (balance == null) {
                ProgressView(text = stringResource(id = R.string.home__balance_loading))
            } else {
                AmountView(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp),
                    amount = balance,
                    amountTextStyle = MaterialTheme.typography.h1,
                    unitTextStyle = MaterialTheme.typography.h3.copy(color = MaterialTheme.colors.primary),
                )
            }
            model.incomingBalance?.let { incomingSwapAmount ->
                Spacer(modifier = Modifier.height(8.dp))
                IncomingAmountNotif(incomingSwapAmount)
            }
            Spacer(modifier = Modifier.height(24.dp))
            PrimarySeparator()
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    items = payments,
                ) {
                    if (it.paymentInfo == null) {
                        LaunchedEffect(key1 = it.orderRow.id.identifier) {
                            homeViewModel.getPaymentDescription(it.orderRow)
                        }
                        PaymentLineLoading(it.orderRow.id, it.orderRow.createdAt, onPaymentClick)
                    } else {
                        PaymentLine(it.paymentInfo, onPaymentClick)
                    }
                }
            }
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
    connectionsState: Connections?
) {
    Row(
        Modifier
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
                iconTint = if (isBadElectrumCert) negativeColor() else LocalContentColor.current,
                onClick = onConnectionsStateButtonClick,
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = if (isBadElectrumCert) negativeColor() else LocalContentColor.current),
                backgroundColor = mutedBgColor(),
                space = 8.dp,
                padding = PaddingValues(8.dp),
                modifier = Modifier.alpha(connectionsButtonAlpha)
            )
        }
    }
}

@Composable
private fun ConnectionDialog(connections: Connections?, onClose: () -> Unit) {
    val nc = navController
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
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_electrum), connection = connections.electrum, onClick = { nc.navigate(Screen.ElectrumServer) })
                HSeparator()
                ConnectionDialogLine(label = stringResource(id = R.string.conndialog_lightning), connection = connections.peer)
                HSeparator()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectionDialogLine(label: String, connection: Connection?, onClick: (() -> Unit)? = null) {
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
                Connection.ESTABLISHED -> positiveColor()
                else -> negativeColor()
            },
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1.0f))
        Text(text = when (connection) {
            Connection.ESTABLISHING -> stringResource(R.string.conndialog_connecting)
            Connection.ESTABLISHED -> stringResource(R.string.conndialog_connected)
            else -> if (connection is Connection.CLOSED && connection.isBadCertificate()) {
                stringResource(R.string.conndialog_closed_bad_cert)
            } else {
                stringResource(R.string.conndialog_closed)
            }
        }, style = monoTypo())
    }
}

@Composable
private fun IncomingAmountNotif(amount: MilliSatoshi) {
    Text(
        text = stringResource(id = R.string.home__swapin_incoming, amount.toPrettyString(preferredAmountUnit, fiatRate, withUnit = true)),
        style = MaterialTheme.typography.caption
    )
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
                icon = R.drawable.ic_send,
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
                    .height(4.dp)
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
