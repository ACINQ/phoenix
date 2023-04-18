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
import fr.acinq.lightning.blockchain.fee.FeerateTolerance
import fr.acinq.lightning.blockchain.fee.OnChainFeeConf
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class NodeParamsManager(
    loggerFactory: LoggerFactory,
    chain: Chain,
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
                    loggerFactory = loggerFactory,
                    keyManager = keyManager,
                    alias = "phoenix",
                    features = Features(
                        Feature.InitialRoutingSync to FeatureSupport.Optional,
                        Feature.OptionDataLossProtect to FeatureSupport.Optional,
                        Feature.VariableLengthOnion to FeatureSupport.Mandatory,
                        Feature.PaymentSecret to FeatureSupport.Mandatory,
                        Feature.BasicMultiPartPayment to FeatureSupport.Optional,
                        Feature.Wumbo to FeatureSupport.Optional,
                        Feature.StaticRemoteKey to FeatureSupport.Optional,
                        Feature.AnchorOutputs to FeatureSupport.Optional,
                        Feature.ShutdownAnySegwit to FeatureSupport.Optional,
                        Feature.ChannelType to FeatureSupport.Mandatory,
                        Feature.PaymentMetadata to FeatureSupport.Optional,
                        Feature.ExperimentalTrampolinePayment to FeatureSupport.Optional,
                        Feature.ZeroReserveChannels to FeatureSupport.Optional,
                        Feature.WakeUpNotificationClient to FeatureSupport.Optional,
                        Feature.PayToOpenClient to FeatureSupport.Optional,
                        Feature.ChannelBackupClient to FeatureSupport.Optional,
                        Feature.DualFunding to FeatureSupport.Mandatory,
                    ),
                    dustLimit = 546.sat,
                    maxRemoteDustLimit = 600.sat,
                    onChainFeeConf = OnChainFeeConf(
                        closeOnOfflineMismatch = true,
                        updateFeeMinDiffRatio = 0.1,
                        feerateTolerance = FeerateTolerance(ratioLow = 0.01, ratioHigh = 100.0)
                    ),
                    maxHtlcValueInFlightMsat = 20000000000L,
                    maxAcceptedHtlcs = 6,
                    expiryDeltaBlocks = CltvExpiryDelta(144),
                    fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
                    checkHtlcTimeoutAfterStartupDelaySeconds = 15,
                    htlcMinimum = 1000.msat,
                    minDepthBlocks = 3,
                    toRemoteDelayBlocks = CltvExpiryDelta(2016),
                    maxToLocalDelayBlocks = CltvExpiryDelta(1008),
                    feeBase = 1000.msat,
                    feeProportionalMillionth = 100,
                    revocationTimeoutSeconds = 20,
                    authTimeoutSeconds = 10,
                    initTimeoutSeconds = 10,
                    pingIntervalSeconds = 30,
                    pingTimeoutSeconds = 10,
                    pingDisconnect = true,
                    autoReconnect = false,
                    initialRandomReconnectDelaySeconds = 5,
                    maxReconnectIntervalSeconds = 3600,
                    chainHash = chain.chainHash,
                    channelFlags = 1,
                    paymentRequestExpirySeconds = 3600,
                    multiPartPaymentExpirySeconds = 60,
                    maxPaymentAttempts = 5,
                    enableTrampolinePayment = true,
                    zeroConfPeers = setOf(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134")),
                    paymentRecipientExpiryParams = RecipientCltvExpiryParams(CltvExpiryDelta(75), CltvExpiryDelta(200)),
                )

                _nodeParams.value = nodeParams
            }
        }
    }
}