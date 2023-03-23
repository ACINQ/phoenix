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

package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.*
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class NodeParamsManager(
    loggerFactory: LoggerFactory,
    chain: NodeParams.Chain,
    walletManager: WalletManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        walletManager = business.walletManager
    )

    private val log = newLogger(loggerFactory)

    private val _nodeParams = MutableStateFlow<NodeParams?>(null)
    val nodeParams: StateFlow<NodeParams?> = _nodeParams

    init {
        // we listen to the wallet manager and update node params and databases when the wallet changes
        launch {
            walletManager.keyManager.filterNotNull().collect { keyManager ->
                log.info { "nodeid=${keyManager.nodeId}" }
                val nodeParams = NodeParams(
                    chain = chain,
                    loggerFactory = loggerFactory,
                    keyManager = keyManager,
                ).copy(
                    alias = "phoenix",
                    // FIXME: use proper liquidity policy and node
                    zeroConfPeers = setOf(PublicKey.fromHex("025c0e9a61ea4b7ce06a6d7be46c79459c5690c093e110c243ce5424514271b903")),
                    liquidityPolicy = LiquidityPolicy.Auto(100, 30000.sat)
                )
                _nodeParams.value = nodeParams
            }
        }
    }
}