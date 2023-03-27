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

import android.net.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import fr.acinq.phoenix.android.home.*
import fr.acinq.phoenix.android.init.*
import fr.acinq.phoenix.android.intro.IntroView
import fr.acinq.phoenix.android.payments.*
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


@Composable
fun AppView(
    appVM: AppViewModel,
    navController: NavHostController,
) {
    val log = logger("AppView")
    log.debug { "init app view composition" }

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

        // we keep a view model storing payments so that we don't have to fetch them every time
        val paymentsViewModel: PaymentsViewModel = viewModel(
            factory = PaymentsViewModel.Factory(
                connectionsFlow = business.connectionsManager.connections,
                paymentsManager = business.paymentsManager,
            )
        )

        val legacyAppStatus = PrefsDatastore.getLegacyAppStatus(context).collectAsState(null)
        if (legacyAppStatus.value is LegacyAppStatus.Required && navController.currentDestination?.route != Screen.SwitchToLegacy.route) {
            navController.navigate(Screen.SwitchToLegacy.route)
        }

        val scannerDeepLinks = remember {
            listOf(
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
        }

        Column(
            Modifier
                .background(appBackground())
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            NavHost(navController = navController, startDestination = "${Screen.Startup.route}?next={next}") {
                composable(
                    route = "${Screen.Startup.route}?next={next}",
                    arguments = listOf(
                        navArgument("next") { type = NavType.StringType ; nullable = true }
                    ),
                ) {
                    val nextScreenLink = it.arguments?.getString("next")
                    log.debug { "navigating to startup with next=$nextScreenLink" }
                    StartupView(
                        appVM = appVM,
                        onShowIntro = { navController.navigate(Screen.Intro.route) },
                        onKeyAbsent = { navController.navigate(Screen.InitWallet.route) },
                        onBusinessStarted = {
                            val next = nextScreenLink?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                            if (next == null) {
                                popToHome(navController)
                            } else {
                                navController.navigate(next, navOptions = navOptions { popUpTo(Screen.Home.route) { inclusive = true } })
                            }
                        }
                    )
                }
                composable(Screen.Intro.route) {
                    IntroView(onFinishClick = { navController.navigate(Screen.Startup.route) })
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
                    RequireStarted(appVM.walletState.value) {
                        HomeView(
                            paymentsViewModel = paymentsViewModel,
                            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                            onSettingsClick = { navController.navigate(Screen.Settings.route) },
                            onReceiveClick = { navController.navigate(Screen.Receive.route) },
                            onSendClick = { navController.navigate(Screen.ScanData.route) { launchSingleTop = true } },
                            onPaymentsHistoryClick = { navController.navigate(Screen.PaymentsHistory.route) },
                            onTorClick = { navController.navigate(Screen.TorConfig) },
                            onElectrumClick = { navController.navigate(Screen.ElectrumServer) }
                        )
                    }
                }
                composable(Screen.Receive.route) {
                    ReceiveView(
                        onSwapInReceived = { popToHome(navController) },
                    )
                }
                composable(Screen.ScanData.route, deepLinks = scannerDeepLinks) {
                    val input = it.arguments?.getString("data")
                    RequireStarted(appVM.walletState.value, nextUri = "scanview:$input") {
                        ScanDataView(
                            input = input,
                            onBackClick = { popToHome(navController) },
                            onAuthSchemeInfoClick = { navController.navigate("${Screen.PaymentSettings.route}/true") }
                        )
                    }
                }
                composable(
                    route = "${Screen.PaymentDetails.route}?direction={direction}&id={id}&fromEvent={fromEvent}",
                    arguments = listOf(
                        navArgument("direction") { type = NavType.LongType },
                        navArgument("id") { type = NavType.StringType },
                        navArgument("fromEvent") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    ),
                ) {
                    val direction = it.arguments?.getLong("direction")
                    val id = it.arguments?.getString("id")
                    val paymentId = if (id != null && direction != null) WalletPaymentId.create(direction, id) else null
                    if (paymentId != null) {
                        PaymentDetailsView(
                            paymentId = paymentId,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            fromEvent = it.arguments?.getBoolean("fromEvent") ?: false
                        )
                    }
                }
                composable(Screen.PaymentsHistory.route) {
                    PaymentsHistoryView(
                        onBackClick = { popToHome(navController) },
                        paymentsViewModel = paymentsViewModel,
                        onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                        onCsvExportClick = { navController.navigate(Screen.PaymentsCsvExport) },
                    )
                }
                composable(Screen.PaymentsCsvExport.route) {
                    CsvExportView(onBackClick = { navController.navigate(Screen.PaymentsHistory.route) })
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
                composable(Screen.TorConfig.route) {
                    TorConfigView()
                }
                composable(Screen.Channels.route) {
                    ChannelsView()
                }
                composable(Screen.MutualClose.route) {
                    MutualCloseView(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.ForceClose.route) {
                    ForceCloseView(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.Preferences.route) {
                    DisplayPrefsView()
                }
                composable(Screen.About.route) {
                    AboutView()
                }
                composable(Screen.PaymentSettings.route) {
                    PaymentSettingsView(initialShowLnurlAuthSchemeDialog = false)
                }
                composable("${Screen.PaymentSettings.route}/{showAuthSchemeDialog}", arguments = listOf(
                    navArgument("showAuthSchemeDialog") { type = NavType.BoolType }
                )) {
                    val showAuthSchemeDialog = it.arguments?.getBoolean("showAuthSchemeDialog") ?: false
                    PaymentSettingsView(showAuthSchemeDialog)
                }
                composable(Screen.AppLock.route) {
                    AppLockView()
                }
                composable(Screen.Logs.route) {
                    LogsView()
                }
                composable(Screen.SwitchToLegacy.route) {
                    LegacySwitcherView(onProceedNormally = { navController.navigate(Screen.Startup.route) })
                }
            }
        }
    }

    val lastCompletedPayment = business.paymentsManager.lastCompletedPayment.collectAsState().value

    if (lastCompletedPayment != null) {
        log.debug { "completed payment=${lastCompletedPayment}" }
        LaunchedEffect(key1 = lastCompletedPayment.walletPaymentId()) {
            navigateToPaymentDetails(navController, id = lastCompletedPayment.walletPaymentId(), isFromEvent = true)
        }
    }
}

/** Navigates to Home and pops everything from the backstack up to Home. This effectively resets the nav stack. */
private fun popToHome(navController: NavHostController) {
    navController.navigate(Screen.Home.route) {
        popUpTo(Screen.Home.route) { inclusive = true }
    }
}

private fun navigateToPaymentDetails(navController: NavController, id: WalletPaymentId, isFromEvent: Boolean) {
    navController.navigate("${Screen.PaymentDetails.route}?direction=${id.dbType.value}&id=${id.dbId}&fromEvent=${isFromEvent}")
}

@Composable
private fun RequireStarted(
    walletState: WalletState?,
    nextUri: String? = null,
    children: @Composable () -> Unit
) {
    val log = logger("RequireStarted")
    if (walletState !is WalletState.Started) {
        navController.navigate("${Screen.Startup.route}?next=$nextUri")
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