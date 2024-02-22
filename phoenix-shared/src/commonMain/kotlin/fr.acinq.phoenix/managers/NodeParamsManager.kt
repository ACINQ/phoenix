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

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.NodeUri
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.shared.BuildVersions
import fr.acinq.lightning.logging.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class NodeParamsManager(
    loggerFactory: LoggerFactory,
    chain: Chain,
    walletManager: WalletManager,
    appConfigurationManager: AppConfigurationManager,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        walletManager = business.walletManager,
        appConfigurationManager = business.appConfigurationManager,
    )

    private val log = loggerFactory.newLogger(this::class)

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
                    zeroConfPeers = setOf(trampolineNodeId),
                    liquidityPolicy = MutableStateFlow(startupParams.liquidityPolicy),
                )
            }.collect {
                log.info { "hello!" }
                log.info { "nodeid=${it.nodeId}" }
                log.info { "commit=${BuildVersions.PHOENIX_COMMIT}" }
                log.info { "lightning-kmp version=${BuildVersions.LIGHTNING_KMP_VERSION}" }
                _nodeParams.value = it
            }
        }
    }

    companion object {
        val chain = Chain.Mainnet
        val trampolineNodeId = PublicKey.fromHex("03864ef025fde8fb587d989186ce6a4a186895ee44a926bfc370e2c366597a3f8f")
        val trampolineNodeUri = NodeUri(id = trampolineNodeId, "3.33.236.230", 9735)
        const val remoteSwapInXpub = "xpub69q3sDXXsLuHVbmTrhqmEqYqTTsXJKahdfawXaYuUt6muf1PbZBnvqzFcwiT8Abpc13hY8BFafakwpPbVkatg9egwiMjed1cRrPM19b2Ma7"
        val defaultLiquidityPolicy = LiquidityPolicy.Auto(maxAbsoluteFee = 5_000.sat, maxRelativeFeeBasisPoints = 50_00 /* 50% */, skipAbsoluteFeeCheck = false)

        fun liquidityLeaseRate(amount: Satoshi): LiquidityAds.LeaseRate {
            // WARNING : THIS MUST BE KEPT IN SYNC WITH LSP OTHERWISE FUNDING REQUEST WILL BE REJECTED BY PHOENIX
            val fundingWeight = if (amount <= 100_000.sat) {
                271 * 2 // 2-inputs (wpkh) / 0-change
            } else if (amount <= 250_000.sat) {
                271 * 2 // 2-inputs (wpkh) / 0-change
            } else if (amount <= 500_000.sat) {
                271 * 4 // 4-inputs (wpkh) / 0-change
            } else if (amount <= 1_000_000.sat) {
                271 * 4 // 4-inputs (wpkh) / 0-change
            } else {
                271 * 6 // 6-inputs (wpkh) / 0-change
            }
            return LiquidityAds.LeaseRate(
                leaseDuration = 0,
                fundingWeight = fundingWeight,
                leaseFeeProportional = 100, // 1%
                leaseFeeBase = 0.sat,
                maxRelayFeeProportional = 100,
                maxRelayFeeBase = 1_000.msat
            )
        }
    }
}