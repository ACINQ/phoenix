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
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.lnurl.*
import fr.acinq.phoenix.legacy.utils.BitcoinURI
import fr.acinq.phoenix.legacy.utils.UnreadableLightningObject
import fr.acinq.phoenix.legacy.utils.Wallet
import okhttp3.HttpUrl
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LNObjectParserTest {

  @Test
  fun basic_ok() {
    // bolt 11
    Wallet.parseLNObject("lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgpfna3yz") as PaymentRequest
    Wallet.parseLNObject("lightning:lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgpfna3yz") as PaymentRequest
    Wallet.parseLNObject("lightning://lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgpfna3yz") as PaymentRequest

    // bitcoin uri
    Wallet.parseLNObject("2N6TSQSJtykBsqH68vQyoLb3UHPfsYzJQ7Z") as BitcoinURI
    Wallet.parseLNObject("bitcoin:2N6TSQSJtykBsqH68vQyoLb3UHPfsYzJQ7Z") as BitcoinURI
    Wallet.parseLNObject("bitcoin://2N6TSQSJtykBsqH68vQyoLb3UHPfsYzJQ7Z") as BitcoinURI

    // lnurl
    Wallet.parseLNObject("lnurl1dp68gurn8ghj7ctsdyhxcmndv9exket5wvhxxmmd9akxuatjdshkz0m5v9nn6mr0va5kufntxy7kzvfnv5ckxvmzxu6r2enrxscnxc33vvunsdrx8qmnxdfkxa3xgc3sv9jkgctyvgmrxde5xqcr2veh8ymrycf5vvuxydp5vyexgdpexgnxsmtpvv7nwc3cxyuxgdf58qmrsvehvvcxyvmrxvurxc3jx93rsetyvfjnyepn89jxvcmpxccnyvejxe3kxc3cxumxvde5v3jxvc3sxcmrycnxxuj63qyj") as LNUrlAuth
    Wallet.parseLNObject("lightning:LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHQCTE8AEK2UMND9HKU0FCV3JKGVRXVFJRXE33X93RXCECVVUXXCTRV43R2VFEXVERZEPSVVUXZVE5VDJXZERRVDNRXEP4VFNRQDRRXSURQWPCX5MN2WR9X4NRGRA4LD4") as LNUrlPay
    Wallet.parseLNObject("lnurl:lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veax2un09e3k7mf0w5lhgct884kx7emfdcnxkvfa8p3nqepjv5cnsdesxdjnxe33x5cngvfev5enxdtzvdnxze33xpjrvwrr8q6k2epkxgux2dnpxe3rgefevg6x2vp4v4nx2d3exenryesuzq939") as LNUrlAuth
    Wallet.parseLNObject("https://service.com/giftcard/redeem?id=123&lightning=LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHQCTE8AEK2UMND9HKU0FCV3JKGVRXVFJRXE33X93RXCECVVUXXCTRV43R2VFEXVERZEPSVVUXZVE5VDJXZERRVDNRXEP4VFNRQDRRXSURQWPCX5MN2WR9X4NRGRA4LD4") as LNUrlPay
    Wallet.parseLNObject("http://foo.bar?lightning=LNURL1DP68GURN8GHJ7MRWW4EXCTNZD9NHXATW9EU8J730D3H82UNV94KX7EMFDCLHGCT884KX7EMFDCNXKVFAX5CRGCFJXANRWVN9VSUK2WTPVF3NXVP4V93KXD3HVS6RWVTZXY6NWVEHV5CNQCFN893RJWF4V9NRQVM9XANRYVR9X4NXGETY8Q6KYDC0Q6NTC") as LNUrlAuth
    Wallet.parseLNObject("foobar://test?lightning=LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHW6T5DPJ8YCTH8AEK2UMND9HKU0FCV3JKGVRXVFJRXE33X93RXCECVVUXXCTRV43R2VFEXVERZEPSVVUXZVE5VDJXZERRVDNRXEP4VFNRQDRRXSURQWPCX5MN2WR9X4NRG65RLR9") as LNUrlWithdraw

    // non bech32 lnurl
//    var url = Wallet.parseLNObject("lnurlp:lnurl-toolbox.degreesofzero.com/u?q=a07d243eb98af499b538e0b6ad387b014b48181b04a5feb6e55d30993f96635a")
//    Assert.assertTrue(url is LNUrlPay)
//    Assert.assertEquals(MilliSatoshi(10_000), (url as LNUrlPay).minSendable)
//    Assert.assertEquals(MilliSatoshi(20_000), url.maxSendable)
//    Assert.assertEquals(null, url.maxCommentLength)
//    Assert.assertEquals("https://lnurl-toolbox.degreesofzero.com/u/a07d243eb98af499b538e0b6ad387b014b48181b04a5feb6e55d30993f96635a", url.callbackUrl)
//    Assert.assertEquals(LNUrlPayMetadata(raw = "[[\"text/plain\",\"lnurl-toolbox: payRequest\"]]", plainText = "lnurl-toolbox: payRequest", image = null, longDesc = null, identifier = null, email = null), url.rawMetadata)

    // lightning address
    val email = LNUrl.extractLNUrl("acinq@zbd.gg")
    Assert.assertTrue(email is LNUrlPay)
    Assert.assertEquals("api.zebedee.io", HttpUrl.parse((email as LNUrlPay).callbackUrl)!!.host())
  }

  @Test(expected = UnreadableLightningObject::class)
  fun bolt_11_invalid() {
    Wallet.parseLNObject("lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgp")  as PaymentRequest
  }

  @Test(expected = UnreadableLightningObject::class)
  fun bolt_11_bad_prefix() {
    Wallet.parseLNObject("whatever:lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgpfna3yz") as PaymentRequest
  }

  @Test(expected = UnreadableLightningObject::class)
  fun lnurl_invalid() {
    Wallet.parseLNObject("lnurl1dp68gurn8ghj7ctsdyhxcmndv9exket5wvhxxmmd9akxuatjdshkz0m5v9nn6mr0va5kufntxy7kzvfnv5ckxvmzxu6r2enrxscnxc33vvunsdrx8qmnxdfkxa3xgc3sv9jkgctyvgmrxde5xqcr2veh8ymrycf5vvuxydp5vyexgdpexgnxsmtpvv7nwc3cxyuxgdf58qmrsvehvvcxyvmrxvurxc3jx93rsetyvfjnyepn89jxvcmpxccnyvejxe3kxc3cxumxvde5v3jxvc3sxcmrycnxxuj63qyjaaaaaaaa") as LNUrlAuth
  }

  @Test(expected = UnreadableLightningObject::class)
  fun lnurl_invalid_fallback_param() {
    Wallet.parseLNObject("http://foo.bar/redeem?lightning2=LNURL1DP68GURN8GHJ7MRWW4EXCTNZD9NHXATW9EU8J730D3H82UNV94KX7EMFDCLHGCT884KX7EMFDCNXKVFAX5CRGCFJXANRWVN9VSUK2WTPVF3NXVP4V93KXD3HVS6RWVTZXY6NWVEHV5CNQCFN893RJWF4V9NRQVM9XANRYVR9X4NXGETY8Q6KYDC0Q6NTC")
  }

  @Test(expected = UnreadableLightningObject::class)
  fun lnurl_invalid_fallback_uri() {
    Wallet.parseLNObject("foo:bar?lightning=LNURL1DP68GURN8GHJ7MRWW4EXCTNZD9NHXATW9EU8J730D3H82UNV94KX7EMFDCLHGCT884KX7EMFDCNXKVFAX5CRGCFJXANRWVN9VSUK2WTPVF3NXVP4V93KXD3HVS6RWVTZXY6NWVEHV5CNQCFN893RJWF4V9NRQVM9XANRYVR9X4NXGETY8Q6KYDC0Q6NTC")
  }
}
