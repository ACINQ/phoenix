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

import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.NoticesViewModel
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.home.HomeView
import fr.acinq.phoenix.android.payments.details.PaymentDetailsView
import fr.acinq.phoenix.android.payments.receive.ReceiveView
import fr.acinq.phoenix.android.payments.send.SendView
import org.slf4j.LoggerFactory

fun NavGraphBuilder.homeNavGraph(navController: NavController, appViewModel: AppViewModel) {
    businessComposable(Screen.Home.route, appViewModel) { backStackEntry, walletId, business ->

        val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("main-$walletId") }
        val paymentsViewModel = viewModel<PaymentsViewModel>(viewModelStoreOwner = parentEntry, factory = PaymentsViewModel.Factory(business.paymentsManager))

        val noticesViewModel = viewModel<NoticesViewModel>(
            viewModelStoreOwner = parentEntry,
            factory = NoticesViewModel.Factory(
                walletId = walletId,
                appConfigurationManager = business.appConfigurationManager,
                peerManager = business.peerManager,
                connectionsManager = business.connectionsManager,
            )
        )
        //?.also { monitorPermission(it) }

        HomeView(
            paymentsViewModel = paymentsViewModel,
            noticesViewModel = noticesViewModel,
            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
            onSettingsClick = { navController.navigate(Screen.Settings.route) },
            onReceiveClick = { navController.navigate(Screen.Receive.route) },
            onSendClick = { navController.navigate(Screen.Send.route) },
            onPaymentsHistoryClick = { navController.navigate(Screen.PaymentsHistory.route) },
            onTorClick = { navController.navigate(Screen.TorConfig.route) },
            onElectrumClick = { navController.navigate(Screen.ElectrumServer.route) },
            onNavigateToSwapInWallet = { navController.navigate(Screen.WalletInfo.SwapInWallet.route) },
            onNavigateToFinalWallet = { navController.navigate(Screen.WalletInfo.FinalWallet.route) },
            onShowNotifications = { navController.navigate(Screen.Notifications.route) },
            onRequestLiquidityClick = { navController.navigate(Screen.LiquidityRequest.route) },
        )
    }
}

fun NavGraphBuilder.paymentsNavGraph(navController: NavController, appViewModel: AppViewModel) {
    val log = LoggerFactory.getLogger("Navigation")

    businessComposable(Screen.Receive.route, appViewModel) { _, walletId, _ ->
        ReceiveView(
            walletId = walletId,
            onBackClick = { navController.popBackStack() },
            onScanDataClick = { navController.navigate("${Screen.Send.route}?openScanner=true&forceNavOnBack=true") },
            onFeeManagementClick = { navController.navigate(Screen.LiquidityPolicy.route) },
        )
    }

    businessComposable(
        route = "${Screen.PaymentDetails.route}?id={id}&fromEvent={fromEvent}",
        appViewModel = appViewModel,
        arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("fromEvent") {
                type = NavType.BoolType
                defaultValue = false
            }
        ),
        deepLinks = listOf(navDeepLink { uriPattern = "phoenix:payments/{walletid}/{id}" })
    ) { backstackEntry, walletId, _ ->
        val walletIdDeeplink = backstackEntry.arguments!!.getString("walletid")?.let { WalletId(it) }
        val paymentId = try {
            UUID.fromString(backstackEntry.arguments!!.getString("id")!!)
        } catch (_: Exception) {
            null
        }
        if (walletIdDeeplink != null && walletIdDeeplink != walletId) {
            LaunchedEffect(Unit) { navController.popToHome() }
        } else if (paymentId != null) {
            val fromEvent = backstackEntry.arguments?.getBoolean("fromEvent") ?: false
            PaymentDetailsView(
                paymentId = paymentId,
                onBackClick = {
                    val previousNav = navController.previousBackStackEntry
                    if (fromEvent && previousNav?.destination?.route == Screen.Send.route) {
                        navController.popToHome()
                    } else if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.popToHome()
                    }
                },
                fromEvent = fromEvent
            )
        }
    }

    businessComposable(
        route = "${Screen.Send.route}?input={input}&openScanner={openScanner}&forceNavOnBack={forceNavOnBack}",
        appViewModel = appViewModel,
        deepLinkPrefix = "scanview:",
        arguments = listOf(
            navArgument("input") { type = NavType.StringType ; nullable = true },
            navArgument("openScanner") { type = NavType.BoolType ; defaultValue = false },
            navArgument("forceNavOnBack") { type = NavType.BoolType ; defaultValue = false },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "lightning:{data}" },
            navDeepLink { uriPattern = "bitcoin:{data}" },
            navDeepLink { uriPattern = "lnurl:{data}" },
            navDeepLink { uriPattern = "lnurlp:{data}" },
            navDeepLink { uriPattern = "lnurlw:{data}" },
            navDeepLink { uriPattern = "keyauth:{data}" },
            navDeepLink { uriPattern = "phoenix:lightning:{data}" },
            navDeepLink { uriPattern = "phoenix:bitcoin:{data}" },
            navDeepLink { uriPattern = "scanview:{data}" },
        )
    ) { backStackEntry, walletId, _ ->
        @Suppress("DEPRECATION")
        val intent = try {
            backStackEntry.arguments?.getParcelable<Intent>(NavController.KEY_DEEP_LINK_INTENT)
        } catch (e: Exception) {
            null
        }
        // prevents forwarding an internal deeplink intent coming from androidx-navigation framework.
        // TODO properly parse deeplinks following f0ae90444a23cc17d6d7407dfe43c0c8d20e62fc
        val isIntentFromNavigation = intent?.dataString?.contains("androidx.navigation") ?: true
        log.debug("isIntentFromNavigation=$isIntentFromNavigation")
        val input = if (isIntentFromNavigation) {
            backStackEntry.arguments?.getString("input")
        } else {
            intent?.data?.toString()?.substringAfter("scanview:")
        }

        log.info("navigating to send-payment with input=$input")
        SendView(
            walletId = walletId,
            initialInput = input,
            fromDeepLink = !isIntentFromNavigation,
            immediatelyOpenScanner = backStackEntry.arguments?.getBoolean("openScanner") ?: false,
            forceNavOnBack = backStackEntry.arguments?.getBoolean("forceNavOnBack") ?: false,
        )
    }
}