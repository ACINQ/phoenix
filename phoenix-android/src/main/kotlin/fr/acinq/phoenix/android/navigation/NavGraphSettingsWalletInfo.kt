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

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navDeepLink
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.settings.walletinfo.FinalWalletInfo
import fr.acinq.phoenix.android.settings.walletinfo.FinalWalletRefundView
import fr.acinq.phoenix.android.settings.walletinfo.SendSwapInRefundView
import fr.acinq.phoenix.android.settings.walletinfo.SwapInAddresses
import fr.acinq.phoenix.android.settings.walletinfo.SwapInSignerView
import fr.acinq.phoenix.android.settings.walletinfo.SwapInWallet
import fr.acinq.phoenix.android.settings.walletinfo.WalletInfoView

fun NavGraphBuilder.walletInfoNavGraph(navController: NavController, appViewModel: AppViewModel) {

    businessComposable(Screen.WalletInfo.route, appViewModel) { _, _, business ->
        WalletInfoView(
            business = business,
            onBackClick = { navController.popBackStack() },
            onLightningWalletClick = { navController.navigate(Screen.Channels.route) },
            onSwapInWalletClick = { navController.navigate(Screen.WalletInfo.SwapInWallet.route) },
            onSwapInWalletInfoClick = { navController.navigate(Screen.WalletInfo.SwapInAddresses.route) },
            onFinalWalletClick = { navController.navigate(Screen.WalletInfo.FinalWallet.route) },
        )
    }

    businessComposable(
        route = Screen.WalletInfo.SwapInWallet.route,
        appViewModel = appViewModel,
        deepLinks = listOf(
            navDeepLink { uriPattern = "phoenix:swapinwallet" }
        )
    ) { _, _, business ->
        SwapInWallet(
            business = business,
            onBackClick = { navController.popBackStack() },
            onViewChannelPolicyClick = { navController.navigate(Screen.LiquidityPolicy.route) },
            onAdvancedClick = { navController.navigate(Screen.WalletInfo.SwapInSigner.route) },
            onSpendRefundable = { navController.navigate(Screen.WalletInfo.SwapInRefund.route) },
        )
    }

    businessComposable(Screen.WalletInfo.SwapInAddresses.route, appViewModel) { _, _, business ->
        SwapInAddresses(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.WalletInfo.SwapInSigner.route, appViewModel) { _, _, business ->
        SwapInSignerView(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.WalletInfo.SwapInRefund.route, appViewModel) { _, walletId, business ->
        SendSwapInRefundView(walletId = walletId, business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.WalletInfo.FinalWalletRefund.route, appViewModel) { _, walletId, business ->
        FinalWalletRefundView(walletId = walletId, business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.WalletInfo.FinalWallet.route, appViewModel) { _, _, business ->
        FinalWalletInfo(business = business, onBackClick = { navController.popBackStack() }, onSpendClick = { navController.navigate(Screen.WalletInfo.FinalWalletRefund.route) })
    }
}