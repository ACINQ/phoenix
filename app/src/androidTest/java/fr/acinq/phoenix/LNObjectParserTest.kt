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
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.lnurl.LNUrlAuth
import fr.acinq.phoenix.lnurl.LNUrlWithdraw
import fr.acinq.phoenix.utils.BitcoinURI
import fr.acinq.phoenix.utils.Wallet
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.RuntimeException

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
    Wallet.parseLNObject("lightning:LNURL1DP68GURN8GHJ7MRWW4EXCTNZD9NHXATW9EU8J730D3H82UNV94MKJARGV3EXZAELWDJHXUMFDAHR6WPSXU6NYVRRVE3K2VFJX9JXYCF3XA3RZDMPV43NWDPSVCUKXDFHVVEKXDMYXD3XVCN9XSEK2VPHVS6KVERYXUUNJWR9VS6XYCEHX5CQQCJJXZ") as LNUrlWithdraw
  }

  @Test(expected = RuntimeException::class)
  fun bolt_11_invalid() {
    Wallet.parseLNObject("lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgp")  as PaymentRequest
  }

  @Test(expected = RuntimeException::class)
  fun bolt_11_bad_prefix() {
    Wallet.parseLNObject("whatever:lntb15u1p0ct4v7pp5rdsy32dyuhxrsv67l05yfus4hmfu36cknav0zgxk432v0nvt5a0qdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5h2626t59xdj8jkjju3hwlg5rlsrqtcvr54k09k09ma77gd378ahsy0qc5hgvz44x57aswjns56kk7m75cgn6hns0yt7ur4m38k44wuusq0r7sfq4e23nvqznqfpvha452wd8ewemett86gw27n2uwqe8aqgpfna3yz") as PaymentRequest
  }

  @Test(expected = RuntimeException::class)
  fun lnurl_invalid() {
    Wallet.parseLNObject("lnurl1dp68gurn8ghj7ctsdyhxcmndv9exket5wvhxxmmd9akxuatjdshkz0m5v9nn6mr0va5kufntxy7kzvfnv5ckxvmzxu6r2enrxscnxc33vvunsdrx8qmnxdfkxa3xgc3sv9jkgctyvgmrxde5xqcr2veh8ymrycf5vvuxydp5vyexgdpexgnxsmtpvv7nwc3cxyuxgdf58qmrsvehvvcxyvmrxvurxc3jx93rsetyvfjnyepn89jxvcmpxccnyvejxe3kxc3cxumxvde5v3jxvc3sxcmrycnxxuj63qyjaaaaaaaa") as LNUrlAuth
  }
}
