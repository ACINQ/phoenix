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

import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.initwallet.InitNewWallet
import fr.acinq.phoenix.android.initwallet.create.CreateWalletView
import fr.acinq.phoenix.android.initwallet.restore.RestoreWalletView
import fr.acinq.phoenix.android.intro.IntroView
import fr.acinq.phoenix.android.startup.StartupView
import fr.acinq.phoenix.android.startup.StartupViewModel
import org.slf4j.LoggerFactory

fun NavGraphBuilder.baseNavGraph(navController: NavController, appViewModel: AppViewModel) {
    val log = LoggerFactory.getLogger("Navigation")

    // startup is the default navigation route
    composable(
        route = "${Screen.Startup.route}?next={next}",
        arguments = listOf(
            navArgument("next") { type = NavType.StringType; nullable = true }
        ),
    ) {
        val nextScreenLink = it.arguments?.getString("next")
        val startupViewModel = viewModel<StartupViewModel>(viewModelStoreOwner = it, factory = StartupViewModel.Factory(application))
        StartupView(
            appViewModel = appViewModel,
            startupViewModel = startupViewModel,
            onShowIntro = { navController.navigate(Screen.Intro.route) },
            onSeedNotFound = { navController.navigate(Screen.InitWallet.route) },
            onWalletReady = {
                val next = nextScreenLink?.takeUnless { it.isBlank() }?.let { Uri.parse(it) }

                if (next == null) {
                    log.debug("redirecting from startup to home")
                    navController.popToHome()
                } else if (!navController.graph.hasDeepLink(next)) {
                    log.debug("redirecting from startup to home, ignoring invalid next=[$nextScreenLink]")
                    navController.popToHome()
                } else {
                    log.debug("redirecting from startup to {}", next)
                    navController.navigate(next, navOptions = navOptions {
                        popUpTo(navController.graph.id) { inclusive = true }
                    })
                }
            }
        )
    }

    composable(Screen.Intro.route) {
        IntroView(onFinishClick = { navController.navigate(Screen.Startup.route) })
    }

    composable(Screen.InitWallet.route) {
        InitNewWallet(
            onCreateWalletClick = { navController.navigate(Screen.CreateWallet.route) },
            onRestoreWalletClick = { navController.navigate(Screen.RestoreWallet.route) },
            onSettingsClick = { navController.navigate(Screen.Settings.route) }
        )
    }

    composable(Screen.CreateWallet.route) {
        CreateWalletView(onSeedWritten = { nodeId ->
            navController.navigate(Screen.Startup.route)
            appViewModel.listAvailableWallets()
            appViewModel.switchToWallet(nodeId = nodeId)
        })
    }

    composable(Screen.RestoreWallet.route) {
        RestoreWalletView(onRestoreDone = { nodeId ->
            navController.navigate(Screen.Startup.route)
            appViewModel.listAvailableWallets()
            appViewModel.switchToWallet(nodeId = nodeId)
        })
    }
}