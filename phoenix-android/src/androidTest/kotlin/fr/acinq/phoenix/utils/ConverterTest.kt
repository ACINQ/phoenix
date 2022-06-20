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
import fr.acinq.phoenix.android.utils.Converter.toMilliSatoshi
import fr.acinq.phoenix.data.BitcoinUnit
import junit.framework.Assert.assertEquals
import org.junit.Test

class ConverterTest {
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
}