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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.data.*


typealias CF = ControllerFactory

val LocalTheme = staticCompositionLocalOf { UserTheme.SYSTEM }
val LocalBusiness = staticCompositionLocalOf<PhoenixBusiness?> { null }
val LocalControllerFactory = staticCompositionLocalOf<ControllerFactory?> { null }
val LocalNavController = staticCompositionLocalOf<NavHostController?> { null }
val LocalBitcoinUnit = compositionLocalOf { BitcoinUnit.Sat }
val LocalFiatCurrency = compositionLocalOf { FiatCurrency.USD }
val LocalExchangeRates = compositionLocalOf<List<ExchangeRate>> { listOf() }
val LocalShowInFiat = compositionLocalOf { false }
val LocalWalletContext = compositionLocalOf <WalletContext.V0.ChainContext?> { null }
val LocalElectrumServer = compositionLocalOf<ServerAddress?> { null }
val isDarkTheme: Boolean
    @Composable
    get() = LocalTheme.current.let { it == UserTheme.DARK || (it == UserTheme.SYSTEM && isSystemInDarkTheme()) }

val navController: NavHostController
    @Composable
    get() = LocalNavController.current ?: error("navigation controller is not available")

val preferredAmountUnit: CurrencyUnit
    @Composable
    get() = if (LocalShowInFiat.current) LocalFiatCurrency.current else LocalBitcoinUnit.current

val fiatRate: ExchangeRate.BitcoinPriceRate?
    @Composable
    get() = LocalFiatCurrency.current.let { prefFiat ->
        return when (val rate = LocalExchangeRates.current.find { it.fiatCurrency == prefFiat }) {
            is ExchangeRate.BitcoinPriceRate -> rate
            is ExchangeRate.UsdPriceRate -> {
                (LocalExchangeRates.current.find { it.fiatCurrency == FiatCurrency.USD } as? ExchangeRate.BitcoinPriceRate)?.let { usdRate ->
                    // create a BTC/Fiat price rate using the USD/BTC rate and the Fiat/USD rate.
                    ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = rate.fiatCurrency,
                        price = rate.price * usdRate.price,
                        source = rate.source,
                        timestampMillis = rate.timestampMillis
                    )
                }
            }
             else -> null
        }
    }

val controllerFactory: ControllerFactory
    @Composable
    get() = LocalControllerFactory.current ?: error("No controller factory set. Please use appView or mockView.")

val business: PhoenixBusiness
    @Composable
    get() = LocalBusiness.current ?: error("business is not available")

val application: PhoenixApplication
    @Composable
    get() = LocalContext.current.applicationContext as? PhoenixApplication ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")
