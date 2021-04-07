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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.Converter
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.app.amountMsat
import fr.acinq.phoenix.app.desc
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.utils.Connections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


private class HomeViewModel(val connectionsFlow: StateFlow<Connections>, controller: HomeController) : MVIControllerViewModel<Home.Model, Home.Intent>(controller) {
    class Factory(
        private val connectionsFlow: StateFlow<Connections>,
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> HomeController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(connectionsFlow, controllerFactory.getController()) as T
        }
    }
}

@ExperimentalCoroutinesApi
@Composable
fun HomeView(appVM: AppViewModel) {
    requireWalletPresent(inScreen = Screen.Home) {
        val log = logger()
        val connectionsFlow = business.connectionsMonitor.connections
        val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(connectionsFlow, controllerFactory, CF::home))
        val connectionsState = vm.connectionsFlow.collectAsState()
        val showConnectionsDialog = remember { mutableStateOf(false) }
        if (showConnectionsDialog.value) {
            ConnectionDialog(connections = connectionsState.value, onClose = { showConnectionsDialog.value = false })
        }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalDrawer(
            drawerState = drawerState,
            drawerShape = RectangleShape,
            drawerContent = { SideMenu() },
            content = {
                MVIView(CF::home) { model, _ ->
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
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colors.primary, modifier = Modifier
                                .width(50.dp)
                                .height(8.dp)
                                .align(Alignment.CenterHorizontally)
                        ) { }
                        Spacer(modifier = Modifier.height(24.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(model.payments) {
                                PaymentLine(payment = it)
                            }
                        }
                        BottomBar(scope, drawerState)
                    }
                }
            }
        )
    }
}

@Composable
private fun SideMenu() {
    val context = LocalContext.current
    val peerState = business.peerState().collectAsState()
    val nc = navController
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
            onClick = { nc.navigate(Screen.Settings) },
            padding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            text = stringResource(id = R.string.home__drawer__notifications),
            icon = R.drawable.ic_notification,
            enabled = false,
            onClick = { nc.navigate(Screen.Settings) },
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
        text = stringResource(id = R.string.home__swapin_incoming, amount.toPrettyString(localUnit, localRate, withUnit = true)),
        style = MaterialTheme.typography.caption
    )
}

@Composable
private fun PaymentLine(payment: WalletPayment) {
    val log = logger()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PaymentIcon(payment)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row {
                PaymentDescription(payment = payment, modifier = Modifier.weight(1.0f))
                Spacer(modifier = Modifier.width(16.dp))
                if (!isPaymentFailed(payment)) {
                    val isOutgoing = payment is OutgoingPayment
                    val amount = when (payment) {
                        is OutgoingPayment -> if (payment.details is OutgoingPayment.Details.ChannelClosing) {
                            payment.recipientAmount
                        } else {
                            payment.parts.map { it.amount }.sum()
                        }
                        is IncomingPayment -> payment.received?.amount ?: 0.msat
                    }
                    AmountView(
                        amount = amount,
                        amountTextStyle = MaterialTheme.typography.body1.copy(color = if (isOutgoing) negativeColor() else positiveColor()),
                        unitTextStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                        isOutgoing = isOutgoing
                    )
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = WalletPayment.completedAt(payment).toRelativeDateString(), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun PaymentDescription(payment: WalletPayment, modifier: Modifier = Modifier) {
    val desc = when (payment) {
        is OutgoingPayment -> when (val d = payment.details) {
            is OutgoingPayment.Details.Normal -> d.paymentRequest.description
            is OutgoingPayment.Details.KeySend -> stringResource(id = R.string.paymentline_keysend_outgoing)
            is OutgoingPayment.Details.SwapOut -> d.address
            is OutgoingPayment.Details.ChannelClosing -> stringResource(R.string.paymentline_closing_desc, d.closingAddress)
        }
        is IncomingPayment -> when (val o = payment.origin) {
            is IncomingPayment.Origin.Invoice -> o.paymentRequest.description
            is IncomingPayment.Origin.KeySend -> stringResource(id = R.string.paymentline_keysend_incoming)
            is IncomingPayment.Origin.SwapIn -> o.address ?: stringResource(id = R.string.paymentline_swap_in_desc)
        }
    }.takeIf { !it.isNullOrBlank() }
    Text(
        text = desc ?: stringResource(id = R.string.paymentdetails_no_description),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (desc != null) MaterialTheme.typography.body1 else MaterialTheme.typography.body1.copy(color = mutedTextColor()),
        modifier = modifier
    )
}

private fun isPaymentFailed(payment: WalletPayment) = (payment is OutgoingPayment && payment.status is OutgoingPayment.Status.Completed.Failed)
        || (payment is IncomingPayment && payment.isExpired())

@Composable
private fun PaymentIcon(payment: WalletPayment) {
    when (payment) {
        is OutgoingPayment -> when (payment.status) {
            is OutgoingPayment.Status.Completed.Failed -> PaymentIconComponent(
                icon = R.drawable.ic_payment_failed,
                description = stringResource(id = R.string.paymentdetails_status_sent_failed)
            )
            is OutgoingPayment.Status.Pending -> PaymentIconComponent(
                icon = R.drawable.ic_payment_pending,
                description = stringResource(id = R.string.paymentdetails_status_sent_pending)
            )
            is OutgoingPayment.Status.Completed.Succeeded.OffChain -> PaymentIconComponent(
                icon = R.drawable.ic_payment_success,
                description = stringResource(id = R.string.paymentdetails_status_sent_successful),
                iconSize = 18.dp,
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
            is OutgoingPayment.Status.Completed.Succeeded.OnChain -> PaymentIconComponent(
                icon = R.drawable.ic_payment_success_onchain,
                description = stringResource(id = R.string.paymentdetails_status_sent_successful),
                iconSize = 12.dp,
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
        }
        is IncomingPayment -> when (payment.received) {
            null -> PaymentIconComponent(
                icon = R.drawable.ic_payment_pending,
                description = stringResource(id = R.string.paymentdetails_status_received_pending)
            )
            else -> PaymentIconComponent(
                icon = R.drawable.ic_payment_success,
                description = stringResource(id = R.string.paymentdetails_status_received_successful),
                iconSize = 18.dp,
                iconColor = MaterialTheme.colors.onPrimary,
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun PaymentIconComponent(icon: Int, description: String, iconSize: Dp = 18.dp, iconColor: Color = MaterialTheme.colors.primary, backgroundColor: Color = Color.Unspecified) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .size(24.dp)
            .background(color = backgroundColor)
            .padding(4.dp)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

@Composable
private fun BottomBar(scope: CoroutineScope, drawerState: DrawerState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colors.background)
    ) {
        val nc = navController
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
            onClick = { nc.navigate(Screen.Receive) },
            iconTint = MaterialTheme.colors.onSurface,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )
        VSeparator(PaddingValues(top = 16.dp, bottom = 16.dp))
        Button(
            text = stringResource(id = R.string.menu_send),
            icon = R.drawable.ic_send,
            onClick = { nc.navigate(Screen.ReadData) },
            iconTint = MaterialTheme.colors.onSurface,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )
    }
}
