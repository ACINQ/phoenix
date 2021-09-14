/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`package$`
import fr.acinq.phoenix.utils.Converter
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
@SmallTest
class ConverterTest {

  @Test
  fun test_millionths_to_percentage() {
    // per lightning rfc, the fee taken by a node = fee_base_msat + (amount_to_forward * fee_proportional_millionths / 1000000)
    // i.e: fee_proportional_millionths / 1000000 = fee_percent
    // i.e: fee_proportional_millionths = 100 <=> 0.0001 = 0.01%
    Locale.setDefault(Locale.US)
    Assert.assertEquals("0.00", Converter.perMillionthsToPercentageString(-1))
    Assert.assertEquals("0.00", Converter.perMillionthsToPercentageString(0))
    Assert.assertEquals("0.0001", Converter.perMillionthsToPercentageString(1))
    Assert.assertEquals("0.01", Converter.perMillionthsToPercentageString(100))
    Assert.assertEquals("0.05", Converter.perMillionthsToPercentageString(500))
    Assert.assertEquals("0.10", Converter.perMillionthsToPercentageString(1000))
    Assert.assertEquals("0.12", Converter.perMillionthsToPercentageString(1200))
    Assert.assertEquals("0.80", Converter.perMillionthsToPercentageString(8000))
    Assert.assertEquals("0.9999", Converter.perMillionthsToPercentageString(9999))
    Assert.assertEquals("12345.6789", Converter.perMillionthsToPercentageString(123456789))

    Locale.setDefault(Locale.FRANCE)
    Assert.assertEquals("12345,6789", Converter.perMillionthsToPercentageString(123456789))
  }

  @Test
  fun test_percentage_to_millionths() {
    Locale.setDefault(Locale.US)
    Assert.assertEquals(0, Converter.percentageToPerMillionths("-1.00"))
    Assert.assertEquals(0, Converter.percentageToPerMillionths("0.00"))
    Assert.assertEquals(1, Converter.percentageToPerMillionths("0.0001"))
    Assert.assertEquals(10, Converter.percentageToPerMillionths("0.001"))
    Assert.assertEquals(100, Converter.percentageToPerMillionths("0.01"))
    Assert.assertEquals(8000, Converter.percentageToPerMillionths("0.8"))
    Assert.assertEquals(1000100, Converter.percentageToPerMillionths("100.01"))
    Assert.assertEquals(Long.MAX_VALUE, Converter.percentageToPerMillionths("174095871350984735094837509218750982710497502745097350"))
    // Test from RFC
    Assert.assertEquals(MilliSatoshi(10199), `package$`.`MODULE$`.nodeFee(MilliSatoshi(200), Converter.percentageToPerMillionths("0.2"), MilliSatoshi(4999999)))

    Locale.setDefault(Locale.FRANCE)
    Assert.assertEquals(1, Converter.percentageToPerMillionths("0,0001"))
  }

  @Test(expected = Exception::class)
  fun test_fail_alpha_percentage_to_millionths() {
    Converter.percentageToPerMillionths("abc2")
  }
}
