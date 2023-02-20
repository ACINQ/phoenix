/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.secure
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.*
import org.kodein.log.LoggerFactory
import kotlin.random.Random


class AppReceiveController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
) : AppController<Receive.Model, Receive.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Receive.Model.Awaiting
) {
    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
    )

    private val Receive.Intent.Ask.description: String get() = desc?.takeIf { it.isNotBlank() } ?: ""

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model(Receive.Model.Generating)
                    try {
                        val deferred = CompletableDeferred<PaymentRequest>()
                        val preimage = ByteVector32(Random.secure().nextBytes(32)) // must be different everytime
                        peerManager.getPeer().send(
                            ReceivePayment(
                                paymentPreimage = preimage,
                                amount = intent.amount,
                                description = Either.Left(intent.description),
                                expirySeconds = intent.expirySeconds,
                                result = deferred
                            )
                        )
                        val request = deferred.await()
                        check(request.amount == intent.amount) { "payment request amount=${request.amount} does not match expected amount=${intent.amount}" }
                        check(request.description == intent.description) { "payment request amount=${request.description} does not match expected amount=${intent.description}" }
                        val paymentHash: String = request.paymentHash.toHex()
                        model(Receive.Model.Generated(request.write(), paymentHash, request.amount, request.description))
                    } catch (e: Throwable) {
                        logger.error(e) { "failed to process intent=$intent" }
                    }
                }
            }
            Receive.Intent.RequestSwapIn -> {
                launch {
                    model(Receive.Model.SwapIn(peerManager.getPeer().swapInAddress))
                }
            }
        }
    }

}
