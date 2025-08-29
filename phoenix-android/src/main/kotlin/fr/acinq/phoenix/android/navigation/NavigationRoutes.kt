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

sealed class Screen(val route: String) {
    data object Intro : Screen("intro")
    data object InitWallet : Screen("initwallet")
    data object CreateWallet : Screen("createwallet")
    data object RestoreWallet : Screen("restorewallet")
    data object Startup : Screen("startup")
    data object StartupRecovery : Screen("startuprecovery")
    data object Home : Screen("home")
    data object Receive : Screen("receive")
    data object Send : Screen("send")
    data object PaymentDetails : Screen("payments")
    data object PaymentsHistory : Screen("payments/all")
    data object PaymentsExport : Screen("payments/export")

    // -- settings
    data object Settings : Screen("settings")
    data object DisplaySeed : Screen("settings/seed")
    data object ElectrumServer : Screen("settings/electrum")
    data object TorConfig : Screen("settings/tor")
    data object Channels : Screen("settings/channels")
    data object ChannelDetails : Screen("settings/channeldetails")
    data object ImportChannelsData : Screen("settings/importchannels")
    data object SpendChannelAddress : Screen("settings/spendchanneladdress")
    data object MutualClose : Screen("settings/mutualclose")
    data object ForceClose : Screen("settings/forceclose")
    data object DisplayPrefs : Screen("settings/displayPrefs")
    data object About : Screen("settings/about")
    data object AppAccess : Screen("settings/appaccess")
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
