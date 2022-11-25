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

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.managers.xpub
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyManagerTest {
    private val mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val seed = MnemonicCode.toSeed(mnemonics, passphrase = "").toByteVector()

    private val mainnetKeyManager = LocalKeyManager(seed, Chain.Mainnet.chainHash)
    private val testnetKeyManager = LocalKeyManager(seed, Chain.Testnet.chainHash)

    @Test
    fun masterPublicKey() {
        assertEquals(
            "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs",
            mainnetKeyManager.xpub().first
        )
        assertEquals(
            "vpub5Y6cjg78GGuNLsaPhmYsiw4gYX3HoQiRBiSwDaBXKUafCt9bNwWQiitDk5VZ5BVxYnQdwoTyXSs2JHRPAgjAvtbBrf8ZhDYe2jWAqvZVnsc",
            testnetKeyManager.xpub().first
        )
    }
}
