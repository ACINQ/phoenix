/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.legacy.background

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Transaction
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.legacy.Balance
import fr.acinq.phoenix.legacy.db.ClosingType

class PaymentPending

class BalanceEvent(val balance: Balance)

class ChannelClosingEvent(val balance: MilliSatoshi, val channelId: ByteVector32, val closingType: ClosingType, val spendingTxs: List<Transaction>, val scriptDestMainOutput: String?)

class ChannelStateChange

object PeerConnectionChange

class RemovePendingSwapIn(val address: String)

data class FCMToken(val token: String)
