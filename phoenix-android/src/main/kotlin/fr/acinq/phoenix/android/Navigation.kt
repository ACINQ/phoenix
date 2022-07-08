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

import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


sealed class Screen(val route: String) {
    object SwitchToLegacy : Screen("switchtolegacy")
    object InitWallet : Screen("initwallet")
    object CreateWallet : Screen("createwallet")
    object RestoreWallet : Screen("restorewallet")
    object Startup : Screen("startup")
    object Home : Screen("home")
    object Receive : Screen("receive")
    /**
     * This route also manages the payment flow.
     * TODO: Separate scanning the data from processing the data (aka send payment, process lnurl...). Split to be done at the controller level.
     */
    object ScanData : Screen("readdata")
    object PaymentDetails : Screen("payment")

    // -- settings
    object Settings : Screen("settings")
    object DisplaySeed : Screen("settings/seed")
    object ElectrumServer : Screen("settings/electrum")
    object Channels : Screen("settings/channels")
    object MutualClose : Screen("settings/mutualclose")
    object Preferences : Screen("settings/preferences")
    object About : Screen("settings/about")
    object AppLock : Screen("settings/applock")
    object PaymentSettings : Screen("settings/paymentsettings")
    object Logs : Screen("settings/logs")
}

fun NavHostController.navigate(screen: Screen, arg: List<Any> = emptyList(), builder: NavOptionsBuilder.() -> Unit = {}) {
    val log = newLogger<NavController>(LoggerFactory.default)
    val path = arg.joinToString{ "/$it" }
    val route = "${screen.route}$path"
    log.debug { "navigating from ${currentDestination?.route} to $route" }
    try {
        if (route == currentDestination?.route) {
            log.warning { "cannot navigate to same route" }
        } else {
            navigate(route, builder)
        }
    } catch (e: Exception) {
        log.error(e) { "failed to navigate to $route" }
    }
}
