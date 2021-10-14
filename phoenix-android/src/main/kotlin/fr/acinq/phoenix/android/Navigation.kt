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

package fr.acinq.phoenix.android

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.utils.logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


sealed class Screen(val route: String, val arg: String? = null) {
    val fullRoute by lazy { if (arg.isNullOrBlank()) route else "${route}/{$arg}" }

    object InitWallet : Screen("initwallet")
    object CreateWallet : Screen("createwallet")
    object RestoreWallet : Screen("restorewallet")
    object Startup : Screen("startup")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object DisplaySeed : Screen("settings/seed")
    object ElectrumServer : Screen("settings/electrum")
    object Channels : Screen("settings/channels")
    object MutualClose : Screen("settings/mutualclose")
    object Preferences : Screen("settings/preferences")
    object Receive : Screen("receive")
    object ReadData : Screen("readdata")
    object Send : Screen("send", "request")
}

@Composable
fun requireWalletPresent(
    inScreen: Screen,
    children: @Composable () -> Unit
) {
    if (keyState !is KeyState.Present) {
        logger().warning { "accessing screen=$inScreen with keyState=$keyState" }
        navController.navigate(Screen.Startup)
        Text("redirecting...")
    } else {
        logger().debug { "access to screen=$inScreen granted" }
        children()
    }
}

fun NavHostController.navigate(screen: Screen, arg: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val route = if (arg.isNullOrBlank()) screen.route else "${screen.route}/$arg"
    newLogger<NavController>(LoggerFactory.default).debug { "navigating to $route" }
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        newLogger(LoggerFactory.default).error(e) { "failed to navigate to $route" }
    }
}
