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

package fr.acinq.phoenix.utils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Base64
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.SatUnit
import fr.acinq.eclair.WatchListener
import fr.acinq.eclair.`CoinUtils$`
import fr.acinq.phoenix.R

object Prefs {

  private const val PREFS_LAST_VERSION_USED: String = "PREFS_LAST_VERSION_USED"

  private const val PREFS_MNEMONICS_SEEN_TIMESTAMP: String = "PREFS_MNEMONICS_SEEN_TIMESTAMP"
  private const val PREFS_IS_FIRST_TIME: String = "PREFS_IS_FIRST_TIME"

  // -- unit, fiat, conversion...
  const val PREFS_SHOW_AMOUNT_IN_FIAT: String = "PREFS_SHOW_AMOUNT_IN_FIAT"
  private const val PREFS_FIAT_CURRENCY: String = "PREFS_FIAT_CURRENCY"
  const val PREFS_COIN_UNIT: String = "PREFS_COIN_UNIT"
  private const val PREFS_EXCHANGE_RATE_TIMESTAMP: String = "PREFS_EXCHANGE_RATES_TIMESTAMP"
  private const val PREFS_EXCHANGE_RATE_PREFIX: String = "PREFS_EXCHANGE_RATE_"

  // -- authentication with PIN/biometrics
  private const val PREFS_IS_SEED_ENCRYPTED: String = "PREFS_IS_SEED_ENCRYPTED"
  private const val PREFS_ENCRYPTED_PIN: String = "PREFS_ENCRYPTED_PIN"
  private const val PREFS_ENCRYPTED_PIN_IV: String = "PREFS_ENCRYPTED_PIN_IV"
  private const val PREFS_USE_BIOMETRICS: String = "PREFS_USE_BIOMETRICS"

  // -- background channels watcher
  private const val PREFS_WATCHER_LAST_ATTEMPT_OUTCOME: String = "PREFS_WATCHER_LAST_ATTEMPT_OUTCOME"
  private const val PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP: String = "PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP"

  // -- node configuration
  const val PREFS_ELECTRUM_ADDRESS = "PREFS_ELECTRUM_ADDRESS"

  // -- other
  const val PREFS_THEME: String = "PREFS_THEME"

  fun getLastVersionUsed(context: Context): Int {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREFS_LAST_VERSION_USED, 0)
  }

  fun setLastVersionUsed(context: Context, version: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREFS_LAST_VERSION_USED, 0).apply()
  }

  fun isFirstTime(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_FIRST_TIME, true)
  }

  fun setHasStartedOnce(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_FIRST_TIME, false).apply()
  }

  // -- ==================================
  // -- authentication with PIN/biometrics
  // -- ==================================

  fun getIsSeedEncrypted(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_SEED_ENCRYPTED, false)
  }

  fun setIsSeedEncrypted(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_SEED_ENCRYPTED, true).apply()
  }

  fun useBiometrics(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_USE_BIOMETRICS, false)
  }

  fun useBiometrics(context: Context, useBiometrics: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_USE_BIOMETRICS, useBiometrics).apply()
  }

  fun getEncryptedPIN(context: Context): ByteArray? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ENCRYPTED_PIN, null)?.let { Base64.decode(it, Base64.DEFAULT) }
  }

  fun saveEncryptedPIN(context: Context, encryptedPIN: ByteArray) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_ENCRYPTED_PIN, Base64.encodeToString(encryptedPIN, Base64.DEFAULT)).apply()
  }

  fun getEncryptedPINIV(context: Context): ByteArray? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ENCRYPTED_PIN_IV, null)?.let { Base64.decode(it, Base64.DEFAULT) }
  }

  fun saveEncryptedPINIV(context: Context, iv: ByteArray) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_ENCRYPTED_PIN_IV, Base64.encodeToString(iv, Base64.DEFAULT)).apply()
  }

  // -- ==================================
  // -- unit, fiat, conversion...
  // -- ==================================

  fun getCoinUnit(context: Context): CoinUnit {
    return `CoinUtils$`.`MODULE$`.getUnitFromString(PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_COIN_UNIT, SatUnit.code()))
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

  fun setExchangeRate(context: Context, code: String, rate: Float) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putFloat(PREFS_EXCHANGE_RATE_PREFIX + code.toUpperCase(), rate).apply()
  }

  fun getExchangeRateTimestamp(context: Context): Long {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(PREFS_EXCHANGE_RATE_TIMESTAMP, 0)
  }

  fun setExchangeRateTimestamp(context: Context, timestamp: Long) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREFS_EXCHANGE_RATE_TIMESTAMP, timestamp).apply()
  }

  fun getShowAmountInFiat(prefs: SharedPreferences): Boolean {
    return prefs.getBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, false)
  }

  fun getShowAmountInFiat(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, false)
  }

  fun setShowAmountInFiat(context: Context, amountInFiat: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, amountInFiat).apply()
  }

  fun getFiatCurrency(prefs: SharedPreferences): String {
    return prefs.getString(PREFS_FIAT_CURRENCY, "USD") ?: "USD"
  }

  fun getFiatCurrency(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_FIAT_CURRENCY, "USD") ?: "USD"
  }

  fun setFiatCurrency(context: Context, code: String) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_FIAT_CURRENCY, code.toUpperCase()).apply()
  }

  fun getTheme(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_THEME, ThemeHelper.default) ?: ThemeHelper.default
  }

  fun getWatcherLastAttemptOutcome(context: Context): Pair<WatchListener.WatchResult?, Long> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val outcome: WatchListener.WatchResult? = when (prefs.getString(PREFS_WATCHER_LAST_ATTEMPT_OUTCOME, "")) {
      "Ok" -> WatchListener.`Ok$`.`MODULE$`
      "NotOk" -> WatchListener.`NotOk$`.`MODULE$`
      "Unknown" -> WatchListener.`Unknown$`.`MODULE$`
      else -> null
    }
    val timestamp = prefs.getLong(PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP, 0L)
    return Pair(outcome, timestamp)
  }

  fun saveWatcherAttemptOutcome(context: Context, result: WatchListener.WatchResult) {
    PreferenceManager.getDefaultSharedPreferences(context).edit()
      .putLong(PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP, System.currentTimeMillis())
      .putString(PREFS_WATCHER_LAST_ATTEMPT_OUTCOME, result.toString())
      .apply()
  }

  fun getElectrumServer(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ELECTRUM_ADDRESS, "") ?: ""
  }

  fun saveElectrumServer(context: Context, address: String) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_ELECTRUM_ADDRESS, address.trim()).apply()
  }

}
