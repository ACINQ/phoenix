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

package fr.acinq.phoenix.legacy.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.preference.PreferenceManager
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.eclair.*
import fr.acinq.phoenix.legacy.TrampolineFeeSetting
import org.json.JSONObject
import org.slf4j.LoggerFactory

object Prefs {
  val log = LoggerFactory.getLogger(this::class.java)

  private const val PREFS_LAST_VERSION_USED: String = "PREFS_LAST_VERSION_USED"
  const val PREFS_MNEMONICS_SEEN_TIMESTAMP: String = "PREFS_MNEMONICS_SEEN_TIMESTAMP"
  private const val PREFS_SHOW_FTUE: String = "PREFS_SHOW_FTUE"

  // -- unit, fiat, conversion...
  const val PREFS_SHOW_AMOUNT_IN_FIAT: String = "PREFS_SHOW_AMOUNT_IN_FIAT"
  private const val PREFS_FIAT_CURRENCY: String = "PREFS_FIAT_CURRENCY"
  const val PREFS_COIN_UNIT: String = "PREFS_COIN_UNIT"
  private const val PREFS_EXCHANGE_RATE_TIMESTAMP: String = "PREFS_EXCHANGE_RATES_TIMESTAMP"
  private const val PREFS_EXCHANGE_RATE_PREFIX: String = "PREFS_EXCHANGE_RATE_"
  const val PREFS_EXPLORER: String = "PREFS_EXPLORER"
  private const val PREFS_LAST_KNOWN_WALLET_CONTEXT: String = "PREFS_LAST_KNOWN_WALLET_CONTEXT"

  // -- authentication with PIN/biometrics
  const val PREFS_SCREEN_LOCK: String = "PREFS_SCREEN_LOCK_ENABLED"

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  private const val PREFS_IS_SEED_ENCRYPTED: String = "PREFS_IS_SEED_ENCRYPTED"

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  private const val PREFS_USE_BIOMETRICS: String = "PREFS_USE_BIOMETRICS"

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  private const val PREFS_ENCRYPTED_PIN: String = "PREFS_ENCRYPTED_PIN"

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  private const val PREFS_ENCRYPTED_PIN_IV: String = "PREFS_ENCRYPTED_PIN_IV"

  // -- background channels watcher
  private const val PREFS_WATCHER_LAST_ATTEMPT_OUTCOME: String = "PREFS_WATCHER_LAST_ATTEMPT_OUTCOME"
  private const val PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP: String = "PREFS_WATCHER_LAST_ATTEMPT_OUTCOME_TIMESTAMP"

  // -- node configuration
  const val PREFS_ELECTRUM_ADDRESS = "PREFS_ELECTRUM_ADDRESS"
  const val PREFS_ELECTRUM_FORCE_SSL = "PREFS_ELECTRUM_FORCE_SSL"
  const val PREFS_TRAMPOLINE_MAX_FEE_INDEX = "PREFS_TRAMPOLINE_MAX_FEE_INDEX"

  // -- payment configuration
  const val PREFS_PAYMENT_DEFAULT_DESCRIPTION = "PREFS_PAYMENT_DEFAULT_DESCRIPTION"
  const val PREFS_PAYMENTS_EXPIRY_SEC = "PREFS_PAYMENTS_EXPIRY_SEC"
  const val PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE = "PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE"
  const val PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE = "PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE"
  const val PREFS_AUTO_PAY_TO_OPEN = "PREFS_AUTO_PAY_TO_OPEN"
  const val PREFS_MISSED_PAY_TO_OPEN_NOTIF_TIMESTAMP = "PREFS_MISSED_PAY_TO_OPEN_NOTIF_TIMESTAMP"

  // -- migration
  const val PREFS_MIGRATED_FROM = "PREFS_MIGRATED_FROM"

  // -- other
  const val PREFS_THEME: String = "PREFS_THEME"
  const val PREFS_TOR_ENABLED: String = "PREFS_TOR_ENABLED"
  const val PREFS_SCRAMBLE_PIN: String = "PREFS_SCRAMBLE_PIN"
  const val PREFS_FCM_TOKEN: String = "PREFS_FCM_TOKEN"
  const val PREFS_SHOW_BALANCE_HOME: String = "PREFS_SHOW_BALANCE_HOME"

  // -- ==================================
  // -- authentication with PIN/biometrics
  // -- ==================================

  fun isScreenLocked(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SCREEN_LOCK, false)

  fun saveScreenLocked(context: Context, isLocked: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_SCREEN_LOCK, isLocked).apply()
  }

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  fun isSeedEncrypted(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_IS_SEED_ENCRYPTED, false)
  }

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  fun setIsSeedEncrypted(context: Context, isEncrypted: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_IS_SEED_ENCRYPTED, isEncrypted).apply()
  }

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  fun useBiometrics(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_USE_BIOMETRICS, false)
  }

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  fun getEncryptedPIN(context: Context): ByteArray? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ENCRYPTED_PIN, null)?.let { Base64.decode(it, Base64.DEFAULT) }
  }

  @Deprecated("only useful for EncryptedSeed.V1 access control system")
  fun getEncryptedPINIV(context: Context): ByteArray? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ENCRYPTED_PIN_IV, null)?.let { Base64.decode(it, Base64.DEFAULT) }
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

  fun isBackupDone(context: Context): Boolean = getMnemonicsSeenTimestamp(context) > 0

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

  fun getShowAmountInFiat(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, false)
  }

  fun setShowAmountInFiat(context: Context, amountInFiat: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, amountInFiat).apply()
  }

  fun getFiatCurrency(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_FIAT_CURRENCY, "USD") ?: "USD"
  }

  fun setFiatCurrency(context: Context, code: String) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_FIAT_CURRENCY, code.toUpperCase()).apply()
  }

  fun getExplorer(context: Context?): String {
    val pref = context?.let { PreferenceManager.getDefaultSharedPreferences(it).getString(PREFS_EXPLORER, null) }
    return when {
      "Blockstream.info".equals(pref, ignoreCase = true) -> Constants.BLOCKSTREAM_EXPLORER_URL
      else -> Constants.MEMPOOLSPACE_EXPLORER_URL
    }
  }

  fun getLastKnownWalletContext(context: Context): JSONObject? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_LAST_KNOWN_WALLET_CONTEXT, null)?.run {
      try {
        JSONObject(this)
      } catch (e: Exception) {
        log.debug("could not read wallet context json from preferences")
        null
      }
    }
  }

  fun saveWalletContext(context: Context, json: JSONObject) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_LAST_KNOWN_WALLET_CONTEXT, json.toString()).apply()
  }

  // -- ==================================
  // -- version, ftue, migration...
  // -- ==================================

  fun getLastVersionUsed(context: Context): Int {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREFS_LAST_VERSION_USED, 0)
  }

  fun setLastVersionUsed(context: Context, version: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREFS_LAST_VERSION_USED, version).apply()
  }

  fun showFTUE(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SHOW_FTUE, true)
  }

  fun setShowFTUE(context: Context, show: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_SHOW_FTUE, show).apply()
  }

  fun getMigratedFrom(context: Context): Int {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREFS_MIGRATED_FROM, 0)
  }

  @SuppressLint("ApplySharedPref")
  fun setMigratedFrom(context: Context, code: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREFS_MIGRATED_FROM, code).commit()
  }

  // -- ==================================
  // -- other...
  // -- ==================================

  fun getTheme(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_THEME, ThemeHelper.default) ?: ThemeHelper.default
  }

  fun showBalanceHome(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SHOW_BALANCE_HOME, true)
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

  fun getForceElectrumSSL(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_ELECTRUM_FORCE_SSL, false)
  }

  fun saveForceElectrumSSL(context: Context, mustCheck: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_ELECTRUM_FORCE_SSL, mustCheck).apply()
  }

  fun getTrampolineMaxFeeIndex(context: Context): Int {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREFS_TRAMPOLINE_MAX_FEE_INDEX, -1)
  }

  fun saveTrampolineMaxFeeIndex(context: Context, index: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREFS_TRAMPOLINE_MAX_FEE_INDEX, index).apply()
  }

  fun isTorEnabled(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_TOR_ENABLED, false)
  }

  fun saveTorEnabled(context: Context, enabled: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_TOR_ENABLED, enabled).apply()
  }

  fun isPinScrambled(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SCRAMBLE_PIN, false)
  }

  fun getFCMToken(context: Context): String? {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_FCM_TOKEN, null)
  }

  fun saveFCMToken(context: Context, token: String) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_FCM_TOKEN, token).apply()
  }

  fun getDefaultPaymentDescription(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_PAYMENT_DEFAULT_DESCRIPTION, null) ?: ""
  }

  fun setDefaultPaymentDescription(context: Context, value: String) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_PAYMENT_DEFAULT_DESCRIPTION, value).apply()
  }

  // default expiry = 1 week
  private val DEFAULT_EXPIRY: Long = 60L * 60 * 24 * 7

  fun getPaymentsExpirySeconds(context: Context): Long {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(PREFS_PAYMENTS_EXPIRY_SEC, DEFAULT_EXPIRY)
  }

  fun setPaymentsExpirySeconds(context: Context, expirySeconds: Long?) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREFS_PAYMENTS_EXPIRY_SEC, expirySeconds ?: DEFAULT_EXPIRY).apply()
  }

  fun getMaxTrampolineCustomFee(context: Context): TrampolineFeeSetting? {
    PreferenceManager.getDefaultSharedPreferences(context).run {
      val baseFee = getLong(PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE, -1)
      val proportionalFee = getLong(PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE, -1)
      return if (baseFee < 0L || proportionalFee < 0L) {
        null
      } else {
        TrampolineFeeSetting(Satoshi(baseFee), proportionalFee, CltvExpiryDelta(576))
      }
    }
  }

  fun setMaxTrampolineCustomFee(context: Context, base: Satoshi, proportional: Long) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit()
      .putLong(PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE, base.toLong())
      .putLong(PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE, proportional)
      .apply()
  }

  fun removeMaxTrampolineCustomFee(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).edit()
      .remove(PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE)
      .remove(PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE)
      .commit()
  }

  fun isAutoPayToOpenEnabled(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_AUTO_PAY_TO_OPEN, true)
  }

  fun setAutoPayToOpen(context: Context, enabled: Boolean) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFS_AUTO_PAY_TO_OPEN, enabled).apply()
  }

  fun getMissedPayToOpenNotifTimestamp(context: Context): Long {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(PREFS_MISSED_PAY_TO_OPEN_NOTIF_TIMESTAMP, 0)
  }

  fun setMissedPayToOpenNotifTimestamp(context: Context, timestamp: Long) {
    return PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREFS_MISSED_PAY_TO_OPEN_NOTIF_TIMESTAMP, timestamp).apply()
  }
}
