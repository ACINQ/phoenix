package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.Feature
import fr.acinq.lightning.Features
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.SendPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.ctrl.Scan
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.toMilliSatoshi
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppScanController(
    loggerFactory: LoggerFactory,
    firstModel: Scan.Model?,
    private val peerManager: PeerManager,
    private val utilities: Utilities,
    private val chain: Chain
) : AppController<Scan.Model, Scan.Intent>(
    loggerFactory,
    firstModel = firstModel ?: Scan.Model.Ready
) {

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                val balanceMsat = balanceMsat(channels)
                model {
                    if (this is Scan.Model.Validate) {
                        this.copy(balanceMsat = balanceMsat)
                    } else {
                        this
                    }
                }
            }
        }
    }

    private fun balanceMsat(channels: Map<ByteVector32, ChannelState>): Long {
        return channels.values.map {
            when (it) {
                is Closing -> MilliSatoshi(0)
                is Closed -> MilliSatoshi(0)
                is Aborted -> MilliSatoshi(0)
                is ErrorInformationLeak -> MilliSatoshi(0)
                else -> it.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
            }
        }.sum().toLong()
    }

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> launch {
                when (val result = readPaymentRequest(intent.request)) {
                    is Either.Left -> { // result.value: Scan.BadRequestReason
                        model(Scan.Model.BadRequest(result.value))
                    }
                    is Either.Right -> {
                        val paymentRequest: PaymentRequest = result.value
                        var isDangerous = if (paymentRequest.amount != null) {
                            false
                        } else {
                            // amountless invoice -> dangerous unless full trampoline is in effect
                            val features = Features(paymentRequest.features ?: ByteVector.empty)
                            !features.hasFeature(Feature.TrampolinePayment)
                        }
                        if (isDangerous)
                            model(Scan.Model.DangerousRequest(intent.request))
                        else
                            validatePaymentRequest(intent.request, paymentRequest)
                    }
                }
            }
            is Scan.Intent.ConfirmDangerousRequest -> launch {
                when (val result = readPaymentRequest(intent.request)) {
                    is Either.Left -> { // result.value: Scan.BadRequestReason
                        model(Scan.Model.BadRequest(result.value))
                    }
                    is Either.Right -> { // result.value: PaymentRequest
                        validatePaymentRequest(intent.request, result.value)
                    }
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    when (val result = readPaymentRequest((intent.request))) {
                        is Either.Left -> { // result.value: Scan.BadRequestReason
                            model(Scan.Model.BadRequest(result.value))
                        }
                        is Either.Right -> {
                            val paymentRequest: PaymentRequest = result.value
                            val paymentId = UUID.randomUUID()
                            peerManager.getPeer().send(
                                SendPayment(
                                    paymentId = paymentId,
                                    amount = intent.amount,
                                    recipient = paymentRequest.nodeId,
                                    details = OutgoingPayment.Details.Normal(paymentRequest)
                                )
                            )
                            model(Scan.Model.Sending)
                        }
                    }
                }
            }
        }
    }

    private suspend fun readPaymentRequest(
        input: String
    ) : Either<Scan.BadRequestReason, PaymentRequest> {

        var request = input.replace("\\u00A0", "").trim() // \u00A0 = '\n'
        request = when {
            request.startsWith("lightning://", true) -> request.drop(12)
            request.startsWith("lightning:", true) -> request.drop(10)
            request.startsWith("bitcoin://", true) -> request.drop(10)
            request.startsWith("bitcoin:", true) -> request.drop(8)
            else -> request
        }

        try {
            val paymentRequest = PaymentRequest.read(request) // <- throws
            val requestChain = when (paymentRequest.prefix) {
                "lnbc" -> Chain.Mainnet
                "lntb" -> Chain.Testnet
                "lnbcrt" -> Chain.Regtest
                else -> null
            }
            return if (chain != requestChain) {
                Either.Left(Scan.BadRequestReason.ChainMismatch(chain, requestChain))
            } else {
                Either.Right(paymentRequest)
            }
        } catch (t: Throwable) {
            // The qrcode doesn't appear to be for a lightning invoice.
            // Is it a LNURL ?
            val isLnUrl = when {
                input.startsWith("lightning://lnurl1", true) -> true
                input.startsWith("lightning:lnurl1", true) -> true
                input.startsWith("lnurl1", true) -> true
                else -> false
            }
            if (isLnUrl) {
                return Either.Left(Scan.BadRequestReason.isLnUrl)
            }
            // Is it for a bitcoin address ?
            return when (val result = utilities.parseBitcoinAddress(input)) {
                is Either.Left -> {
                    val reason: Utilities.BitcoinAddressError = result.value
                    when (reason) {
                        is Utilities.BitcoinAddressError.ChainMismatch -> {
                            // Two problems here:
                            // - they're scanning a bitcoin address, but we don't support swap-out yet
                            // - the bitcoin address is for the wrong chain
                            Either.Left(Scan.BadRequestReason.ChainMismatch(
                                myChain = reason.myChain,
                                requestChain = reason.addrChain
                            ))
                        }
                        else -> {
                            Either.Left(Scan.BadRequestReason.UnknownFormat)
                        }
                    }
                }
                is Either.Right -> {
                    // Yup, it's a bitcoin address.
                    // But we don't support swap-out yet.
                    Either.Left(Scan.BadRequestReason.IsBitcoinAddress)
                }
            }
        }
    }

    private suspend fun validatePaymentRequest(request: String, paymentRequest: PaymentRequest) {
        val balanceMsat = balanceMsat(peerManager.getPeer().channels)
        val expiryTimestamp = paymentRequest.expirySeconds?.let {
            paymentRequest.timestampSeconds + it
        }
        model(
            Scan.Model.Validate(
                request = request,
                amountMsat = paymentRequest.amount?.toLong(),
                expiryTimestamp = expiryTimestamp,
                requestDescription = paymentRequest.description,
                balanceMsat = balanceMsat
            )
        )
    }
}
