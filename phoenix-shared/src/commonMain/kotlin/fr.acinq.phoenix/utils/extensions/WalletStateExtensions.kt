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

package fr.acinq.phoenix.utils.extensions

import fr.acinq.lightning.SwapInParams
import fr.acinq.lightning.blockchain.electrum.WalletState
import kotlin.math.ceil

/** Number of confirmation during which a utxo is locked between the swap window closing and the refund delay. */
val SwapInParams.gracePeriod: Int
    get() = refundDelay - maxConfirmations
val SwapInParams.gracePeriodInDays: Int
    get() = ceil( gracePeriod.toDouble() / 144).toInt()
val SwapInParams.maxConfirmationsInDays: Int
    get() = ceil(maxConfirmations.toDouble() / 144).toInt()
val SwapInParams.refundDelayInDays: Int
    get() = ceil(refundDelay.toDouble() / 144).toInt()

/** Returns the block count after which a utxo will NOT be swappable anymore, according to the wallet's swap params. */
fun WalletState.WalletWithConfirmations.timeoutIn(utxo: WalletState.Utxo): Int {
    return (swapInParams.maxConfirmations - confirmations(utxo)).coerceAtLeast(0)
}

/** A map of deeply confirmed utxos to their expiry, according to the wallet's swap params. */
val WalletState.WalletWithConfirmations.deeplyConfirmedToExpiry: List<Pair<WalletState.Utxo, Int>>
    get() = deeplyConfirmed.map { it to timeoutIn(it) }

/** A map of deeply confirmed utxos to their expiry, according to the wallet's swap params. */
val WalletState.WalletWithConfirmations.nextTimeout: Pair<WalletState.Utxo, Int>?
    get() = deeplyConfirmedToExpiry.minByOrNull { it.second }