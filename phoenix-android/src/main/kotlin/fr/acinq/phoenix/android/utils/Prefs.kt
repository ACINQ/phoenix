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

package fr.acinq.phoenix.android.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import java.io.IOException


val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = Prefs.DATASTORE_FILE)

object Prefs {
    private val log = LoggerFactory.getLogger(this::class.java)

    const val DATASTORE_FILE = "settings"

    // -- unit, fiat, conversion...
    val PREFS_SHOW_AMOUNT_IN_FIAT = booleanPreferencesKey("PREFS_SHOW_AMOUNT_IN_FIAT")
    val PREFS_BITCOIN_UNIT = stringPreferencesKey("PREFS_BITCOIN_UNIT")
    val PREFS_FIAT_CURRENCY = stringPreferencesKey("PREFS_FIAT_CURRENCY")

    // -- node configuration
    val PREFS_ELECTRUM_ADDRESS = stringPreferencesKey("PREFS_ELECTRUM_ADDRESS")
    const val PREFS_ELECTRUM_FORCE_SSL = "PREFS_ELECTRUM_FORCE_SSL"

    private fun prefs(context: Context): Flow<Preferences> {
        return context.datastore.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
    }

    // -- ==================================

    fun getBitcoinUnit(context: Context): Flow<BitcoinUnit> = prefs(context).map { BitcoinUnit.valueOf(it[PREFS_BITCOIN_UNIT] ?: BitcoinUnit.Sat.name) }
    suspend fun saveBitcoinUnit(context: Context, coinUnit: BitcoinUnit) = context.datastore.edit { it[PREFS_BITCOIN_UNIT] = coinUnit.name }
    fun getFiatCurrency(context: Context): Flow<FiatCurrency> = prefs(context).map { FiatCurrency.valueOf(it[PREFS_FIAT_CURRENCY] ?: FiatCurrency.USD.name) }
    suspend fun saveFiatCurrency(context: Context, currency: FiatCurrency) = context.datastore.edit { it[PREFS_FIAT_CURRENCY] = currency.name }
    fun getIsAmountInFiat(context: Context): Flow<Boolean> = prefs(context).map { it[PREFS_SHOW_AMOUNT_IN_FIAT] ?: false }
    suspend fun saveIsAmountInFiat(context: Context, inFiat: Boolean) = context.datastore.edit { it[PREFS_SHOW_AMOUNT_IN_FIAT] = inFiat }

    fun getElectrumServer(context: Context): Flow<ServerAddress?> = prefs(context).map {
        it[PREFS_ELECTRUM_ADDRESS]?.takeIf { it.isNotBlank() }?.let { address ->
            log.info("retrieved preferred electrum=$address from datastore")
            if (address.contains(":")) {
                val (host, port) = address.split(":")
                ServerAddress(host, port.toInt())
            } else ServerAddress(address, 50002)
        }
    }

    suspend fun saveElectrumServer(context: Context, address: String) = context.datastore.edit { it[PREFS_ELECTRUM_ADDRESS] = address }
    suspend fun saveElectrumServer(context: Context, address: ServerAddress) = saveElectrumServer(context, "${address.host}:${address.port}")
}
