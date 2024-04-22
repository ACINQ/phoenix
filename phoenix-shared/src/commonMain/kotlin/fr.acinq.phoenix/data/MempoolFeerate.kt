/*
 * Copyright 2023 ACINQ SAS
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

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.transactions.Transactions
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.managers.NodeParamsManager


/** Inspired from https://mempool.space/api/v1/fees/recommended */
data class MempoolFeerate(
    val fastest: FeeratePerByte,
    val halfHour: FeeratePerByte,
    val hour: FeeratePerByte,
    val economy: FeeratePerByte,
    val minimum: FeeratePerByte,
    val timestamp: Long,
) {
    /**
     * Estimates roughly the cost of a dual-funded splice, using the current feerate and an arbitrary tx weight.
     *
     * An additional service fee is expected if there's no channels already.
     */
    fun swapEstimationFee(hasNoChannels: Boolean): Satoshi {
        return Transactions.weight2fee(feerate = FeeratePerKw(hour), weight = DualFundingPayToSpliceWeight) + if (hasNoChannels) 1000.sat else 0.sat
    }

    fun payToOpenEstimationFee(amount: MilliSatoshi, hasNoChannels: Boolean): Satoshi {
        return swapEstimationFee(hasNoChannels) + (amount * NodeParamsManager.payToOpenFeeBase / 10_000).truncateToSatoshi()
    }

    companion object {
        /** Spending a channel output and adding funds from a wpkh wallet with one change output: 2-inputs (wpkh+wsh)/2-outputs (wpkh+wsh) */
        const val DualFundingPayToSpliceWeight = 992
    }
}
