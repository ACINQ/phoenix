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

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.fee.FeerateTolerance
import fr.acinq.lightning.blockchain.fee.OnChainFeeConf
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.kodein.log.LoggerFactory

object TestConstants {
    object Bob {
        private val entropy = Hex.decode("0202020202020202020202020202020202020202020202020202020202020202")
        val mnemonics = MnemonicCode.toMnemonics(entropy)
        val seed = MnemonicCode.toSeed(mnemonics, "").toByteVector32()
        val keyManager = LocalKeyManager(seed, Block.RegtestGenesisBlock.hash)
        val nodeParams = NodeParams(
            loggerFactory = LoggerFactory.default,
            keyManager = keyManager,
            alias = "bob",
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
            dustLimit = 1_000.sat,
            maxRemoteDustLimit = 1_500.sat,
            onChainFeeConf = OnChainFeeConf(
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1,
                feerateTolerance = FeerateTolerance(ratioLow = 0.5, ratioHigh = 5.0)
            ),
            maxHtlcValueInFlightMsat = 1_500_000_000L,
            maxAcceptedHtlcs = 100,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            checkHtlcTimeoutAfterStartupDelaySeconds = 15,
            htlcMinimum = 1_000.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1024),
            feeBase = 10.msat,
            feeProportionalMillionth = 10,
            revocationTimeoutSeconds = 20,
            authTimeoutSeconds = 10,
            initTimeoutSeconds = 10,
            pingIntervalSeconds = 30,
            pingTimeoutSeconds = 10,
            pingDisconnect = true,
            autoReconnect = false,
            initialRandomReconnectDelaySeconds = 5,
            maxReconnectIntervalSeconds = 3600,
            chainHash = Block.RegtestGenesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpirySeconds = 3600,
            multiPartPaymentExpirySeconds = 60,
            maxPaymentAttempts = 5,
            enableTrampolinePayment = true,
            zeroConfPeers = setOf(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134")),
            paymentRecipientExpiryParams = RecipientCltvExpiryParams(CltvExpiryDelta(75), CltvExpiryDelta(200))
        )
    }
}

val rawWalletParams = """
{
  "testnet": {
    "version": 28,
    "latest_critical_version": 0,
    "trampoline": {
      "v1": {
        "fee_base_sat": 2,
        "fee_percent": 0.001,
        "hops_count": 5,
        "cltv_expiry": 143
      },
      "v2": {
        "attempts": [{
            "fee_base_sat": 1,
            "fee_percent": 0.0005,
            "fee_per_millionths": 500,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 7,
            "fee_percent": 0.001,
            "fee_per_millionths": 1000,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 10,
            "fee_percent": 0.0012,
            "fee_per_millionths": 1200,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 12,
            "fee_percent": 0.005,
            "fee_per_millionths": 5000,
            "cltv_expiry": 576
          }
        ],
        "nodes": [{
          "name": "endurance",
          "uri": "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@13.248.222.197:9735"
        }]
      }
    },
    "pay_to_open": {
      "v1": {
        "min_funding_sat": 10000,
        "min_fee_sat": 3000,
        "fee_percent": 0.01,
        "status": 0
      }
    },
    "swap_in": {
      "v1": {
        "min_funding_sat": 10000,
        "min_fee_sat": 3000,
        "fee_percent": 0.01,
        "status": 0
      }
    },
    "swap_out": {
      "v1": {
        "min_feerate_sat_byte": 0,
        "min_amount_sat": 10000,
        "max_amount_sat": 2000000,
        "status": 0
      }
    },
    "mempool": {
      "v1": {
        "high_usage": false
      }
    }
  },
  "mainnet": {
    "version": 28,
    "latest_critical_version": 0,
    "trampoline": {
      "v1": {
        "fee_base_sat": 2,
        "fee_percent": 0.001,
        "hops_count": 5,
        "cltv_expiry": 143
      },
      "v2": {
        "attempts": [{
            "fee_base_sat": 1,
            "fee_percent": 0.0005,
            "fee_per_millionths": 500,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 7,
            "fee_percent": 0.001,
            "fee_per_millionths": 1000,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 10,
            "fee_percent": 0.0012,
            "fee_per_millionths": 1200,
            "cltv_expiry": 576
          }, {
            "fee_base_sat": 12,
            "fee_percent": 0.005,
            "fee_per_millionths": 5000,
            "cltv_expiry": 576
          }
        ],
        "nodes": [{
          "name": "ACINQ",
          "uri": "03864ef025fde8fb587d989186ce6a4a186895ee44a926bfc370e2c366597a3f8f@3.33.236.230:9735"
        }]
      }
    },
    "pay_to_open": {
      "v1": {
        "min_funding_sat": 10000,
        "min_fee_sat": 3000,
        "fee_percent": 0.01,
        "status": 0
      }
    },
    "swap_in": {
      "v1": {
        "min_funding_sat": 10000,
        "min_fee_sat": 3000,
        "fee_percent": 0.01,
        "status": 0
      }
    },
    "swap_out": {
      "v1": {
        "min_feerate_sat_byte": 20,
        "min_amount_sat": 10000,
        "max_amount_sat": 2000000,
        "status": 0
      }
    },
    "mempool": {
      "v1": {
        "high_usage": false
      }
    }
  }
}
"""

fun runTest(timeout: Duration = 30.seconds, test: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        withTimeout(timeout) {
            launch {
                test()
                cancel()
            }.join()
        }
    }
}