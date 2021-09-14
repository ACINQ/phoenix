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

package fr.acinq.phoenix.utils

import java.lang.IllegalArgumentException

// -- startup exception
object NoSeedYet : Exception()
class InvalidElectrumAddress(val address: String) : Exception(address)
class TorSetupException(val s: String) : Exception(s)
class NetworkException : RuntimeException()
object KitNotInitialized : RuntimeException("kit is not ready yet")
object ServiceDisconnected : RuntimeException("node service is disconnected")

// -- Channels errors
class ChannelsNotClosed(channelsNotClosedCount: Int) : RuntimeException()

// -- payment exceptions
sealed class AmountError: RuntimeException() {
  object Default : AmountError()
  object NotEnoughBalance : AmountError()
  object SwapOutBelowMin : AmountError()
  object SwapOutAboveMax : AmountError()
}
object CannotSendHeadless : RuntimeException("the service cannot send a payment while being headless")

// -- parsing exceptions
class UnreadableLightningObject(message: String): IllegalArgumentException(message)
class BitcoinURIParseException : Exception {
  constructor(s: String) : super(s)

  constructor(s: String, throwable: Throwable) : super(s, throwable)
}
