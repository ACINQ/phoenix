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

package fr.acinq.phoenix.events

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.wire.PayToOpenRequest

class PaymentPending

class BalanceEvent(val balance: MilliSatoshi)

class ChannelClosingEvent(val balance: MilliSatoshi, val channelId: ByteVector32)

class ChannelStateChange

object PeerConnectionChange

class RemovePendingSwapIn(val address: String)

class PayToOpenNavigationEvent(val payToOpen: PayToOpenRequest)
