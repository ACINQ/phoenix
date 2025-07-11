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

package fr.acinq.phoenix.android

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.auth.screenlock.ScreenLockPrompt
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.buttons.openLink
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.navigation.baseNavGraph
import fr.acinq.phoenix.android.navigation.baseSettingsNavGraph
import fr.acinq.phoenix.android.navigation.channelsNavGraph
import fr.acinq.phoenix.android.navigation.homeNavGraph
import fr.acinq.phoenix.android.navigation.miscSettingsNavGraph
import fr.acinq.phoenix.android.navigation.navigateToPaymentDetails
import fr.acinq.phoenix.android.navigation.paymentsNavGraph
import fr.acinq.phoenix.android.navigation.walletInfoNavGraph
import fr.acinq.phoenix.android.startup.StartupViewModel
import fr.acinq.phoenix.android.utils.appBackground
import fr.acinq.phoenix.android.utils.datastore.PreferredBitcoinUnits
import fr.acinq.phoenix.android.utils.extensions.findActivitySafe
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.managers.AppConfigurationManager
import kotlinx.coroutines.flow.first

@Composable
fun AppRoot(
    navController: NavHostController,
    appViewModel: AppViewModel,
) {
    val log = logger("AppRoot")
    log.debug("entering app root")

    val activeBusiness by BusinessRepo.activeBusiness.collectAsState(null)
    val business = activeBusiness?.second
    val fiatRatesMap by BusinessRepo.fiatRatesMap.collectAsState()

    val isAmountInFiat = userPrefs.getIsAmountInFiat.collectAsState(false)
    val bitcoinUnits = userPrefs.getBitcoinUnits.collectAsState(initial = PreferredBitcoinUnits(primary = BitcoinUnit.Sat))
    val fiatCurrencies = userPrefs.getFiatCurrencies.collectAsState(initial = AppConfigurationManager.PreferredFiatCurrencies(primary = FiatCurrency.USD, others = emptyList()))

    val paymentsViewModel = business?.let {
        viewModel<PaymentsViewModel>(factory = PaymentsViewModel.Factory(it.paymentsManager))
    }

    val noticesViewModel = business?.let {
        viewModel<NoticesViewModel>(
            factory = NoticesViewModel.Factory(
                appConfigurationManager = it.appConfigurationManager,
                peerManager = it.peerManager,
                connectionsManager = it.connectionsManager,
            )
        )
    }?.also { monitorPermission(it) }

    val startupViewModel = viewModel<StartupViewModel>(factory = StartupViewModel.Factory(application))

    CompositionLocalProvider(
        LocalBusiness provides business,
        LocalControllerFactory provides business?.controllers,
        LocalNavController provides navController,
        LocalExchangeRatesMap provides fiatRatesMap,
        LocalBitcoinUnits provides bitcoinUnits.value,
        LocalFiatCurrencies provides fiatCurrencies.value,
        LocalShowInFiat provides isAmountInFiat.value,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .background(appBackground())
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "${Screen.Startup.route}?next={next}",
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    route = "main" // works like an id, can be used to scope view models with `navController.getBackStackEntry("main")`
                ) {
                    baseNavGraph(navController, startupViewModel)
                    baseSettingsNavGraph(navController, appViewModel, noticesViewModel, business)

                    if (paymentsViewModel != null && noticesViewModel != null) {
                        homeNavGraph(navController, paymentsViewModel, noticesViewModel)
                        miscSettingsNavGraph(navController, paymentsViewModel, noticesViewModel)
                    }

                    paymentsNavGraph(navController)
                    walletInfoNavGraph(navController)
                    channelsNavGraph(navController)
                }
            }

            val context = LocalContext.current
            val isScreenLocked by appViewModel.isScreenLocked
            val isBiometricLockEnabled by userPrefs.getIsScreenLockBiometricsEnabled.collectAsState(initial = null)
            val isCustomPinLockEnabled by userPrefs.getIsScreenLockPinEnabled.collectAsState(initial = null)

            if ((isBiometricLockEnabled == true || isCustomPinLockEnabled == true) && isScreenLocked) {
                BackHandler {
                    // back button minimises the app
                    context.findActivitySafe()?.moveTaskToBack(false)
                }
                ScreenLockPrompt(
                    promptScreenLockImmediately = appViewModel.promptScreenLockImmediately.value,
                    onLock = { appViewModel.lockScreen() },
                    onUnlock = {
                        appViewModel.unlockScreen()
                        appViewModel.promptScreenLockImmediately.value = false
                    },
                )
            }

            val lastCompletedPayment = business?.paymentsManager?.lastCompletedPayment?.collectAsState()
            lastCompletedPayment?.value?.let { payment ->
                LaunchedEffect(key1 = payment.id) {
                    navigateToPaymentDetails(navController, id = payment.id, isFromEvent = true)
                }
            }

            val isUpgradeRequired = business?.peerManager?.upgradeRequired?.collectAsState(false)
            if (isUpgradeRequired?.value == true) {
                UpgradeRequiredBlockingDialog()
            }
        }
    }
}

@SuppressLint("ComposableNaming")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun monitorPermission(noticesViewModel: NoticesViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val userPrefs = userPrefs
        val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        if (!notificationPermission.status.isGranted) {
            LaunchedEffect(Unit) {
                if (userPrefs.getShowNotificationPermissionReminder.first()) {
                    noticesViewModel.addNotice(Notice.NotificationPermission)
                }
            }
        } else {
            noticesViewModel.removeNotice<Notice.NotificationPermission>()
        }
        LaunchedEffect(Unit) {
            userPrefs.getShowNotificationPermissionReminder.collect {
                if (it && !notificationPermission.status.isGranted) {
                    noticesViewModel.addNotice(Notice.NotificationPermission)
                } else {
                    noticesViewModel.removeNotice<Notice.NotificationPermission>()
                }
            }
        }
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