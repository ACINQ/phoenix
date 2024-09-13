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
import androidx.navigation.NavOptionsBuilder
import org.slf4j.LoggerFactory


sealed class Screen(val route: String) {
    data object SwitchToLegacy : Screen("switchtolegacy")
    data object Intro : Screen("intro")
    data object InitWallet : Screen("initwallet")
    data object CreateWallet : Screen("createwallet")
    data object RestoreWallet : Screen("restorewallet")
    data object Startup : Screen("startup")
    data object Home : Screen("home")
    data object Receive : Screen("receive")
    data object PrepareSend : Screen("preparesend")
    /**
     * This route also manages the payment flow.
     * TODO: Separate scanning the data from processing the data (aka send payment, process lnurl...). Split to be done at the controller level.
     */
    data object ScanData : Screen("readdata")
    data object PaymentDetails : Screen("payments")
    data object PaymentsHistory : Screen("payments/all")
    data object PaymentsCsvExport : Screen("payments/export")

    // -- settings
    data object Settings : Screen("settings")
    data object DisplaySeed : Screen("settings/seed")
    data object ElectrumServer : Screen("settings/electrum")
    data object TorConfig : Screen("settings/tor")
    data object Channels : Screen("settings/channels")
    data object ChannelDetails : Screen("settings/channeldetails")
    data object ImportChannelsData : Screen("settings/importchannels")
    data object MutualClose : Screen("settings/mutualclose")
    data object ForceClose : Screen("settings/forceclose")
    data object Preferences : Screen("settings/preferences")
    data object About : Screen("settings/about")
    data object AppLock : Screen("settings/applock")
    data object PaymentSettings : Screen("settings/paymentsettings")
    data object Logs : Screen("settings/logs")
    data object WalletInfo : Screen("settings/walletinfo") {
        data object SwapInWallet: Screen("settings/walletinfo/swapin")
        data object SwapInAddresses: Screen("settings/walletinfo/swapinaddresses")
        data object SwapInSigner: Screen("settings/walletinfo/swapinsigner")
        data object FinalWallet: Screen("settings/walletinfo/final")
        data object FinalWalletRefund: Screen("settings/walletinfo/finalrefund")
        data object SwapInRefund: Screen("settings/walletinfo/swapinrefund")
    }
    data object LiquidityPolicy: Screen("settings/liquiditypolicy")
    data object LiquidityRequest: Screen("settings/requestliquidity")
    data object AdvancedLiquidityPolicy: Screen("settings/advancedliquiditypolicy")
    data object Notifications: Screen("notifications")
    data object Contacts: Screen("settings/contacts")
    data object ResetWallet: Screen("settings/resetwallet")
    data object Experimental: Screen("settings/experimental")
}

fun NavController.navigate(screen: Screen, arg: List<Any> = emptyList(), builder: NavOptionsBuilder.() -> Unit = {}) {
    val log = LoggerFactory.getLogger("NavController")
    val path = arg.joinToString{ "/$it" }
    val route = "${screen.route}$path"
    log.debug("navigating from ${currentDestination?.route} to $route")
    try {
        if (route == currentDestination?.route) {
            log.warn("cannot navigate to same route")
        } else {
            navigate(route, builder)
        }
    } catch (e: Exception) {
        log.error("failed to navigate to $route: " , e)
    }
}
