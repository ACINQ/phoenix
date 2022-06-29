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
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.acinq.phoenix.android.home.*
import fr.acinq.phoenix.android.init.*
import fr.acinq.phoenix.android.payments.PaymentDetailsView
import fr.acinq.phoenix.android.payments.ReceiveView
import fr.acinq.phoenix.android.payments.ScanDataView
import fr.acinq.phoenix.android.service.WalletState
import fr.acinq.phoenix.android.settings.*
import fr.acinq.phoenix.android.utils.appBackground
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@Composable
fun AppView(
    mainActivity: MainActivity,
    appVM: AppViewModel,
) {
    val log = logger("AppView")
    log.debug { "init app view composition" }

    val navController = rememberNavController()
    val fiatRates = application.business.currencyManager.ratesFlow.collectAsState(listOf())
    val walletContext = application.business.appConfigurationManager.chainContext.collectAsState(initial = null)
    val context = LocalContext.current
    val isAmountInFiat = UserPrefs.getIsAmountInFiat(context).collectAsState(false)
    val fiatCurrency = UserPrefs.getFiatCurrency(context).collectAsState(initial = FiatCurrency.USD)
    val bitcoinUnit = UserPrefs.getBitcoinUnit(context).collectAsState(initial = BitcoinUnit.Sat)
    val electrumServer = UserPrefs.getElectrumServer(context).collectAsState(initial = null)
    val business = application.business

    CompositionLocalProvider(
        LocalBusiness provides business,
        LocalControllerFactory provides business.controllers,
        LocalNavController provides navController,
        LocalExchangeRates provides fiatRates.value,
        LocalBitcoinUnit provides bitcoinUnit.value,
        LocalFiatCurrency provides fiatCurrency.value,
        LocalShowInFiat provides isAmountInFiat.value,
        LocalWalletContext provides walletContext.value,
        LocalElectrumServer provides electrumServer.value,
    ) {

        // this view model should not be tied to the HomeView composition because it contains a dynamic payments list that must not be lost when switching to another view
        val homeViewModel: HomeViewModel = viewModel(
            factory = HomeViewModel.Factory(
                connectionsFlow = business.connectionsManager.connections,
                paymentsManager = business.paymentsManager,
                controllerFactory = controllerFactory,
                getController = CF::home
            )
        )

        val legacyAppStatus = PrefsDatastore.getLegacyAppStatus(context).collectAsState(null)
        if (legacyAppStatus.value is LegacyAppStatus.Required && navController.currentDestination?.route != Screen.SwitchToLegacy.route) {
            navController.navigate(Screen.SwitchToLegacy.route)
        }

        Column(
            Modifier
                .background(appBackground())
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            NavHost(navController = navController, startDestination = Screen.Startup.route) {
                composable(Screen.Startup.route) {
                    StartupView(
                        mainActivity,
                        appVM,
                        onKeyAbsent = { navController.navigate(Screen.InitWallet.route) },
                        onBusinessStarted = { navController.navigate(Screen.Home.route) }
                    )
                }
                composable(Screen.InitWallet.route) {
                    InitWallet(
                        onCreateWalletClick = { navController.navigate(Screen.CreateWallet.route) },
                        onRestoreWalletClick = { navController.navigate(Screen.RestoreWallet.route) },
                    )
                }
                composable(Screen.CreateWallet.route) {
                    CreateWalletView(onSeedWritten = { navController.navigate(Screen.Startup.route) })
                }
                composable(Screen.RestoreWallet.route) {
                    RestoreWalletView(onSeedWritten = { navController.navigate(Screen.Startup.route) })
                }
                composable(Screen.Home.route) {
                    RequireKey(appVM.walletState.value) {
                        HomeView(
                            homeViewModel = homeViewModel,
                            onPaymentClick = { navigateToPaymentDetails(navController, it) },
                            onSettingsClick = { navController.navigate(Screen.Settings.route) },
                            onReceiveClick = { navController.navigate(Screen.Receive.route) },
                            onSendClick = { navController.navigate(Screen.ScanData.route) { launchSingleTop = true } }
                        )
                    }
                }
                composable(Screen.Receive.route) {
                    ReceiveView()
                }
                composable(Screen.ScanData.route) {
                    ScanDataView(onBackClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) {
                                inclusive = true
                            }
                        }
                    })
                }
                composable(
                    route = "${Screen.PaymentDetails.route}/{direction}/{id}",
                    arguments = listOf(
                        navArgument("direction") { type = NavType.LongType },
                        navArgument("id") { type = NavType.StringType }
                    ),
                ) {
                    val direction = it.arguments?.getLong("direction")
                    val id = it.arguments?.getString("id")
                    val paymentId = if (id != null && direction != null) WalletPaymentId.create(direction, id) else null
                    if (paymentId != null) {
                        PaymentDetailsView(
                            paymentId = paymentId,
                            onBackClick = {
                                navController.navigate(Screen.Home.route)
                            })
                    }
                }
                composable(Screen.Settings.route) {
                    SettingsView()
                }
                composable(Screen.DisplaySeed.route) {
                    SeedView()
                }
                composable(Screen.ElectrumServer.route) {
                    ElectrumView()
                }
                composable(Screen.Channels.route) {
                    ChannelsView()
                }
                composable(Screen.MutualClose.route) {
                    MutualCloseView()
                }
                composable(Screen.Preferences.route) {
                    DisplayPrefsView()
                }
                composable(Screen.About.route) {
                    AboutView()
                }
                composable(Screen.PaymentSettings.route) {
                    PaymentSettingsView()
                }
                composable(Screen.AppLock.route) {
                    AppLockView(
                        mainActivity = mainActivity,
                        appVM = appVM
                    )
                }
                composable(Screen.Logs.route) {
                    LogsView()
                }
                composable(Screen.SwitchToLegacy.route) {
                    LegacySwitcherView(onLegacyFinished = { navController.navigate(Screen.Startup.route) })
                }
            }
        }
    }

    val lastCompletedPayment = business.paymentsManager.lastCompletedPayment.collectAsState().value

    if (lastCompletedPayment != null) {
        log.debug { "completed payment=${lastCompletedPayment}" }
        LaunchedEffect(key1 = lastCompletedPayment.walletPaymentId()) {
            navigateToPaymentDetails(navController, lastCompletedPayment.walletPaymentId())
        }
    }
}

private fun navigateToPaymentDetails(navController: NavController, id: WalletPaymentId) {
    navController.navigate("${Screen.PaymentDetails.route}/${id.dbType.value}/${id.dbId}")
}

@Composable
private fun RequireKey(
    walletState: WalletState?, // TODO: replace by UI lock state
    children: @Composable () -> Unit
) {
    if (walletState !is WalletState.Started) {
        logger().warning { "rejecting access to screen with wallet in state=$walletState" }
        navController.navigate(Screen.Startup)
        Text("redirecting...")
    } else {
        logger().debug { "access to screen granted" }
        children()
    }
}

sealed class LockState {
    sealed class Locked : LockState() {
        object Default : Locked()
        data class WithError(val code: Int?) : Locked()
    }

    object Unlocked : LockState()
}