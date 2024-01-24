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

import co.touchlab.kermit.Logger
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.utils.Either
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.launch


class AppReceiveController(
    loggerFactory: Logger,
    private val peerManager: PeerManager,
) : AppController<Receive.Model, Receive.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Receive.Model.Awaiting
) {
    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.newLoggerFactory,
        peerManager = business.peerManager,
    )

    private val Receive.Intent.Ask.description: String get() = desc?.takeIf { it.isNotBlank() } ?: ""

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model(Receive.Model.Generating)
                    val paymentRequest = peerManager.getPeer().createInvoice(
                        paymentPreimage = randomBytes32(),
                        amount = intent.amount,
                        description = Either.Left(intent.description),
                        expirySeconds = intent.expirySeconds
                    )
                    model(Receive.Model.Generated(paymentRequest.write(), paymentRequest.paymentHash.toHex(), paymentRequest.amount, paymentRequest.description))
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
