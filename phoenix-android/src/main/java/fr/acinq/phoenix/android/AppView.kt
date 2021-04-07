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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.android.home.HomeView
import fr.acinq.phoenix.android.home.ReadDataView
import fr.acinq.phoenix.android.home.StartupView
import fr.acinq.phoenix.android.init.CreateWalletView
import fr.acinq.phoenix.android.init.InitWallet
import fr.acinq.phoenix.android.init.RestoreWalletView
import fr.acinq.phoenix.android.receive.ReceiveView
import fr.acinq.phoenix.android.send.SendView
import fr.acinq.phoenix.android.settings.*
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@ExperimentalMaterialApi
@Composable
fun AppView(appVM: AppViewModel) {
    val log = logger()
    val navController = rememberNavController()
    val fiatRates = application.business.currencyManager.ratesFlow.collectAsState(listOf())
    CompositionLocalProvider(
        LocalBusiness provides application.business,
        LocalControllerFactory provides application.business.controllers,
        LocalNavController provides navController,
        LocalKeyState provides appVM.keyState,
        LocalFiatRates provides fiatRates.value,
        LocalBitcoinUnit provides appVM.bitcoinUnit,
        LocalFiatCurrency provides appVM.fiatCurrency,
        LocalShowInFiat provides appVM.showInFiat,
    ) {
        Column(
            Modifier
                .background(appBackground())
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            NavHost(navController = navController, startDestination = Screen.Startup.route) {
                composable(Screen.Startup.fullRoute) {
                    StartupView()
                }
                composable(Screen.InitWallet.fullRoute) {
                    InitWallet()
                }
                composable(Screen.CreateWallet.fullRoute) {
                    CreateWalletView(appVM)
                }
                composable(Screen.RestoreWallet.fullRoute) {
                    RestoreWalletView(appVM)
                }
                composable(Screen.Home.fullRoute) {
                    HomeView(appVM)
                }
                composable(Screen.Receive.fullRoute) {
                    ReceiveView()
                }
                composable(Screen.ReadData.fullRoute) {
                    ReadDataView()
                }
                composable(Screen.Send.fullRoute) { backStackEntry ->
                    SendView(backStackEntry.arguments?.getString("request")?.run {
                        log.info { "redirecting to send view with invoice=$this" }
                        PaymentRequest.read(cleanUpInvoice(this))
                    })
                }
                composable(Screen.Settings.fullRoute) {
                    SettingsView()
                }
                composable(Screen.DisplaySeed.fullRoute) {
                    SeedView(appVM)
                }
                composable(Screen.ElectrumServer.fullRoute) {
                    ElectrumView()
                }
                composable(Screen.Channels.fullRoute) {
                    ChannelsView()
                }
                composable(Screen.MutualClose.fullRoute) {
                    MutualCloseView()
                }
            }
        }
    }
}

private fun cleanUpInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
        trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
        trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
        else -> trimmed
    }
}
