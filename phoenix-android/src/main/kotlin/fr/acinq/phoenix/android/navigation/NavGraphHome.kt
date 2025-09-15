/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.Notice
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.home.HomeView
import kotlinx.coroutines.flow.first

fun NavGraphBuilder.homeNavGraph(navController: NavController, appViewModel: AppViewModel) {
    businessComposable(Screen.BusinessNavGraph.Home.route, appViewModel) { backStackEntry, walletId, business ->

        val homeGraphEntry = remember(navController.previousBackStackEntry) { navController.getBackStackEntry(Screen.BusinessNavGraph.route) }
        val paymentsViewModel = viewModel<PaymentsViewModel>(factory = PaymentsViewModel.Factory(business.paymentsManager), viewModelStoreOwner = homeGraphEntry)

        val noticesViewModel = viewModel<NoticesViewModel>(
            factory = NoticesViewModel.Factory(
                walletId = walletId,
                appConfigurationManager = business.appConfigurationManager,
                peerManager = business.peerManager,
                connectionsManager = business.connectionsManager,
            )
        ).also { monitorPermission(it) }

        HomeView(
            walletId = walletId,
            business = business,
            paymentsViewModel = paymentsViewModel,
            noticesViewModel = noticesViewModel,
            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
            onSettingsClick = { navController.navigate(Screen.BusinessNavGraph.Settings.route) },
            onReceiveClick = { navController.navigate(Screen.BusinessNavGraph.Receive.route) },
            onSendClick = { navController.navigate(Screen.BusinessNavGraph.Send.route) },
            onPaymentsHistoryClick = { navController.navigate(Screen.BusinessNavGraph.PaymentsHistory.route) },
            onTorClick = { navController.navigate(Screen.BusinessNavGraph.TorConfig.route) },
            onElectrumClick = { navController.navigate(Screen.BusinessNavGraph.ElectrumServer.route) },
            onNavigateToSwapInWallet = { navController.navigate(Screen.BusinessNavGraph.WalletInfo.SwapInWallet.route) },
            onNavigateToFinalWallet = { navController.navigate(Screen.BusinessNavGraph.WalletInfo.FinalWallet.route) },
            onShowNotifications = { navController.navigate(Screen.BusinessNavGraph.Notifications.route) },
            onRequestLiquidityClick = { navController.navigate(Screen.BusinessNavGraph.LiquidityRequest.route) },
        )
    }
}

@SuppressLint("ComposableNaming")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun monitorPermission(noticesViewModel: NoticesViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val userPrefs = LocalUserPrefs.current
        val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        if (!notificationPermission.status.isGranted) {
            LaunchedEffect(Unit) {
                if (userPrefs?.getShowNotificationPermissionReminder?.first() == true) {
                    noticesViewModel.addNotice(Notice.NotificationPermission)
                }
            }
        } else {
            noticesViewModel.removeNotice<Notice.NotificationPermission>()
        }
        LaunchedEffect(userPrefs) {
            userPrefs?.getShowNotificationPermissionReminder?.collect {
                if (it && !notificationPermission.status.isGranted) {
                    noticesViewModel.addNotice(Notice.NotificationPermission)
                } else {
                    noticesViewModel.removeNotice<Notice.NotificationPermission>()
                }
            }
        }
    }
}