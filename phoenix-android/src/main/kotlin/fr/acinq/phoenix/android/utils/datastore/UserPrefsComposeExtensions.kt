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

package fr.acinq.phoenix.android.utils.datastore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.managers.AppConfigurationManager
import kotlinx.coroutines.flow.flowOf

@Composable
fun UserPrefs?.getHomeAmountDisplayMode() : State<HomeAmountDisplayMode> =
    (this?.getHomeAmountDisplayMode ?: flowOf(HomeAmountDisplayMode.REDACTED)).collectAsState(initial = HomeAmountDisplayMode.REDACTED)

@Composable
fun UserPrefs?.getIsAmountInFiat(): State<Boolean> =
    (this?.getIsAmountInFiat ?: flowOf(false)).collectAsState(initial = false)

@Composable
fun UserPrefs?.getBitcoinUnits(): State<PreferredBitcoinUnits> =
    (this?.getBitcoinUnits ?: flowOf(PreferredBitcoinUnits(primary = BitcoinUnit.Sat))).collectAsState(initial = PreferredBitcoinUnits(primary = BitcoinUnit.Sat))

@Composable
fun UserPrefs?.getFiatCurrencies(): State<AppConfigurationManager.PreferredFiatCurrencies> =
    (this?.getFiatCurrencies ?: flowOf(AppConfigurationManager.PreferredFiatCurrencies(primary = FiatCurrency.USD, others = emptyList()))).collectAsState(initial = AppConfigurationManager.PreferredFiatCurrencies(primary = FiatCurrency.USD, others = emptyList()))
