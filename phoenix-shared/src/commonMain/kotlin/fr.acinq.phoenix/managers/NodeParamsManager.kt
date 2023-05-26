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
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class NodeParamsManager(
    loggerFactory: LoggerFactory,
    chain: NodeParams.Chain,
    walletManager: WalletManager,
    appConfigurationManager: AppConfigurationManager,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        walletManager = business.walletManager,
        appConfigurationManager = business.appConfigurationManager,
    )

    private val log = newLogger(loggerFactory)

    private val _nodeParams = MutableStateFlow<NodeParams?>(null)
    val nodeParams: StateFlow<NodeParams?> = _nodeParams

    init {
        launch {
            combine(
                walletManager.keyManager.filterNotNull(),
                appConfigurationManager.startupParams.filterNotNull(),
            ) { keyManager, startupParams ->
                NodeParams(
                    chain = chain,
                    loggerFactory = loggerFactory,
                    keyManager = keyManager,
                ).copy(
                    alias = "phoenix",
                    zeroConfPeers = setOf(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134")),
                    liquidityPolicy = MutableStateFlow(startupParams.liquidityPolicy),
                )
            }.collect {
                log.info { "nodeid=${it.nodeId}" }
                _nodeParams.value = it
            }
        }
    }

    companion object {
        val defaultLiquidityPolicy = LiquidityPolicy.Auto(maxAbsoluteFee = 3_000.sat /* 10 % */, maxRelativeFeeBasisPoints = 30_00)
    }
}