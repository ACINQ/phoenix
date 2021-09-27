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
import androidx.preference.PreferenceManager
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import org.slf4j.LoggerFactory

object Prefs {
    private val log = LoggerFactory.getLogger(this::class.java)

    private const val PREFS_LAST_VERSION_USED: String = "PREFS_LAST_VERSION_USED"

    // -- unit, fiat, conversion...
    const val PREFS_SHOW_AMOUNT_IN_FIAT: String = "PREFS_SHOW_AMOUNT_IN_FIAT"
    const val PREFS_FIAT_CURRENCY: String = "PREFS_FIAT_CURRENCY"
    const val PREFS_BITCOIN_UNIT: String = "PREFS_COIN_UNIT"

    // -- payment configuration
    const val PREFS_PAYMENT_DEFAULT_DESCRIPTION = "PREFS_PAYMENT_DEFAULT_DESCRIPTION"

    // -- node configuration
    const val PREFS_ELECTRUM_ADDRESS = "PREFS_ELECTRUM_ADDRESS"
    const val PREFS_ELECTRUM_FORCE_SSL = "PREFS_ELECTRUM_FORCE_SSL"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    // -- ==================================

    fun getLastVersionUsed(context: Context): Int = prefs(context).getInt(PREFS_LAST_VERSION_USED, 0)
    fun saveLastVersionUsed(context: Context, version: Int) = prefs(context).edit().putInt(PREFS_LAST_VERSION_USED, version).apply()

    fun getFiatCurrency(context: Context): FiatCurrency = FiatCurrency.valueOf(prefs(context).getString(PREFS_FIAT_CURRENCY, FiatCurrency.USD.name) ?: FiatCurrency.USD.name)
    fun getBitcoinUnit(context: Context): BitcoinUnit = BitcoinUnit.valueOf(prefs(context).getString(PREFS_BITCOIN_UNIT, BitcoinUnit.Sat.name) ?: BitcoinUnit.Sat.name)

    fun useFiat(context: Context): Boolean = prefs(context).getBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, false)
    fun saveUseFiat(context: Context, inFiat: Boolean) = prefs(context).edit().putBoolean(PREFS_SHOW_AMOUNT_IN_FIAT, inFiat).apply()

    fun getDefaultDescription(context: Context): String = prefs(context).getString(PREFS_PAYMENT_DEFAULT_DESCRIPTION, "") ?: ""

    fun getElectrumServer(context: Context): ServerAddress? = prefs(context).getString(PREFS_ELECTRUM_ADDRESS, null)?.run {
        log.info("using pref electrum=$this")
        if (contains(":")) {
            val (host, port) =  split(":")
            ServerAddress(host, port.toInt())
        } else null
    }
    fun saveElectrumServer(context: Context, address: String) = prefs(context).edit().putString(PREFS_ELECTRUM_ADDRESS, address).apply()
    fun saveElectrumServer(context: Context, address: ServerAddress) = prefs(context).edit().putString(PREFS_ELECTRUM_ADDRESS, "${address.host}:${address.port}").apply()
}
