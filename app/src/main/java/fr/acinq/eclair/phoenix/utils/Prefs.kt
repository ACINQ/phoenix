/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.phoenix.utils

import android.content.Context
import android.preference.PreferenceManager
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.MSatUnit
import fr.acinq.eclair.SatUnit
import fr.acinq.eclair.`CoinUtils$`

object Prefs {
  private const val PREFS_IS_SEED_ENCRYPTED: String = "PREFS_IS_SEED_ENCRYPTED"
  private const val PREFS_MNEMONICS_SEEN_TIMESTAMP: String = "PREFS_MNEMONICS_SEEN_TIMESTAMP"
  private const val PREFS_IS_FIRST_TIME: String = "PREFS_IS_FIRST_TIME"
  private const val PREFS_COIN_UNIT: String = "PREFS_COIN_UNIT"
  private const val PREFS_EXCHANGE_RATE_TIMESTAMP: String = "PREFS_EXCHANGE_RATES_TIMESTAMP"
  private const val PREFS_EXCHANGE_RATE_PREFIX: String = "PREFS_EXCHANGE_RATE_"

  fun isFirstTime(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_FIRST_TIME, true)
  }

  fun setHasStartedOnce(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_FIRST_TIME, false).apply()
  }

  fun getCoin(context: Context): CoinUnit {
    return `CoinUtils$`.`MODULE$`.getUnitFromString(PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_COIN_UNIT, SatUnit.code()))
  }

  fun isSeedEncrypted(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_SEED_ENCRYPTED, false)
  }

  fun setIsSeedEncrypted(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_SEED_ENCRYPTED, true).apply()
  }

  fun getMnemonicsSeenTimestamp(context: Context): Long {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(PREFS_MNEMONICS_SEEN_TIMESTAMP, 0)
  }

  fun setMnemonicsSeenTimestamp(context: Context, timestamp: Long) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREFS_MNEMONICS_SEEN_TIMESTAMP, timestamp).apply()
  }

  fun getExchangeRate(context: Context, code: String): Float {
    return PreferenceManager.getDefaultSharedPreferences(context).getFloat(PREFS_EXCHANGE_RATE_PREFIX + code.toUpperCase(), -1.0f)
  }

  fun setExhangeRate(context: Context, code: String, rate: Float) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putFloat(PREFS_EXCHANGE_RATE_PREFIX + code, rate).apply()
  }

  fun getExchangeRateTimestamp(context: Context): Long {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(PREFS_EXCHANGE_RATE_TIMESTAMP, 0)
  }

  fun setExchangeRateTimestamp(context: Context, timestamp: Long) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREFS_EXCHANGE_RATE_TIMESTAMP, timestamp).apply()
  }
}
