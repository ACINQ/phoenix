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

import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.*
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.rawWalletParams
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApiWalletParamsTest {

    @Test
    fun wallet_params_deserialization() {
        val json = Json { ignoreUnknownKeys = true }
        val decodedParams = json.decodeFromString(WalletContext.V0.serializer(), rawWalletParams)

        assertNotNull(decodedParams)

        fun WalletContext.V0.ChainContext.checkStructure() {
            assertEquals(28, version)
            assertEquals(0, latestCriticalVersion)
            assertEquals(4, trampoline.v2.attempts.size)
            assertEquals(WalletContext.V0.TrampolineParams.TrampolineFees(1, 500, 576), trampoline.v2.attempts.first())
            assertEquals(WalletContext.V0.TrampolineParams.TrampolineFees(12, 5000, 576), trampoline.v2.attempts.last())
        }

        decodedParams.testnet.checkStructure()
        decodedParams.mainnet.checkStructure()
        assertEquals("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134",
            decodedParams.testnet.trampoline.v2.nodes.first().export().id.toString())
        assertEquals("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134",
            decodedParams.testnet.trampoline.v2.nodes.first().export().id.toString())
    }

    @Test
    fun export_to_wallet_params() {
        val json = Json { ignoreUnknownKeys = true }
        val apiWalletParams = json.decodeFromString(WalletContext.V0.serializer(), rawWalletParams)
        assertNotNull(apiWalletParams)

        assertEquals(
            NodeUri(
                PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"),
                "13.248.222.197",
                9735
            ),
            apiWalletParams.export(Chain.Testnet).walletParams().trampolineNode)
    }

}
