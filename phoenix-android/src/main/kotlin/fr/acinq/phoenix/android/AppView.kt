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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.openLink
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.navigation.baseNavGraph
import fr.acinq.phoenix.android.navigation.channelsNavGraph
import fr.acinq.phoenix.android.navigation.homeNavGraph
import fr.acinq.phoenix.android.navigation.navigateToPaymentDetails
import fr.acinq.phoenix.android.navigation.paymentsNavGraph
import fr.acinq.phoenix.android.navigation.settingsNavGraph
import fr.acinq.phoenix.android.navigation.walletInfoNavGraph
import fr.acinq.phoenix.android.utils.appBackground
import fr.acinq.phoenix.android.utils.datastore.getBitcoinUnits
import fr.acinq.phoenix.android.utils.datastore.getFiatCurrencies
import fr.acinq.phoenix.android.utils.datastore.getIsAmountInFiat
import fr.acinq.phoenix.android.utils.logger
import kotlin.time.Duration.Companion.seconds

@Composable
fun AppRoot(
    navController: NavHostController,
    appViewModel: AppViewModel,
) {
    val log = logger("AppRoot")

    val activeWallet by appViewModel.activeWalletInUI.collectAsState(null)
    log.debug("entering app root with active_wallet={}", activeWallet)
    val activeWalletId = activeWallet?.id
    val business = activeWallet?.business
    val activeUserPrefs = activeWallet?.userPrefs
    val activeInternalPrefs = activeWallet?.internalPrefs
    val fiatRatesMap by appViewModel.fiatRatesMap.collectAsState()

    val isAmountInFiat = activeUserPrefs.getIsAmountInFiat()
    val bitcoinUnits = activeUserPrefs.getBitcoinUnits()
    val fiatCurrencies = activeUserPrefs.getFiatCurrencies()

    CompositionLocalProvider(
        LocalBusiness provides business,
        LocalWalletId provides activeWalletId,
        LocalUserPrefs provides activeUserPrefs,
        LocalInternalPrefs provides activeInternalPrefs,
        LocalControllerFactory provides business?.controllers,
        LocalNavController provides navController,
        LocalExchangeRatesMap provides fiatRatesMap,
        LocalBitcoinUnits provides bitcoinUnits.value,
        LocalFiatCurrencies provides fiatCurrencies.value,
        LocalShowInFiat provides isAmountInFiat.value,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(appBackground())
                    .fillMaxSize()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "${Screen.Startup.route}?next={next}",
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                ) {
                    baseNavGraph(navController, appViewModel)
                    // nav graphs below depends on the business, and will redirect to /startup if no wallet is active
                    settingsNavGraph(navController, appViewModel, business)
                    homeNavGraph(navController, appViewModel)
                    paymentsNavGraph(navController, appViewModel)
                    walletInfoNavGraph(navController, appViewModel)
                    channelsNavGraph(navController, appViewModel)
                }
            }

            val lastCompletedPayment = business?.paymentsManager?.lastCompletedPayment?.collectAsState()
            lastCompletedPayment?.value?.let { payment ->
                LaunchedEffect(key1 = payment.id) {
                    val completedAt = payment.completedAt
                    if (navController.currentDestination?.route == Screen.Home.route && (completedAt != null && (currentTimestampMillis() - completedAt) < 5.seconds.inWholeMilliseconds) ) {
                        navigateToPaymentDetails(navController, id = payment.id, isFromEvent = true)
                    }
                }
            }

            val isUpgradeRequired = business?.peerManager?.upgradeRequired?.collectAsState(false)
            if (isUpgradeRequired?.value == true) {
                UpgradeRequiredBlockingDialog()
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