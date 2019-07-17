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
import androidx.core.content.edit
import fr.acinq.eclair.BtcUnit
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.SatUnit
import fr.acinq.eclair.`CoinUtils$`

object Prefs {
  private val PREFS_IS_PIN_SET: String = "PREFS_IS_PIN_SET"
  private val PREFS_IS_FIRST_TIME: String = "PREFS_IS_FIRST_TIME"
  private val PREFS_COIN_UNIT: String = "PREFS_COIN_UNIT"

  fun isFirstTime(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_FIRST_TIME, true)
  }

  fun setHasStartedOnce(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_FIRST_TIME, false).apply()
  }

  fun prefCoin(context: Context): CoinUnit {
    return `CoinUtils$`.`MODULE$`.getUnitFromString(PreferenceManager.getDefaultSharedPreferences(context)
      .getString(PREFS_COIN_UNIT, SatUnit.code()))
  }

  fun isPinSet(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_PIN_SET, false)
  }

  fun pinIsNowSet(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_PIN_SET, true).apply()
  }
}
