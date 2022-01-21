package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.secure
import fr.acinq.lightning.wire.SwapInRequest
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.kodein.log.LoggerFactory
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(
    loggerFactory: LoggerFactory,
    private val chain: Chain,
    private val peerManager: PeerManager
) : AppController<Receive.Model, Receive.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Receive.Model.Awaiting
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        peerManager = business.peerManager
    )

    private val Receive.Intent.Ask.description: String get() = desc?.takeIf { it.isNotBlank() } ?: ""

    init {
        launch {
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                if (event is SwapInResponseEvent && models.value is Receive.Model.SwapIn.Requesting) {
                    logger.debug { "received swap-in response=$event" }
                    model(Receive.Model.SwapIn.Generated(event.swapInResponse.bitcoinAddress))
                }
            }
        }
    }

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
                    model(Receive.Model.SwapIn.Requesting)
                    logger.info { "requesting swap-in" }
                    peerManager.getPeer().sendToPeer(SwapInRequest(chain.chainHash))
                }
            }
        }
    }

}
