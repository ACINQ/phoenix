/*
 * Copyright 2022 ACINQ SAS
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

import android.content.Context
import androidx.datastore.preferences.core.*
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.legacy.TrampolineFeeSetting
import fr.acinq.phoenix.legacy.userPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException


object UserPrefs {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun prefs(context: Context): Flow<Preferences> {
        return context.userPrefs.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
    }

    // -- unit, fiat, conversion...

    private val BITCOIN_UNIT = stringPreferencesKey("BITCOIN_UNIT")
    fun getBitcoinUnit(context: Context): Flow<BitcoinUnit> = prefs(context).map { BitcoinUnit.valueOf(it[BITCOIN_UNIT] ?: BitcoinUnit.Sat.name) }
    suspend fun saveBitcoinUnit(context: Context, coinUnit: BitcoinUnit) = context.userPrefs.edit { it[BITCOIN_UNIT] = coinUnit.name }

    private val FIAT_CURRENCY = stringPreferencesKey("FIAT_CURRENCY")
    fun getFiatCurrency(context: Context): Flow<FiatCurrency> = prefs(context).map { FiatCurrency.valueOf(it[FIAT_CURRENCY] ?: FiatCurrency.USD.name) }
    suspend fun saveFiatCurrency(context: Context, currency: FiatCurrency) = context.userPrefs.edit { it[FIAT_CURRENCY] = currency.name }

    private val SHOW_AMOUNT_IN_FIAT = booleanPreferencesKey("SHOW_AMOUNT_IN_FIAT")
    fun getIsAmountInFiat(context: Context): Flow<Boolean> = prefs(context).map { it[SHOW_AMOUNT_IN_FIAT] ?: false }
    suspend fun saveIsAmountInFiat(context: Context, inFiat: Boolean) = context.userPrefs.edit { it[SHOW_AMOUNT_IN_FIAT] = inFiat }

    private val THEME = stringPreferencesKey("THEME")
    fun getUserTheme(context: Context): Flow<UserTheme> = prefs(context).map { UserTheme.safeValueOf(it[THEME]) }
    suspend fun saveUserTheme(context: Context, theme: UserTheme) = context.userPrefs.edit { it[THEME] = theme.name }

    private val HIDE_BALANCE = booleanPreferencesKey("HIDE_BALANCE")
    fun getHideBalance(context: Context): Flow<Boolean> = prefs(context).map { it[HIDE_BALANCE] ?: false }
    suspend fun saveHideBalance(context: Context, hideBalance: Boolean) = context.userPrefs.edit { it[HIDE_BALANCE] = hideBalance }

    // -- electrum

    val PREFS_ELECTRUM_ADDRESS = stringPreferencesKey("PREFS_ELECTRUM_ADDRESS")
    fun getElectrumServer(context: Context): Flow<ServerAddress?> = prefs(context).map {
        it[PREFS_ELECTRUM_ADDRESS]?.takeIf { it.isNotBlank() }?.let { address ->
            log.info("retrieved preferred electrum=$address from datastore")
            if (address.contains(":")) {
                val (host, port) = address.split(":")
                ServerAddress(host, port.toInt(), TcpSocket.TLS.TRUSTED_CERTIFICATES)
            } else ServerAddress(address, 50002, TcpSocket.TLS.TRUSTED_CERTIFICATES)
        }
    }
    suspend fun saveElectrumServer(context: Context, address: String) = context.userPrefs.edit { it[PREFS_ELECTRUM_ADDRESS] = address }
    suspend fun saveElectrumServer(context: Context, address: ServerAddress) = saveElectrumServer(context, "${address.host}:${address.port}")

    // -- security

    val PREFS_SCREEN_LOCK = booleanPreferencesKey("PREFS_SCREEN_LOCK")
    fun getIsScreenLockActive(context: Context): Flow<Boolean> = prefs(context).map { it[PREFS_SCREEN_LOCK] ?: false }
    suspend fun saveIsScreenLockActive(context: Context, isScreenLockActive: Boolean) = context.userPrefs.edit { it[PREFS_SCREEN_LOCK] = isScreenLockActive }

    // -- payments options

    private val INVOICE_DEFAULT_DESC = stringPreferencesKey("INVOICE_DEFAULT_DESC")
    fun getInvoiceDefaultDesc(context: Context): Flow<String> = prefs(context).map { it[INVOICE_DEFAULT_DESC]?.takeIf { it.isNotBlank() } ?: "" }
    suspend fun saveInvoiceDefaultDesc(context: Context, description: String) = context.userPrefs.edit { it[INVOICE_DEFAULT_DESC] = description }

    private val INVOICE_DEFAULT_EXPIRY = longPreferencesKey("INVOICE_DEFAULT_EXPIRY")
    fun getInvoiceDefaultExpiry(context: Context): Flow<Long> = prefs(context).map { it[INVOICE_DEFAULT_EXPIRY] ?: 60 * 60 * 24 * 7 }
    suspend fun saveInvoiceDefaultExpiry(context: Context, expirySeconds: Long) = context.userPrefs.edit { it[INVOICE_DEFAULT_EXPIRY] = expirySeconds }

    private val TRAMPOLINE_MAX_BASE_FEE = longPreferencesKey("TRAMPOLINE_MAX_BASE_FEE")
    private val TRAMPOLINE_MAX_PROPORTIONAL_FEE = longPreferencesKey("TRAMPOLINE_MAX_PROPORTIONAL_FEE")

    suspend fun saveTrampolineMaxFee(context: Context, fee: TrampolineFees) = context.userPrefs.edit {
        it[TRAMPOLINE_MAX_BASE_FEE] = fee.feeBase.toLong()
        it[TRAMPOLINE_MAX_PROPORTIONAL_FEE] = fee.feeProportional
    }

    fun getTrampolineMaxFee(context: Context): Flow<TrampolineFees> = prefs(context).map {
        val feeBase = it[TRAMPOLINE_MAX_BASE_FEE] ?: -1L
        val feeProportional = it[TRAMPOLINE_MAX_PROPORTIONAL_FEE] ?: -1L
        TrampolineFees(feeBase = Satoshi(feeBase), feeProportional = feeProportional, cltvExpiryDelta = CltvExpiryDelta(144))
    }

    private val AUTO_PAY_TO_OPEN = booleanPreferencesKey("AUTO_PAY_TO_OPEN")
    fun getIsAutoPayToOpenEnabled(context: Context): Flow<Boolean> = prefs(context).map { it[AUTO_PAY_TO_OPEN] ?: true }
    suspend fun saveIsAutoPayToOpenEnabled(context: Context, isEnabled: Boolean) = context.userPrefs.edit { it[AUTO_PAY_TO_OPEN] = isEnabled }

}