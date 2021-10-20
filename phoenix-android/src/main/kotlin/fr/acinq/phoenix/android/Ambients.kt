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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.data.BitcoinPriceRate
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency


typealias CF = ControllerFactory

val LocalBusiness = staticCompositionLocalOf<PhoenixBusiness?> { null }
val LocalControllerFactory = staticCompositionLocalOf<ControllerFactory?> { null }
val LocalNavController = staticCompositionLocalOf<NavHostController?> { null }
val LocalKeyState = staticCompositionLocalOf<KeyState> { KeyState.Unknown }
val LocalBitcoinUnit = compositionLocalOf { BitcoinUnit.Sat }
val LocalFiatCurrency = compositionLocalOf { FiatCurrency.USD }
val LocalFiatRates = compositionLocalOf<List<BitcoinPriceRate>> { listOf() }
val LocalShowInFiat = compositionLocalOf { false }
val LocalElectrumServer = compositionLocalOf<ServerAddress?> { null }

val navController: NavHostController
    @Composable
    get() = LocalNavController.current ?: error("navigation controller is not available")

val keyState
    @Composable
    get() = LocalKeyState.current

val amountUnit: CurrencyUnit
    @Composable
    get() = if (LocalShowInFiat.current) LocalFiatCurrency.current else LocalBitcoinUnit.current

val fiatRate: BitcoinPriceRate?
    @Composable
    get() = LocalFiatCurrency.current.let { prefFiat -> LocalFiatRates.current.find { it.fiatCurrency == prefFiat } }

val controllerFactory: ControllerFactory
    @Composable
    get() = LocalControllerFactory.current ?: error("No controller factory set. Please use appView or mockView.")

val business: PhoenixBusiness
    @Composable
    get() = LocalBusiness.current ?: error("business is not available")

val application: PhoenixApplication
    @Composable
    get() = LocalContext.current.applicationContext as? PhoenixApplication ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")
