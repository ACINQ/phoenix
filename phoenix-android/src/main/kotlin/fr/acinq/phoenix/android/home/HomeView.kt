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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.MotionScene
import androidx.constraintlayout.compose.layoutId
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.findActivity
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.launch


@Composable
fun HomeView(
    paymentsViewModel: PaymentsViewModel,
    noticesViewModel: NoticesViewModel,
    onPaymentClick: (WalletPaymentId) -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    onTorClick: () -> Unit,
    onElectrumClick: () -> Unit,
    onShowSwapInWallet: () -> Unit,
    onShowChannels: () -> Unit,
    onShowNotifications: () -> Unit,
) {
    val log = logger("HomeView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val torEnabledState = UserPrefs.getIsTorEnabled(context).collectAsState(initial = null)
    val connectionsState by paymentsViewModel.connectionsFlow.collectAsState(null)
    val balanceDisplayMode by UserPrefs.getHomeAmountDisplayMode(context).collectAsState(initial = HomeAmountDisplayMode.REDACTED)

    var showConnectionsDialog by remember { mutableStateOf(false) }
    if (showConnectionsDialog) {
        ConnectionDialog(connections = connectionsState, onClose = { showConnectionsDialog = false }, onTorClick = onTorClick, onElectrumClick = onElectrumClick)
    }

    val allPaymentsCount by business.paymentsManager.paymentsCount.collectAsState()
    val payments by paymentsViewModel.latestPaymentsFlow.collectAsState()
    val swapInBalance = business.balanceManager.swapInWalletBalance.collectAsState()
    val pendingChannelsBalance = business.balanceManager.pendingChannelsBalance.collectAsState()

    // controls for the migration dialog
    val migrationResult = PrefsDatastore.getMigrationResult(context).collectAsState(initial = null).value
    val migrationResultShown = InternalData.getMigrationResultShown(context).collectAsState(initial = null).value

    BackHandler {
        // force the back button to minimize the app
        context.findActivity().moveTaskToBack(false)
    }

    val defaultHeight = 190.dp
    val collapsedHeight = 0.dp

    val motionScene = MotionScene {
        val collapsibleRef = createRefFor("collapsible")
        val topBarRef = createRefFor("topBar")
        val balanceRef = createRefFor("balance")
        val separatorRef = createRefFor("separator")
        val noticesRef = createRefFor("notices")

        val startConstraint = constraintSet {
            constrain(collapsibleRef) {
                top.linkTo(parent.top)
                height = Dimension.value(defaultHeight)
            }
            constrain(topBarRef) {
                top.linkTo(parent.top, margin = 8.dp)
            }
            constrain(balanceRef) {
                top.linkTo(topBarRef.bottom, margin = 36.dp)
                centerHorizontallyTo(collapsibleRef)
            }
            constrain(separatorRef) {
                bottom.linkTo(collapsibleRef.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
            constrain(noticesRef) {
                top.linkTo(separatorRef.bottom, margin = 16.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom, margin = 16.dp)
            }
        }
        val endConstraint = constraintSet {
            constrain(collapsibleRef) {
                top.linkTo(parent.top)
                height = Dimension.value(collapsedHeight)
            }
            constrain(topBarRef) {
                bottom.linkTo(parent.top)
            }
            constrain(balanceRef) {
                bottom.linkTo(parent.top)
                centerHorizontallyTo(parent)
            }
            constrain(separatorRef) {
                bottom.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
            constrain(noticesRef) {
                top.linkTo(separatorRef.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            }
        }
        transition(startConstraint, endConstraint, "default") {}
    }

    val maxPx = with(LocalDensity.current) { defaultHeight.roundToPx().toFloat() }
    val minPx = with(LocalDensity.current) { collapsedHeight.roundToPx().toFloat() }
    val collapsibleHeight = remember { mutableStateOf(maxPx) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val height = collapsibleHeight.value

                if (height + available.y > maxPx) {
                    collapsibleHeight.value = maxPx
                    return Offset(0f, maxPx - height)
                }

                if (height + available.y < minPx) {
                    collapsibleHeight.value = minPx
                    return Offset(0f, minPx - height)
                }

                collapsibleHeight.value += available.y
                return Offset(0f, available.y)
            }
        }
    }

    val progress = 1 - (collapsibleHeight.value - minPx) / (maxPx - minPx)

    MVIView(CF::home) { model, _ ->
        val balance = remember(model) { model.balance }
        val notices = noticesViewModel.notices.values.toList()
        val notifications by business.notificationsManager.notifications.collectAsState(emptyList())

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MotionLayout(
                motionScene = motionScene,
                progress = progress
            ) {
                Box(modifier = Modifier
                    .layoutId("collapsible")
                    .fillMaxWidth()) {}
                TopBar(
                    modifier = Modifier.layoutId("topBar"),
                    onConnectionsStateButtonClick = { showConnectionsDialog = true },
                    connectionsState = connectionsState,
                    isTorEnabled = torEnabledState.value,
                    onTorClick = onTorClick
                )
                HomeBalance(
                    modifier = Modifier.layoutId("balance"),
                    balance = balance,
                    balanceDisplayMode = balanceDisplayMode,
                    swapInBalance = swapInBalance.value,
                    unconfirmedChannelsBalance = pendingChannelsBalance.value,
                    onShowSwapInWallet = onShowSwapInWallet,
                    onShowChannels = onShowChannels,
                )
                PrimarySeparator(modifier = Modifier.layoutId("separator"))
                if (notices.isNotEmpty() || notifications.isNotEmpty()) {
                    NoticesButtonRow(
                        modifier = Modifier.layoutId("notices"),
                        notices = noticesViewModel.notices.values.toList(),
                        notifications = notifications,
                        onNavigateToNotificationsList = onShowNotifications,
                    )
                }
            }

            PaymentsList(
                modifier = Modifier.nestedScroll(nestedScrollConnection),
                swapInBalance = swapInBalance.value,
                balanceDisplayMode = balanceDisplayMode,
                onPaymentClick = onPaymentClick,
                onPaymentsHistoryClick = onPaymentsHistoryClick,
                fetchPaymentDetails = { paymentsViewModel.fetchPaymentDetails(it) },
                payments = payments,
                allPaymentsCount = allPaymentsCount
            )
            BottomBar(Modifier, onSettingsClick, onReceiveClick, onSendClick)
        }
    }

    if (migrationResultShown == false && migrationResult != null) {
        MigrationResultDialog(migrationResult) {
            scope.launch { InternalData.saveMigrationResultShown(context, true) }
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
