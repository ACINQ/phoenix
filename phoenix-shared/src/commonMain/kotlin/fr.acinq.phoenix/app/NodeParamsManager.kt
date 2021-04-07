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

package fr.acinq.phoenix.app

import fr.acinq.bitcoin.Block
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.fee.FeerateTolerance
import fr.acinq.lightning.blockchain.fee.OnChainFeeConf
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.db.ChannelsDb
import fr.acinq.lightning.db.Databases
import fr.acinq.lightning.db.PaymentsDb
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import org.kodein.memory.text.toHexString

class NodeParamsManager(
    loggerFactory: LoggerFactory,
    private val ctx: PlatformContext,
    chain: Chain,
    walletManager: WalletManager
) : CoroutineScope by MainScope() {

    private val log = newLogger(loggerFactory)

    private val _nodeParams = MutableStateFlow<NodeParams?>(null)
    val nodeParams: StateFlow<NodeParams?> = _nodeParams

    private val _databases = MutableStateFlow<Databases?>(null)
    val databases: StateFlow<Databases?> = _databases

    init {
        // we listen to the wallet manager and update node params and databases when the wallet changes
        launch {
            walletManager.wallet.collect {
                if (it == null) return@collect
                log.info { "wallet update in wallet manager, building node params and databases..." }
                val genesisBlock = when (chain) {
                    Chain.Mainnet -> Block.LivenetGenesisBlock
                    Chain.Testnet -> Block.TestnetGenesisBlock
                    Chain.Regtest -> Block.RegtestGenesisBlock
                }

                val keyManager = LocalKeyManager(it.seed.toByteVector(), genesisBlock.hash)
                log.info { "nodeid=${keyManager.nodeId}" }

                val nodeParams = NodeParams(
                    keyManager = keyManager,
                    alias = "phoenix",
                    features = Features(
                        Feature.OptionDataLossProtect to FeatureSupport.Mandatory,
                        Feature.VariableLengthOnion to FeatureSupport.Optional,
                        Feature.PaymentSecret to FeatureSupport.Optional,
                        Feature.BasicMultiPartPayment to FeatureSupport.Optional,
                        Feature.Wumbo to FeatureSupport.Optional,
                        Feature.StaticRemoteKey to FeatureSupport.Optional,
                        Feature.TrampolinePayment to FeatureSupport.Optional,
                        Feature.AnchorOutputs to FeatureSupport.Optional,
                    ),
                    dustLimit = 546.sat,
                    maxRemoteDustLimit = 600.sat,
                    onChainFeeConf = OnChainFeeConf(
                        closeOnOfflineMismatch = true,
                        updateFeeMinDiffRatio = 0.1,
                        feerateTolerance = FeerateTolerance(ratioLow = 0.01, ratioHigh = 100.0)
                    ),
                    maxHtlcValueInFlightMsat = 150000000L,
                    maxAcceptedHtlcs = 30,
                    expiryDeltaBlocks = CltvExpiryDelta(144),
                    fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
                    checkHtlcTimeoutAfterStartupDelaySeconds = 15,
                    htlcMinimum = 1000.msat,
                    minDepthBlocks = 3,
                    toRemoteDelayBlocks = CltvExpiryDelta(144),
                    maxToLocalDelayBlocks = CltvExpiryDelta(1000),
                    feeBase = 1000.msat,
                    feeProportionalMillionth = 10,
                    reserveToFundingRatio = 0.01, // note: not used (overridden below)
                    maxReserveToFundingRatio = 0.05,
                    revocationTimeoutSeconds = 20,
                    authTimeoutSeconds = 10,
                    initTimeoutSeconds = 10,
                    pingIntervalSeconds = 30,
                    pingTimeoutSeconds = 10,
                    pingDisconnect = true,
                    autoReconnect = false,
                    initialRandomReconnectDelaySeconds = 5,
                    maxReconnectIntervalSeconds = 3600,
                    chainHash = genesisBlock.hash,
                    channelFlags = 1,
                    paymentRequestExpirySeconds = 3600,
                    multiPartPaymentExpirySeconds = 60,
                    minFundingSatoshis = 1000.sat,
                    maxFundingSatoshis = 16777215.sat,
                    maxPaymentAttempts = 5,
                    enableTrampolinePayment = true,
                )

                _nodeParams.value = nodeParams

                val nodeIdHash = nodeParams.nodeId.hash160().toHexString()
                val channelsDb = SqliteChannelsDb(createChannelsDbDriver(ctx, chain, nodeIdHash), nodeParams.copy())
                val paymentsDb = SqlitePaymentsDb(createPaymentsDbDriver(ctx, chain, nodeIdHash))
                log.debug { "databases object created" }
                _databases.value = object : Databases {
                    override val channels: ChannelsDb get() = channelsDb
                    override val payments: PaymentsDb get() = paymentsDb
                }
            }
        }
    }
}