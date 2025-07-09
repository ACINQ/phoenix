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
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
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
import fr.acinq.phoenix.android.settings.ResetWallet
import fr.acinq.phoenix.android.settings.SettingsContactsView
import fr.acinq.phoenix.android.settings.SettingsView
import fr.acinq.phoenix.android.settings.TorConfigView
import fr.acinq.phoenix.android.settings.displayseed.DisplaySeedView
import fr.acinq.phoenix.android.settings.electrum.ElectrumView
import fr.acinq.phoenix.android.settings.fees.AdvancedIncomingFeePolicy
import fr.acinq.phoenix.android.settings.fees.LiquidityPolicyView


fun NavGraphBuilder.baseSettingsNavGraph(navController: NavController, appViewModel: AppViewModel, noticesViewModel: NoticesViewModel?, business: PhoenixBusiness?) {
    composable(Screen.Settings.route) {
        val notifications = business?.notificationsManager?.notifications?.collectAsState()
        SettingsView(noticesViewModel?.notices?.toList() ?: emptyList(), notifications?.value ?: emptyList())
    }

    composable(Screen.ElectrumServer.route) {
        ElectrumView(onBackClick = { navController.popBackStack() })
    }

    composable(Screen.TorConfig.route) {
        TorConfigView(onBackClick = { navController.popBackStack() }, onBusinessTeardown = { navController.popToHome() })
    }

    composable(Screen.DisplayPrefs.route) {
        DisplayPrefsView()
    }

    composable(Screen.About.route) {
        AboutView()
    }

    composable("${Screen.PaymentSettings.route}?showAuthSchemeDialog={showAuthSchemeDialog}", arguments = listOf(
        navArgument("showAuthSchemeDialog") { type = NavType.BoolType; defaultValue = false }
    )) {
        val showAuthSchemeDialog = it.arguments?.getBoolean("showAuthSchemeDialog") ?: false
        PaymentSettingsView(initialShowLnurlAuthSchemeDialog = showAuthSchemeDialog)
    }

    composable(Screen.AppAccess.route) {
        AppAccessSettings(onBackClick = { navController.popBackStack() }, onScheduleAutoLock = appViewModel::scheduleAutoLock)
    }

    composable(Screen.Logs.route) {
        LogsView()
    }
}

fun NavGraphBuilder.miscSettingsNavGraph(navController: NavController, paymentsViewModel: PaymentsViewModel, noticesViewModel: NoticesViewModel) {

    businessComposable(Screen.PaymentsHistory.route) {
        PaymentsHistoryView(
            onBackClick = { navController.popBackStack() },
            paymentsViewModel = paymentsViewModel,
            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
            onCsvExportClick = { navController.navigate(Screen.PaymentsExport.route) },
        )
    }

    businessComposable(Screen.PaymentsExport.route) {
        PaymentsExportView(onBackClick = {
            navController.navigate(Screen.PaymentsHistory.route) {
                popUpTo(Screen.PaymentsHistory.route) { inclusive = true }
            }
        })
    }

    businessComposable(Screen.DisplaySeed.route) {
        DisplaySeedView()
    }

    businessComposable(Screen.Notifications.route) {
        NotificationsView(
            noticesViewModel = noticesViewModel,
            onBackClick = { navController.popBackStack() },
        )
    }

    businessComposable(Screen.LiquidityPolicy.route, deepLinks = listOf(navDeepLink { uriPattern = "phoenix:liquiditypolicy" })) {
        LiquidityPolicyView(
            onBackClick = { navController.popBackStack() },
            onAdvancedClick = { navController.navigate(Screen.AdvancedLiquidityPolicy.route) },
        )
    }

    businessComposable(Screen.LiquidityRequest.route, deepLinks = listOf(navDeepLink { uriPattern = "phoenix:requestliquidity" })) {
        RequestLiquidityView(onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.AdvancedLiquidityPolicy.route) {
        AdvancedIncomingFeePolicy(onBackClick = { navController.popBackStack() })
    }

    businessComposable("${Screen.Contacts.route}?showAddContactDialog={showAddContactDialog}", arguments = listOf(
        navArgument("showAddContactDialog") { type = NavType.BoolType; defaultValue = false }
    )) {
        val showAddContactDialog = it.arguments?.getBoolean("showAddContactDialog") ?: false
        SettingsContactsView(onBackClick = { navController.popBackStack() }, immediatelyShowAddContactDialog = showAddContactDialog)
    }

    businessComposable(Screen.Experimental.route) {
        ExperimentalView(onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.ResetWallet.route) {
        ResetWallet(onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.ForceClose.route) {
        ForceCloseView(onBackClick = { navController.popBackStack() })
    }
}