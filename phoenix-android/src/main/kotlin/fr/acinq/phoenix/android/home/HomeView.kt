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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.findActivity
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.canRequestLiquidity
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeView(
    appViewModel: AppViewModel,
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
    onShowNotifications: () -> Unit,
    onRequestLiquidityClick: () -> Unit,
) {
    val context = LocalContext.current

    val internalData = application.internalDataRepository
    val userPrefs = application.userPrefs
    val isPowerSaverModeOn = noticesViewModel.isPowerSaverModeOn
    val torEnabledState = userPrefs.getIsTorEnabled.collectAsState(initial = null)
    val balanceDisplayMode by userPrefs.getHomeAmountDisplayMode.collectAsState(initial = HomeAmountDisplayMode.REDACTED)

    val connections by business.connectionsManager.connections.collectAsState()
    val electrumMessages by business.appConfigurationManager.electrumMessages.collectAsState()
    val channels by business.peerManager.channelsFlow.collectAsState()
    val inFlightPaymentsCount = remember(channels) { channels.inFlightPaymentsCount() }

    var showConnectionsDialog by remember { mutableStateOf(false) }
    if (showConnectionsDialog) {
        ConnectionDialog(
            connections = connections,
            electrumBlockheight = electrumMessages?.blockHeight ?: 0,
            onClose = { showConnectionsDialog = false },
            onTorClick = onTorClick,
            onElectrumClick = onElectrumClick
        )
    }

    val allPaymentsCount by business.paymentsManager.paymentsCount.collectAsState()
    val payments by paymentsViewModel.latestPaymentsFlow.collectAsState()
    val swapInBalance = business.balanceManager.swapInWalletBalance.collectAsState()
    val pendingChannelsBalance = business.balanceManager.pendingChannelsBalance.collectAsState()

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
        val balance = model.balance
        val notices = noticesViewModel.notices
        val notifications by business.notificationsManager.notifications.collectAsState(emptyList())

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MotionLayout(
                motionScene = motionScene,
                progress = progress
            ) {
                Box(
                    modifier = Modifier
                        .layoutId("collapsible")
                        .fillMaxWidth()
                ) {}
                TopBar(
                    modifier = Modifier.layoutId("topBar"),
                    appViewModel = appViewModel,
                    onConnectionsStateButtonClick = { showConnectionsDialog = true },
                    connections = connections,
                    electrumBlockheight = electrumMessages?.blockHeight ?: 0,
                    inFlightPaymentsCount = inFlightPaymentsCount,
                    isTorEnabled = torEnabledState.value,
                    onTorClick = onTorClick,
                    isPowerSaverMode = isPowerSaverModeOn,
                    showRequestLiquidity = channels.canRequestLiquidity(),
                    onRequestLiquidityClick = onRequestLiquidityClick,
                )
                HomeBalance(
                    modifier = Modifier.layoutId("balance"),
                    balance = balance,
                    balanceDisplayMode = balanceDisplayMode,
                    swapInBalance = swapInBalance.value,
                    unconfirmedChannelsBalance = pendingChannelsBalance.value,
                    onShowSwapInWallet = onShowSwapInWallet,
                )
                PrimarySeparator(modifier = Modifier.layoutId("separator"))
                HomeNotices(
                    modifier = Modifier.layoutId("notices"),
                    notices = notices,
                    notifications = notifications,
                    onNavigateToSwapInWallet = onShowSwapInWallet,
                    onNavigateToNotificationsList = onShowNotifications,
                )
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

    var showAddressWarningDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (showAddressWarningDialog){
        Dialog(
            onDismiss = { scope.launch { internalData.saveLegacyMigrationAddressWarningShown(true) } },
            title = stringResource(id = R.string.inappnotif_migration_from_legacy_dialog_title),
        ) {
            Text(text = annotatedStringResource(id = R.string.inappnotif_migration_from_legacy_dialog_body), modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp))
        }
    }

    LaunchedEffect(Unit) {
        if (LegacyPrefsDatastore.hasMigratedFromLegacy(context).first()) {
            combine(internalData.getLegacyMigrationMessageShown, internalData.getLegacyMigrationAddressWarningShown) { noticeShown, dialogShown ->
                noticeShown || dialogShown
            }.collect {
                showAddressWarningDialog = !it
            }
        }
    }
}
