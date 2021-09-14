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
package fr.acinq.phoenix

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import fr.acinq.bitcoin.Btc
import fr.acinq.bitcoin.Satoshi
import fr.acinq.phoenix.legacy.utils.BitcoinURI
import fr.acinq.phoenix.legacy.utils.BitcoinURIParseException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import scala.math.BigDecimal

@RunWith(AndroidJUnit4::class)
@SmallTest
class BitcoinURITest {
  private val valid_scheme = "bitcoin:"
  private val valid_address = "2MxGZTPhfGqcU4s8jn7FAM3AZCmAjaQTo1n"

  @Test(expected = BitcoinURIParseException::class)
  fun sanity_check() {
    BitcoinURI("bitcoin:$valid_address&foo=<\\0031 ")
  }

  // -------- scheme

  @Test
  fun scheme_none() {
    Assert.assertEquals(valid_address, BitcoinURI(valid_address).address)
  }

  @Test
  fun scheme_basic() {
    Assert.assertEquals(valid_address, BitcoinURI("bitcoin:$valid_address").address)
  }

  @Test
  fun scheme_many_cases() {
    Assert.assertEquals(valid_address, BitcoinURI("bITcoiN:$valid_address").address)
    Assert.assertEquals(valid_address, BitcoinURI("BitCoin:$valid_address").address)
  }

  @Test
  fun scheme_tolerated() {
    Assert.assertEquals(valid_address, BitcoinURI("bitcoin://$valid_address").address)
  }

  @Test(expected = BitcoinURIParseException::class)
  fun scheme_invalid() {
    BitcoinURI("bitcoinmagic:$valid_address")
  }

  // -------- address

  @Test
  fun address_basic() {
    Assert.assertEquals(valid_address, BitcoinURI(valid_address).address)
    Assert.assertEquals(valid_address, BitcoinURI("bitcoin:$valid_address").address)
    Assert.assertEquals("tb1q0g4ycmrjzp08jt4xhu2kyqxwdx58czhw78av0j", BitcoinURI("bitcoin:tb1q0g4ycmrjzp08jt4xhu2kyqxwdx58czhw78av0j").address)
  }

  @Test(expected = BitcoinURIParseException::class)
  fun address_empty() {
    BitcoinURI("bitcoin:")
  }

  @Test(expected = BitcoinURIParseException::class)
  fun address_invalid() {
    BitcoinURI("bitcoin:2MxGZTPhfGqcU4s8jn7FAM3AZCmAjaQTo1ncU4s8jn")
  }

  // -------- amount parameter

  @Test
  fun amount_valid() {
    Assert.assertEquals(BitcoinURI("bitcoin:${valid_address}?amount=1.23456789").amount, Btc.apply(BigDecimal.decimal(1.23456789)).toSatoshi())
    Assert.assertEquals(BitcoinURI("bitcoin://${valid_address}?amount=0.00000001").amount, Satoshi(1))
    Assert.assertEquals(BitcoinURI("${valid_address}?amount=0.00999999").amount, Satoshi(999999))
  }

  @Test
  fun amount_duplicate() {
    Assert.assertEquals(BitcoinURI("${valid_address}?amount=1.23456789&amount=0.123").amount, Btc.apply(BigDecimal.decimal(0.123)).toSatoshi())
  }

  @Test
  fun amount_invalid() {
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=21.9876.5432").amount)
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=abcdefgh").amount)
  }

  @Test
  fun amount_negative_or_zero() {
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=${-0.123}").amount)
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=${-.456}").amount)
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=${0}").amount)
  }

  @Test
  fun amount_empty() {
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?amount=").amount)
  }

  // -------- message/label parameters

  @Test
  fun message_label() {
    val uri = BitcoinURI("$valid_scheme$valid_address?label=foo%20BAR&message=Donation%20for%20project%20xyz")
    Assert.assertEquals("foo BAR", uri.label)
    Assert.assertEquals("Donation for project xyz", uri.message)
  }

  // -------- lightning fallback parameter

  @Test
  fun lightning_valid() {
    Assert.assertEquals(
      "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134",
      BitcoinURI("$valid_scheme$valid_address?lightning=lntb15u1p05vazrpp5apz75ghtq3ynmc5qm98tsgucmsav44fyffpguhzdep2kcgkfme4sdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5v4hqr48qe0u7al6lxwdpmp3w6k7evjdavm0lh7arpv3qaf038s5st2d8k8vvmxyav2wkfym9jp4mk64srmswgh7l6sqtq7l4xl3nknf8snltamvpw5p3yl9nxg0ax9k0698rr94qx6unrv8yhccmh4z9ghcq77hxps")
        .lightning!!.nodeId().toString())
  }

  @Test
  fun lightning_empty() {
    assert(null == BitcoinURI("$valid_scheme$valid_address?lightning=").lightning)
  }

  @Test
  fun lightning_invalid() {
    Assert.assertNull(BitcoinURI("$valid_scheme$valid_address?lightning=lntb15u1p05vazrpp").lightning)
  }

  // -------- required parameters

  @Test(expected = BitcoinURIParseException::class)
  fun param_required() {
    BitcoinURI("$valid_scheme$valid_address?req-param=whatever")
  }

  // -------- other parameters that are not required nor understood
  // https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki#forward-compatibility

  @Test
  fun param_payjoin() {
    // Payjoin is not required so it can be safely ignored
    BitcoinURI("bitcoin:$valid_address?pj=https://acinq.co")
  }

  @Test
  fun param_not_required() {
    Assert.assertEquals(valid_address, BitcoinURI("bitcoin:2MxGZTPhfGqcU4s8jn7FAM3AZCmAjaQTo1n?somethingyoudontunderstand=50&somethingelseyoudontget=999").address)
  }
}
