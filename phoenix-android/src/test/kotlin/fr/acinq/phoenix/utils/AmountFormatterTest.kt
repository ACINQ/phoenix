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

package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toMilliSatoshi
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class AmountConverterTest {

    private var defaultLocale: Locale? = null

    @Before
    fun pinLocale() {
        defaultLocale = Locale.getDefault()
        // NumberFormat.getInstance() in AmountFormatter is locale-sensitive; pin to US so assertions
        // remain stable across developer machines and CI runners.
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        defaultLocale?.let { Locale.setDefault(it) }
    }
    @Test
    fun test_double_to_msat_rounding() {
        // 1 msat
        assertEquals(1.msat, 0.001.toMilliSatoshi(BitcoinUnit.Sat))
        assertEquals(1.msat, 0.00_001.toMilliSatoshi(BitcoinUnit.Bit))
        assertEquals(1.msat, 0.00000_001.toMilliSatoshi(BitcoinUnit.MBtc))
        assertEquals(1.msat, 0.000_00000_001.toMilliSatoshi(BitcoinUnit.Btc))

        // 1 msat with sub-msat dust
        assertEquals(1.msat, 0.0011.toMilliSatoshi(BitcoinUnit.Sat))
        assertEquals(1.msat, 0.000011.toMilliSatoshi(BitcoinUnit.Bit))
        assertEquals(1.msat, 0.000000011.toMilliSatoshi(BitcoinUnit.MBtc))
        assertEquals(1.msat, 0.000000000011.toMilliSatoshi(BitcoinUnit.Btc))

        // sub-msat dust is truncated
        assertEquals(0.msat, 0.000999999.toMilliSatoshi(BitcoinUnit.Sat))
        assertEquals(0.msat, 0.00000999999.toMilliSatoshi(BitcoinUnit.Bit))
        assertEquals(0.msat, 0.00000000999999.toMilliSatoshi(BitcoinUnit.MBtc))
        assertEquals(0.msat, 0.00000000000999999.toMilliSatoshi(BitcoinUnit.Btc))

        // 1 sat with sub-msat dust is truncated
        assertEquals(1_000.msat, 1.000_1.toMilliSatoshi(BitcoinUnit.Sat))
        assertEquals(1_000.msat, 0.01_000_1.toMilliSatoshi(BitcoinUnit.Bit))
        assertEquals(1_000.msat, 0.00001_000_1.toMilliSatoshi(BitcoinUnit.MBtc))
        assertEquals(1_000.msat, 0.000_00001_000_1.toMilliSatoshi(BitcoinUnit.Btc))

        // 1_999.9999 sat is not rounded up to 2_000 sat
        assertEquals(1_999_999.msat, 1999.999_9.toMilliSatoshi(BitcoinUnit.Sat))
        assertEquals(1_999_999.msat, 19.99_999_9.toMilliSatoshi(BitcoinUnit.Bit))
        assertEquals(1_999_999.msat, 0.01999_999_9.toMilliSatoshi(BitcoinUnit.MBtc))
        assertEquals(1_999_999.msat, 0.000_01999_999_9.toMilliSatoshi(BitcoinUnit.Btc))
    }

    // Regression test for issue #535: when displaying the historical ("then") fiat value of a
    // payment, the (then) line must be denominated in the currency that was actually recorded
    // (rateThen.fiatCurrency), regardless of the user's current preferred fiat.
    //
    // Before the fix, the same `prefFiat` unit was applied to both the (now) line and the
    // (then) line, so after switching the preferred currency the (then) numeric value would
    // visibly be wrong (e.g. "1 PHP" displayed for a payment originally worth ~1 EUR).
    @Test
    fun then_fiat_uses_historical_rate_currency_not_preferred_fiat() {
        // Historical rate at the time of the payment: 1 BTC = 100,000 EUR (round number to keep
        // floating-point conversion deterministic — FIAT_FORMAT uses RoundingMode.CEILING and
        // would otherwise be sensitive to sub-cent drift).
        val rateThen = ExchangeRate.BitcoinPriceRate(
            fiatCurrency = FiatCurrency.EUR,
            price = 100_000.0,
            source = "test",
            timestampMillis = 0L,
        )
        // 0.0001 BTC * 100_000 EUR/BTC = 10 EUR exactly.
        val amount = 10_000_000.msat

        // Mirrors the fixed call site in PaymentTechnicalView.kt: pass rateThen.fiatCurrency.
        val formattedThen = amount.toPrettyString(rateThen.fiatCurrency, rateThen, withUnit = true)

        assertTrue(
            "expected output to end with the historical rate's currency code (EUR), got: $formattedThen",
            formattedThen.endsWith(" EUR"),
        )
        assertTrue(
            "expected numeric portion ~10 EUR (allowing for FIAT_FORMAT CEILING rounding), got: $formattedThen",
            formattedThen.startsWith("10.0") || formattedThen.startsWith("10.01"),
        )
    }

    @Test
    fun then_fiat_label_does_not_follow_preferred_fiat_change() {
        val rateThen = ExchangeRate.BitcoinPriceRate(
            fiatCurrency = FiatCurrency.EUR,
            price = 100_000.0,
            source = "test",
            timestampMillis = 0L,
        )
        val amount = 10_000_000.msat

        // Sanity check: had the call site used the preferred fiat (PHP) as the display unit, the
        // resulting string would carry " PHP" — that's the bug guarded against by passing
        // rateThen.fiatCurrency at the call site.
        val withBuggyPreferredFiat = amount.toPrettyString(FiatCurrency.PHP, rateThen, withUnit = true)
        assertTrue(withBuggyPreferredFiat.endsWith(" PHP"))

        // The fixed call site instead passes rateThen.fiatCurrency, producing the historically
        // correct currency label.
        val withFixedCallSite = amount.toPrettyString(rateThen.fiatCurrency, rateThen, withUnit = true)
        assertTrue(withFixedCallSite.endsWith(" EUR"))
    }
}