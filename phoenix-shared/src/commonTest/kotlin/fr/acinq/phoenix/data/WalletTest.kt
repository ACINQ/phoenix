/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletTest {
    private val mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val seed = MnemonicCode.toSeed(mnemonics, passphrase = "")

    private val mainnetWallet = Wallet(seed, Chain.Mainnet)
    private val testnetWallet = Wallet(seed, Chain.Testnet)

    @Test
    fun masterPublicKey() {
        // Mainnet
        assertEquals(mainnetWallet.masterPublicKey("m/84'/0'/0'"), "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs")
        // Testnet
        assertEquals(testnetWallet.masterPublicKey("m/84'/1'/0'"), "vpub5Y6cjg78GGuNLsaPhmYsiw4gYX3HoQiRBiSwDaBXKUafCt9bNwWQiitDk5VZ5BVxYnQdwoTyXSs2JHRPAgjAvtbBrf8ZhDYe2jWAqvZVnsc")
    }

    @Test
    fun lnurlAuthPath() {
        // Test vector from spec:
        // https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
        val path = mainnetWallet.lnurlAuthPath(
            domain = "site.com",
            hashingKey = Hex.decode("0x7d417a6a5e9a6a4a879aeaba11a11838764c8fa2b959c242d43dea682b3e409b01")
        )
        println("path = $path")
        assertEquals(path, KeyPath("m/138'/3751473387/2829804099/4228872783/4134047485"))
    }

}
