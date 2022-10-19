package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.secure
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.*
import org.kodein.log.LoggerFactory
import kotlin.random.Random


class AppReceiveController(
    loggerFactory: LoggerFactory,
    private val chain: Chain,
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
) : AppController<Receive.Model, Receive.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Receive.Model.Awaiting
) {
    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        peerManager = business.peerManager,
        walletManager = business.walletManager
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
                                description = intent.description,
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
                    model(Receive.Model.SwapIn.Generated(peerManager.getPeer().swapInAddress))
                }
            }
        }
    }

}
