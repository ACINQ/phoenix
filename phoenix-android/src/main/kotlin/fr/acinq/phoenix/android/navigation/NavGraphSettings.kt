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

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.payments.history.PaymentsExportView
import fr.acinq.phoenix.android.payments.history.PaymentsHistoryView
import fr.acinq.phoenix.android.payments.send.liquidity.RequestLiquidityView
import fr.acinq.phoenix.android.settings.AboutView
import fr.acinq.phoenix.android.settings.AppAccessSettings
import fr.acinq.phoenix.android.settings.DisplayPrefsView
import fr.acinq.phoenix.android.settings.ExperimentalView
import fr.acinq.phoenix.android.settings.ForceCloseView
import fr.acinq.phoenix.android.settings.LogsView
import fr.acinq.phoenix.android.settings.NotificationsView
import fr.acinq.phoenix.android.settings.PaymentSettingsView
import fr.acinq.phoenix.android.settings.reset.ResetWallet
import fr.acinq.phoenix.android.settings.SettingsContactsView
import fr.acinq.phoenix.android.settings.SettingsView
import fr.acinq.phoenix.android.settings.TorConfigView
import fr.acinq.phoenix.android.settings.displayseed.DisplaySeedView
import fr.acinq.phoenix.android.settings.electrum.ElectrumView
import fr.acinq.phoenix.android.settings.fees.AdvancedIncomingFeePolicy
import fr.acinq.phoenix.android.settings.fees.LiquidityPolicyView


fun NavGraphBuilder.settingsNavGraph(navController: NavController, appViewModel: AppViewModel, business: PhoenixBusiness?) {

    businessComposable(Screen.Settings.route, appViewModel) { _, _, _ ->
        val notifications = business?.notificationsManager?.notifications?.collectAsState()
        SettingsView(appViewModel, emptyList(), notifications?.value ?: emptyList())
    }

    businessComposable(Screen.ElectrumServer.route, appViewModel) { _, _, _ ->
        ElectrumView(onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.TorConfig.route, appViewModel) { _, walletId, _ ->
        TorConfigView(walletId = walletId, onBackClick = { navController.popBackStack() }, onBusinessTeardown = { navController.popToHome() })
    }

    businessComposable(Screen.DisplayPrefs.route, appViewModel) { _, _, business ->
        DisplayPrefsView(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.About.route, appViewModel) { _, _, _ ->
        AboutView()
    }

    businessComposable("${Screen.PaymentSettings.route}?showAuthSchemeDialog={showAuthSchemeDialog}", arguments = listOf(
        navArgument("showAuthSchemeDialog") { type = NavType.BoolType; defaultValue = false }
    ), appViewModel = appViewModel) { backStackEntry, _, _ ->
        val showAuthSchemeDialog = backStackEntry.arguments?.getBoolean("showAuthSchemeDialog") ?: false
        PaymentSettingsView(initialShowLnurlAuthSchemeDialog = showAuthSchemeDialog)
    }

    businessComposable(Screen.AppAccess.route, appViewModel) { _, walletId, _ ->
        AppAccessSettings(walletId = walletId, onBackClick = { navController.popBackStack() }, onScheduleAutoLock = appViewModel::scheduleAutoLock)
    }

    businessComposable(Screen.Logs.route, appViewModel) { _, _, _ ->
        LogsView()
    }

    businessComposable(Screen.PaymentsHistory.route, appViewModel) { backStackEntry, walletId, business ->
        val paymentsViewModel = viewModel<PaymentsViewModel>(factory = PaymentsViewModel.Factory(business.paymentsManager))
        PaymentsHistoryView(
            onBackClick = { navController.popBackStack() },
            paymentsViewModel = paymentsViewModel,
            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
            onCsvExportClick = { navController.navigate(Screen.PaymentsExport.route) },
        )
    }

    businessComposable(Screen.PaymentsExport.route, appViewModel) { _, walletId, business ->
        PaymentsExportView(
            walletId = walletId,
            business = business,
            onBackClick = {
                navController.navigate(Screen.PaymentsHistory.route) {
                    popUpTo(Screen.PaymentsHistory.route) { inclusive = true }
                }
            }
        )
    }

    businessComposable(Screen.DisplaySeed.route, appViewModel) { _, walletId, _ ->
        DisplaySeedView(onBackClick = { navController.popBackStack() }, walletId = walletId)
    }

    businessComposable(Screen.Notifications.route, appViewModel) { backStackEntry, walletId, business ->
        val noticesViewModel = viewModel<NoticesViewModel>(
            key = walletId.nodeIdHash,
            factory = NoticesViewModel.Factory(
                walletId = walletId,
                appConfigurationManager = business.appConfigurationManager,
                peerManager = business.peerManager,
                connectionsManager = business.connectionsManager,
            )
        )
        NotificationsView(
            business = business,
            noticesViewModel = noticesViewModel,
            onBackClick = { navController.popBackStack() },
        )
    }

    businessComposable(Screen.LiquidityPolicy.route, appViewModel, deepLinks = listOf(navDeepLink { uriPattern = "phoenix:liquiditypolicy" })) { _, _, business ->
        LiquidityPolicyView(
            business = business,
            onBackClick = { navController.popBackStack() },
            onAdvancedClick = { navController.navigate(Screen.AdvancedLiquidityPolicy.route) },
        )
    }

    businessComposable(Screen.LiquidityRequest.route, appViewModel, deepLinks = listOf(navDeepLink { uriPattern = "phoenix:requestliquidity" })) { _, walletId, business ->
        RequestLiquidityView(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.AdvancedLiquidityPolicy.route, appViewModel) { _, _, business ->
        AdvancedIncomingFeePolicy(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable("${Screen.Contacts.route}?showAddContactDialog={showAddContactDialog}", appViewModel, arguments = listOf(
        navArgument("showAddContactDialog") { type = NavType.BoolType; defaultValue = false }
    )) { backStackEntry, _, business ->
        val showAddContactDialog = backStackEntry.arguments?.getBoolean("showAddContactDialog") ?: false
        SettingsContactsView(business = business, onBackClick = { navController.popBackStack() }, immediatelyShowAddContactDialog = showAddContactDialog)
    }

    businessComposable(Screen.Experimental.route, appViewModel) { _, walletId, business ->
        ExperimentalView(walletId = walletId, business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.ResetWallet.route, appViewModel) { _, walletId, business ->
        ResetWallet(walletId = walletId, business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.ForceClose.route, appViewModel) { _, _, _ ->
        ForceCloseView(onBackClick = { navController.popBackStack() })
    }
}