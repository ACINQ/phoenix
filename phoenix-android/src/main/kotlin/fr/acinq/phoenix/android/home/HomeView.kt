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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.components.buttons.MutedFilledButton
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.buttons.openLink
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.home.releasenotes.ReleaseNoteDialog
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.utils.FCMHelper
import fr.acinq.phoenix.android.utils.datastore.getHomeAmountDisplayMode
import fr.acinq.phoenix.android.utils.extensions.findActivity
import fr.acinq.phoenix.data.canRequestLiquidity
import fr.acinq.phoenix.data.inFlightPaymentsCount

@Composable
fun HomeView(
    walletId: WalletId,
    business: PhoenixBusiness,
    paymentsViewModel: PaymentsViewModel,
    noticesViewModel: NoticesViewModel,
    onPaymentClick: (UUID) -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    onTorClick: () -> Unit,
    onElectrumClick: () -> Unit,
    onNavigateToSwapInWallet: () -> Unit,
    onNavigateToFinalWallet: () -> Unit,
    onShowNotifications: () -> Unit,
    onRequestLiquidityClick: () -> Unit,
) {
    val context = LocalContext.current

    val isPowerSaverModeOn = noticesViewModel.isPowerSaverModeOn
    val fcmTokenFlow = application.globalPrefs.getFcmToken.collectAsState(initial = "")
    val isFCMAvailable = remember { FCMHelper.isFCMAvailable(context) }
    val balanceDisplayMode by LocalUserPrefs.current.getHomeAmountDisplayMode()

    val connections by business.connectionsManager.connections.collectAsState()
    val channels by business.peerManager.channelsFlow.collectAsState()
    val inFlightPaymentsCount = remember(channels) { channels.inFlightPaymentsCount() }

    var showConnectionsDialog by remember { mutableStateOf(false) }
    if (showConnectionsDialog) {
        ConnectionDialog(
            connections = connections,
            onClose = { showConnectionsDialog = false },
            onTorClick = onTorClick,
            onElectrumClick = onElectrumClick
        )
    }

    var showTorDisconnectedDialog by remember { mutableStateOf(false) }
    if (showTorDisconnectedDialog) {
        TorDisconnectedDialog(onDismiss = { showTorDisconnectedDialog = false })
    }

    val payments by paymentsViewModel.homePaymentsFlow.collectAsState()
    val swapInBalance = business.balanceManager.swapInWalletBalance.collectAsState()
    val swapInNextTimeout = business.peerManager.swapInNextTimeout.collectAsState(null)
    val finalWallet = business.peerManager.finalWallet.collectAsState()

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
                alpha = 1f
            }
            constrain(balanceRef) {
                top.linkTo(topBarRef.bottom, margin = 36.dp)
                centerHorizontallyTo(collapsibleRef)
                alpha = 1f
            }
            constrain(separatorRef) {
                bottom.linkTo(collapsibleRef.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                alpha = 1f
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
                alpha = 0f
            }
            constrain(balanceRef) {
                bottom.linkTo(parent.top)
                centerHorizontallyTo(parent)
                alpha = 0f
            }
            constrain(separatorRef) {
                bottom.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                alpha = 0f
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

    val balance by business.balanceManager.balance.collectAsState()
    val notices = noticesViewModel.notices
    val notifications by business.notificationsManager.notifications.collectAsState(emptyList())
    val walletContext by application.phoenixGlobal.walletContextManager.walletContext.collectAsState()

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
                onConnectionsStateButtonClick = { showConnectionsDialog = true },
                connections = connections,
                inFlightPaymentsCount = inFlightPaymentsCount,
                onTorClick = onTorClick,
                isFCMUnavailable = fcmTokenFlow.value == null || !isFCMAvailable,
                isPowerSaverMode = isPowerSaverModeOn,
                showRequestLiquidity = walletContext?.isManualLiquidityEnabled == true && channels.canRequestLiquidity(),
                onRequestLiquidityClick = onRequestLiquidityClick,
            )
            HomeBalance(
                modifier = Modifier.layoutId("balance"),
                channels = channels,
                balance = balance,
                balanceDisplayMode = balanceDisplayMode,
                swapInBalance = swapInBalance.value,
                swapInNextTimeout = swapInNextTimeout.value,
                finalWalletBalance = finalWallet.value?.all?.balance ?: 0.sat,
                onNavigateToSwapInWallet = onNavigateToSwapInWallet,
                onNavigateToFinalWallet = onNavigateToFinalWallet,
            )
            PrimarySeparator(modifier = Modifier.layoutId("separator"))
            HomeNotices(
                modifier = Modifier.layoutId("notices"),
                notices = notices.toList(),
                notifications = notifications,
                onNavigateToSwapInWallet = onNavigateToSwapInWallet,
                onNavigateToNotificationsList = onShowNotifications,
                onShowTorDisconnectedClick = { showTorDisconnectedDialog = true }
            )
        }

        LatestPaymentsList(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            balanceDisplayMode = balanceDisplayMode,
            onPaymentClick = onPaymentClick,
            onPaymentsHistoryClick = onPaymentsHistoryClick,
            payments = payments,
        )
        BottomBar(Modifier, onSettingsClick, onReceiveClick, onSendClick)
    }

    val releaseNoteCode = application.globalPrefs.showReleaseNoteSinceCode.collectAsState(initial = null).value
    releaseNoteCode?.let { ReleaseNoteDialog(sinceCode = it) }
}


@Composable
fun TorDisconnectedDialog(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismiss = onDismiss) {
        val navController = navController
        val context = LocalContext.current

        Text(text = stringResource(R.string.tor_disconnected_dialog_title), style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.tor_disconnected_dialog_details_1))
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.tor_disconnected_dialog_details_2))
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.tor_disconnected_dialog_details_3))
        Spacer(Modifier.height(24.dp))
        MutedFilledButton(
            text = stringResource(R.string.tor_disconnected_dialog_open_settings),
            icon = R.drawable.ic_settings,
            onClick = { navController.navigate(Screen.BusinessNavGraph.TorConfig.route) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        TransparentFilledButton(
            text = stringResource(R.string.tor_disconnected_dialog_open_orbot_page),
            icon = R.drawable.ic_external_link,
            onClick = { openLink(context, "https://orbot.app") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )
    }
}