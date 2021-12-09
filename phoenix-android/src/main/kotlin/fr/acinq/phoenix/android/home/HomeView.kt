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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.managers.Connections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@Composable
fun HomeView(
    onPaymentClick: (WalletPaymentId) -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    val log = logger("HomeView")
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(business.connectionsManager.connections, business.paymentsManager, controllerFactory, CF::home))

    val connectionsState = vm.connectionsFlow.collectAsState()

    val showConnectionsDialog = remember { mutableStateOf(false) }
    if (showConnectionsDialog.value) {
        ConnectionDialog(connections = connectionsState.value, onClose = { showConnectionsDialog.value = false })
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val payments = vm.paymentsFlow.collectAsState().value.values.toList()

    ModalDrawer(
        drawerState = drawerState,
        drawerShape = RectangleShape,
        drawerContent = { SideMenu(onSettingsClick) },
        content = {
            MVIView(vm) { model, _ ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TopBar(showConnectionsDialog, connectionsState)
                    Spacer(modifier = Modifier.height(16.dp))
                    AmountView(
                        amount = model.balance,
                        amountTextStyle = MaterialTheme.typography.h3,
                        unitTextStyle = MaterialTheme.typography.h6.copy(color = MaterialTheme.colors.primary),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp)
                    )
                    model.incomingBalance?.run {
                        Spacer(modifier = Modifier.height(8.dp))
                        IncomingAmountNotif(this)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
                            if (it.payment == null) {
                                LaunchedEffect(key1 = it.orderRow.id.identifier) {
                                    vm.getPaymentDescription(it.orderRow)
                                }
                                PaymentLineLoading(it.orderRow.id, it.orderRow.createdAt, onPaymentClick)
                            } else {
                                PaymentLine(it.payment, onPaymentClick)
                            }
                        }
                    }
                    BottomBar(drawerState, onReceiveClick, onSendClick)
                }
            }
        }
    )
}

@Composable
private fun SideMenu(
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val peerState = business.peerState().collectAsState()
    Column {
        Column(Modifier.padding(start = 24.dp, top = 32.dp, end = 16.dp, bottom = 16.dp)) {
            val nodeId = peerState.value?.nodeParams?.nodeId?.toString() ?: stringResource(id = R.string.utils_unknown)
            Surface(shape = CircleShape, elevation = 2.dp) {
                Image(painter = painterResource(id = R.drawable.illus_phoenix), contentDescription = null, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Phoenix Wallet",
                style = MaterialTheme.typography.subtitle2.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nodeId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
            )
            FilledButton(
                text = R.string.home__drawer__copy_nodeid,
                backgroundColor = Color.Unspecified,
                padding = PaddingValues(4.dp),
                space = 8.dp,
                textStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                modifier = Modifier.absoluteOffset(x = (-4).dp),
                onClick = { copyToClipboard(context, data = nodeId) }
            )
        }
        HSeparator()
        Button(
            text = stringResource(id = R.string.home__drawer__settings),
            icon = R.drawable.ic_settings,
            onClick = onSettingsClick,
            padding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            text = stringResource(id = R.string.home__drawer__notifications),
            icon = R.drawable.ic_notification,
            enabled = false,
            onClick = { },
            padding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            text = stringResource(id = R.string.home__drawer__faq),
            icon = R.drawable.ic_help_circle,
            onClick = { },
            padding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        )
        HSeparator()
        Button(
            text = stringResource(id = R.string.home__drawer__support),
            icon = R.drawable.ic_blank,
            onClick = { },
            padding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TopBar(showConnectionsDialog: MutableState<Boolean>, connectionsState: State<Connections>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(40.dp)
            .clipToBounds()
    ) {
        if (connectionsState.value.electrum == Connection.CLOSED || connectionsState.value.peer == Connection.CLOSED) {
            val connectionsTransition = rememberInfiniteTransition()
            val connectionsButtonAlpha by connectionsTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes { durationMillis = 500 },
                    repeatMode = RepeatMode.Reverse
                )
            )
            FilledButton(
                text = R.string.home__connection__connecting,
                icon = R.drawable.ic_connection_lost,
                onClick = { showConnectionsDialog.value = true },
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
                backgroundColor = mutedBgColor(),
                space = 8.dp,
                padding = PaddingValues(8.dp),
                modifier = Modifier.alpha(connectionsButtonAlpha)
            )
        }
    }
}

@Composable
private fun ConnectionDialog(connections: Connections, onClose: () -> Unit) {
    Dialog(title = stringResource(id = R.string.conndialog_title), onDismiss = onClose) {
        Column {
            Text(text = stringResource(id = R.string.conndialog_summary_not_ok), Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator()
            ConnectionDialogLine(label = stringResource(id = R.string.conndialog_electrum), connection = connections.electrum)
            HSeparator()
            ConnectionDialogLine(label = stringResource(id = R.string.conndialog_lightning), connection = connections.peer)
            HSeparator()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectionDialogLine(label: String, connection: Connection) {
    Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (connection == Connection.ESTABLISHED) positiveColor() else negativeColor(),
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1.0f))
        Text(text = stringResource(id = if (connection == Connection.ESTABLISHED) R.string.conndialog_ok else R.string.conndialog_not_ok), style = monoTypo())
    }
}

@Composable
private fun IncomingAmountNotif(amount: MilliSatoshi) {
    Text(
        text = stringResource(id = R.string.home__swapin_incoming, amount.toPrettyString(amountUnit, fiatRate, withUnit = true)),
        style = MaterialTheme.typography.caption
    )
}

@Composable
private fun BottomBar(
    drawerState: DrawerState,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colors.background)
    ) {
        Button(
            icon = R.drawable.ic_settings,
            onClick = {
                scope.launch {
                    drawerState.open()
                }
            },
            iconTint = MaterialTheme.colors.onSurface,
            padding = PaddingValues(24.dp),
            modifier = Modifier.fillMaxHeight()
        )
        VSeparator(PaddingValues(top = 16.dp, bottom = 16.dp))
        Button(
            text = stringResource(id = R.string.menu_receive),
            icon = R.drawable.ic_receive,
            onClick = onReceiveClick,
            iconTint = MaterialTheme.colors.onSurface,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )
        VSeparator(PaddingValues(top = 16.dp, bottom = 16.dp))
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
}
