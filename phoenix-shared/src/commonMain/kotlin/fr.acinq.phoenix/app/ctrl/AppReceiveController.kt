package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.secure
import fr.acinq.lightning.wire.SwapInRequest
import fr.acinq.lightning.wire.SwapInResponse
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.kodein.log.LoggerFactory
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(loggerFactory: LoggerFactory, private val chain: Chain, private val peerManager: PeerManager) : AppController<Receive.Model, Receive.Intent>(loggerFactory, Receive.Model.Awaiting) {

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
                                expirySeconds = 3600 * 24 * 7, // one week
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
