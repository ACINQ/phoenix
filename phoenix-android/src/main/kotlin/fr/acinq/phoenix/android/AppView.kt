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

import android.Manifest
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.home.HomeView
import fr.acinq.phoenix.android.init.CreateWalletView
import fr.acinq.phoenix.android.init.InitWallet
import fr.acinq.phoenix.android.init.RestoreWalletView
import fr.acinq.phoenix.android.intro.IntroView
import fr.acinq.phoenix.android.payments.ReceiveView
import fr.acinq.phoenix.android.payments.ScanDataView
import fr.acinq.phoenix.android.payments.details.PaymentDetailsView
import fr.acinq.phoenix.android.payments.history.CsvExportView
import fr.acinq.phoenix.android.payments.history.PaymentsHistoryView
import fr.acinq.phoenix.android.service.NodeServiceState
import fr.acinq.phoenix.android.settings.*
import fr.acinq.phoenix.android.settings.channels.ChannelDetailsView
import fr.acinq.phoenix.android.settings.channels.ChannelsView
import fr.acinq.phoenix.android.settings.channels.ImportChannelsData
import fr.acinq.phoenix.android.settings.displayseed.DisplaySeedView
import fr.acinq.phoenix.android.settings.fees.AdvancedIncomingFeePolicy
import fr.acinq.phoenix.android.settings.fees.LiquidityPolicyView
import fr.acinq.phoenix.android.settings.walletinfo.FinalWalletInfo
import fr.acinq.phoenix.android.settings.walletinfo.SwapInWalletInfo
import fr.acinq.phoenix.android.settings.walletinfo.WalletInfoView
import fr.acinq.phoenix.android.startup.LegacySwitcherView
import fr.acinq.phoenix.android.startup.StartupView
import fr.acinq.phoenix.android.utils.appBackground
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.utils.extensions.id
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.kodein.memory.util.currentTimestampMillis


@Composable
fun AppView(
    appVM: AppViewModel,
    navController: NavHostController,
) {
    val log = logger("Navigation")
    log.debug { "init app view composition" }

    val fiatRates = application.business.currencyManager.ratesFlow.collectAsState(listOf())
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
        LocalElectrumServer provides electrumServer.value,
    ) {

        // we keep a view model storing payments so that we don't have to fetch them every time
        val paymentsViewModel: PaymentsViewModel = viewModel(
            factory = PaymentsViewModel.Factory(
                connectionsFlow = business.connectionsManager.connections,
                paymentsManager = business.paymentsManager,
            )
        )

        val noticesViewModel = viewModel<NoticesViewModel>(factory = NoticesViewModel.Factory(
            appConfigurationManager = business.appConfigurationManager,
            peerManager = business.peerManager
        ))
        MonitorNotices(vm = noticesViewModel)

        val legacyAppStatus = LegacyPrefsDatastore.getLegacyAppStatus(context).collectAsState(null)
        if (legacyAppStatus.value is LegacyAppStatus.Required && navController.currentDestination?.route != Screen.SwitchToLegacy.route) {
            navController.navigate(Screen.SwitchToLegacy.route)
        }

        val walletState by appVM.serviceState.observeAsState(null)

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
                        navArgument("next") { type = NavType.StringType; nullable = true }
                    ),
                ) {
                    val nextScreenLink = it.arguments?.getString("next")
                    log.debug { "navigating to startup with next=$nextScreenLink" }
                    StartupView(
                        appVM = appVM,
                        onShowIntro = { navController.navigate(Screen.Intro.route) },
                        onKeyAbsent = { navController.navigate(Screen.InitWallet.route) },
                        onBusinessStarted = {
                            val next = nextScreenLink?.takeUnless { it.isBlank() }?.let { Uri.parse(it) }
                            if (next == null || !navController.graph.hasDeepLink(next)) {
                                popToHome(navController)
                            } else {
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
                    RequireStarted(walletState) {
                        HomeView(
                            paymentsViewModel = paymentsViewModel,
                            noticesViewModel = noticesViewModel,
                            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                            onSettingsClick = { navController.navigate(Screen.Settings.route) },
                            onReceiveClick = { navController.navigate(Screen.Receive.route) },
                            onSendClick = { navController.navigate(Screen.ScanData.route) { launchSingleTop = true } },
                            onPaymentsHistoryClick = { navController.navigate(Screen.PaymentsHistory.route) },
                            onTorClick = { navController.navigate(Screen.TorConfig) },
                            onElectrumClick = { navController.navigate(Screen.ElectrumServer) },
                            onShowSwapInWallet = { navController.navigate(Screen.WalletInfo.SwapInWallet) },
                            onShowChannels = { navController.navigate(Screen.Channels) },
                            onShowNotifications = { navController.navigate(Screen.Notifications) }
                        )
                    }
                }
                composable(Screen.Receive.route) {
                    ReceiveView(
                        onSwapInReceived = { popToHome(navController) },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable(
                    Screen.ScanData.route, deepLinks = listOf(
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
                ) {
                    val input = it.arguments?.getString("data")
                    RequireStarted(walletState, nextUri = "scanview:$input") {
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
                    deepLinks = listOf(navDeepLink { uriPattern = "phoenix:payments/{direction}/{id}" })
                ) {
                    val direction = it.arguments?.getLong("direction")
                    val id = it.arguments?.getString("id")

                    val paymentId = if (id != null && direction != null) WalletPaymentId.create(direction, id) else null
                    if (paymentId != null) {
                        RequireStarted(walletState, nextUri = "phoenix:payments/${direction}/${id}") {
                            log.debug { "navigating to payment-details id=$id" }
                            PaymentDetailsView(
                                paymentId = paymentId,
                                onBackClick = {
                                    if (!navController.popBackStack()) {
                                        popToHome(navController)
                                    }
                                },
                                fromEvent = it.arguments?.getBoolean("fromEvent") ?: false
                            )
                        }
                    }
                }
                composable(Screen.PaymentsHistory.route) {
                    PaymentsHistoryView(
                        onBackClick = { navController.popBackStack() },
                        paymentsViewModel = paymentsViewModel,
                        onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                        onCsvExportClick = { navController.navigate(Screen.PaymentsCsvExport) },
                    )
                }
                composable(Screen.PaymentsCsvExport.route) {
                    CsvExportView(onBackClick = {
                        navController.navigate(Screen.PaymentsHistory.route) {
                            popUpTo(Screen.PaymentsHistory.route) { inclusive = true }
                        }
                    })
                }
                composable(Screen.Settings.route) {
                    SettingsView(noticesViewModel)
                }
                composable(Screen.DisplaySeed.route) {
                    DisplaySeedView()
                }
                composable(Screen.ElectrumServer.route) {
                    ElectrumView()
                }
                composable(Screen.TorConfig.route) {
                    TorConfigView()
                }
                composable(Screen.Channels.route) {
                    ChannelsView(
                        onBackClick = {
                            navController.navigate(Screen.Settings) {
                                popUpTo(Screen.Settings.route) { inclusive = true }
                            }
                        },
                        onChannelClick = { navController.navigate("${Screen.ChannelDetails.route}?id=$it") },
                        onImportChannelsDataClick = { navController.navigate(Screen.ImportChannelsData)}
                    )
                }
                composable(
                    route = "${Screen.ChannelDetails.route}?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) {
                    val channelId = it.arguments?.getString("id")
                    ChannelDetailsView(onBackClick = { navController.popBackStack() }, channelId = channelId)
                }
                composable(Screen.ImportChannelsData.route) {
                    ImportChannelsData(onBackClick = { navController.popBackStack() })
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
                    PaymentSettingsView(
                        initialShowLnurlAuthSchemeDialog = false,
                    )
                }
                composable("${Screen.PaymentSettings.route}/{showAuthSchemeDialog}", arguments = listOf(
                    navArgument("showAuthSchemeDialog") { type = NavType.BoolType }
                )) {
                    val showAuthSchemeDialog = it.arguments?.getBoolean("showAuthSchemeDialog") ?: false
                    PaymentSettingsView(
                        initialShowLnurlAuthSchemeDialog = showAuthSchemeDialog,
                    )
                }
                composable(Screen.AppLock.route) {
                    AppLockView(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.Logs.route) {
                    LogsView()
                }
                composable(Screen.SwitchToLegacy.route) {
                    LegacySwitcherView(onProceedNormally = { navController.navigate(Screen.Startup.route) })
                }
                composable(Screen.WalletInfo.route) {
                    WalletInfoView(
                        onBackClick = { navController.popBackStack() },
                        onLightningWalletClick = { navController.navigate(Screen.Channels.route) },
                        onSwapInWalletClick = { navController.navigate(Screen.WalletInfo.SwapInWallet.route) },
                        onFinalWalletClick = { navController.navigate(Screen.WalletInfo.FinalWallet.route) },
                    )
                }
                composable(
                    Screen.WalletInfo.SwapInWallet.route,
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "phoenix:swapinwallet" }
                    )
                ) {
                    SwapInWalletInfo(
                        onBackClick = { navController.popBackStack() },
                        onViewChannelPolicyClick = { navController.navigate(Screen.LiquidityPolicy.route) },
                    )
                }
                composable(Screen.WalletInfo.FinalWallet.route) {
                    FinalWalletInfo(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.LiquidityPolicy.route, deepLinks = listOf(navDeepLink { uriPattern ="phoenix:liquiditypolicy" })) {
                    LiquidityPolicyView(
                        onBackClick = { navController.popBackStack() },
                        onAdvancedClick = { navController.navigate(Screen.AdvancedLiquidityPolicy.route) }
                    )
                }
                composable(Screen.AdvancedLiquidityPolicy.route) {
                    AdvancedIncomingFeePolicy(onBackClick = { navController.popBackStack() })
                }
                composable(Screen.Notifications.route) {
                    NotificationsView(
                        noticesViewModel = noticesViewModel,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(Screen.ResetWallet.route) {
                    ResetWallet(onBackClick = { navController.popBackStack() })
                }
            }
        }
    }

    val isDataMigrationExpected by LegacyPrefsDatastore.getDataMigrationExpected(context).collectAsState(initial = null)
    val lastCompletedPayment by business.paymentsManager.lastCompletedPayment.collectAsState()
    lastCompletedPayment?.let {
        log.debug { "completed payment=${lastCompletedPayment?.id()} with data-migration=$isDataMigrationExpected" }
        LaunchedEffect(key1 = it.walletPaymentId()) {
            if (isDataMigrationExpected == false) {
                navigateToPaymentDetails(navController, id = it.walletPaymentId(), isFromEvent = true)
            }
        }
    }

    val isUpgradeRequired by business.peerManager.upgradeRequired.collectAsState(false)
    if (isUpgradeRequired) {
        UpgradeRequiredBlockingDialog()
    }
}

/** Navigates to Home and pops everything from the backstack up to Home. This effectively resets the nav stack. */
private fun popToHome(navController: NavHostController) {
    navController.navigate(Screen.Home.route) {
        popUpTo(navController.graph.id) { inclusive = true }
    }
}

fun navigateToPaymentDetails(navController: NavController, id: WalletPaymentId, isFromEvent: Boolean) {
    navController.navigate("${Screen.PaymentDetails.route}?direction=${id.dbType.value}&id=${id.dbId}&fromEvent=${isFromEvent}")
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MonitorNotices(
    vm: NoticesViewModel
) {
    val context = LocalContext.current
    val internalData = application.internalDataRepository

    LaunchedEffect(Unit) {
        internalData.showSeedBackupNotice.collect {
            if (it) {
                vm.addNotice(Notice.BackupSeedReminder)
            } else {
                vm.removeNotice<Notice.BackupSeedReminder>()
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        if (!notificationPermission.status.isGranted) {
            LaunchedEffect(Unit) {
                if (UserPrefs.getShowNotificationPermissionReminder(context).first()) {
                    vm.addNotice(Notice.NotificationPermission)
                }
            }
        } else {
            vm.removeNotice<Notice.NotificationPermission>()
        }
        LaunchedEffect(Unit) {
            UserPrefs.getShowNotificationPermissionReminder(context).collect {
                if (it && !notificationPermission.status.isGranted) {
                    vm.addNotice(Notice.NotificationPermission)
                } else {
                    vm.removeNotice<Notice.NotificationPermission>()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        internalData.getChannelsWatcherOutcome.filterNotNull().collect {
            if (currentTimestampMillis() - it.timestamp > 6 * DateUtils.DAY_IN_MILLIS) {
                vm.addNotice(Notice.WatchTowerLate)
            } else {
                vm.removeNotice<Notice.WatchTowerLate>()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LegacyPrefsDatastore.hasMigratedFromLegacy(context).first()) {
            internalData.getLegacyMigrationMessageShown.collect { shown ->
                if (!shown) {
                    vm.addNotice(Notice.MigrationFromLegacy)
                } else {
                    vm.removeNotice<Notice.MigrationFromLegacy>()
                }
            }
        }
    }
}

@Composable
private fun RequireStarted(
    serviceState: NodeServiceState?,
    nextUri: String? = null,
    children: @Composable () -> Unit
) {
    val log = logger("Navigation")
    if (serviceState == null) {
        // do nothing
    } else if (serviceState !is NodeServiceState.Running) {
        log.debug { "access to screen has been denied (state=${serviceState.name})" }
        val nc = navController
        nc.navigate("${Screen.Startup.route}?next=$nextUri") {
            popUpTo(nc.graph.id) { inclusive = true }
        }
    } else {
        children()
    }
}

@Composable
private fun UpgradeRequiredBlockingDialog() {
    val context = LocalContext.current
    Dialog(
        onDismiss = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        title = stringResource(id = R.string.upgraderequired_title),
        buttons = null
    ) {
        Text(
            text = stringResource(id = R.string.upgraderequired_message, BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            text = stringResource(id = R.string.upgraderequired_button),
            icon = R.drawable.ic_external_link,
            space = 8.dp,
            shape = RoundedCornerShape(12.dp),
            onClick = { openLink(context = context, link = "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") },
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}