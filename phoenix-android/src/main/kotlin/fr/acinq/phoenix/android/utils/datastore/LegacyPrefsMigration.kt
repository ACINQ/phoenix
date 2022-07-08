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
import com.google.common.net.HostAndPort
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.ThemeHelper
import org.slf4j.LoggerFactory


object LegacyPrefsMigration {
    private val log = LoggerFactory.getLogger(this::class.java)

    /** Import the legacy app's preferences into the new app's datastores. */
    suspend fun doMigration(context: Context) {
        log.info("started migrating legacy user preferences")

        // -- utils

        InternalData.saveLastUsedAppCode(context, Prefs.getLastVersionUsed(context))
        InternalData.saveMnemonicsCheckTimestamp(context, Prefs.getMnemonicsSeenTimestamp(context))
        Prefs.getFCMToken(context)?.let { InternalData.saveFcmToken(context, it) }

        // -- display

        UserPrefs.saveUserTheme(
            context, when (Prefs.getTheme(context)) {
                ThemeHelper.darkMode -> UserTheme.DARK
                ThemeHelper.lightMode -> UserTheme.LIGHT
                else -> UserTheme.SYSTEM
            }
        )
        UserPrefs.saveBitcoinUnit(
            context, when (Prefs.getCoinUnit(context).code()) {
                "sat" -> BitcoinUnit.Sat
                "bits" -> BitcoinUnit.Bit
                "mbtc" -> BitcoinUnit.MBtc
                else -> BitcoinUnit.Btc
            }
        )
        UserPrefs.saveHideBalance(context, Prefs.showBalanceHome(context))
        UserPrefs.saveIsAmountInFiat(context, Prefs.getShowAmountInFiat(context))
        UserPrefs.saveFiatCurrency(context, FiatCurrency.valueOfOrNull(Prefs.getFiatCurrency(context)) ?: FiatCurrency.USD)

        // -- security

        UserPrefs.saveIsScreenLockActive(context, Prefs.isScreenLocked(context))

        // -- electrum

        UserPrefs.saveElectrumServer(context, Prefs.getElectrumServer(context).takeIf { it.isNotBlank() }?.let {
            val hostPort = HostAndPort.fromString(it).withDefaultPort(50002)
            // TODO: handle onion addresses and TOR
            ServerAddress(hostPort.host, hostPort.port, TcpSocket.TLS.TRUSTED_CERTIFICATES)
        })

        // -- payment settings

        UserPrefs.saveInvoiceDefaultDesc(context, Prefs.getDefaultPaymentDescription(context))
        UserPrefs.saveInvoiceDefaultExpiry(context, Prefs.getPaymentsExpirySeconds(context))

        Prefs.getMaxTrampolineCustomFee(context)?.let {
            TrampolineFees(feeBase = Satoshi(it.feeBase.toLong()), feeProportional = it.feeProportionalMillionths, cltvExpiryDelta = CltvExpiryDelta(it.cltvExpiry.toInt()))
        }?.let {
            UserPrefs.saveTrampolineMaxFee(context, it)
        }

        UserPrefs.saveIsAutoPayToOpenEnabled(context, Prefs.isAutoPayToOpenEnabled(context))

        log.info("finished migration of legacy user preferences")
        InternalData.saveIsLegacyPrefsMigrationDone(context, true)
    }
}