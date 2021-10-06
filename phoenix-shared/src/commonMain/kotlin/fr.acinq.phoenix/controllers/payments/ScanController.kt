package fr.acinq.phoenix.controllers.payments

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
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.managers.*
import fr.acinq.phoenix.managers.Utilities.BitcoinAddressInfo
import fr.acinq.phoenix.utils.PublicSuffixList
import fr.acinq.phoenix.utils.localCommitmentSpec
import io.ktor.http.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.seconds

class AppScanController(
    loggerFactory: LoggerFactory,
    firstModel: Scan.Model?,
    private val peerManager: PeerManager,
    private val lnurlManager: LNUrlManager,
    private val databaseManager: DatabaseManager,
    private val appConfigManager: AppConfigurationManager,
    private val utilities: Utilities,
    private val chain: Chain
) : AppController<Scan.Model, Scan.Intent>(
    loggerFactory = loggerFactory,
    firstModel = firstModel ?: Scan.Model.Ready
) {
    private var prefetchPublicSuffixListTask: Deferred<Pair<String, Long>?>? = null

    constructor(business: PhoenixBusiness, firstModel: Scan.Model?): this(
        loggerFactory = business.loggerFactory,
        firstModel = firstModel,
        peerManager = business.peerManager,
        lnurlManager = business.lnUrlManager,
        databaseManager = business.databaseManager,
        appConfigManager = business.appConfigurationManager,
        utilities = business.util,
        chain = business.chain,
    )

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                val balanceMsat = balanceMsat(channels)
                model {
                    if (this is Scan.Model.ValidateRequest) {
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
                processParse(intent)
            }
            is Scan.Intent.ConfirmDangerousRequest -> launch {
                processConfirmDangerousRequest(intent)
            }
            is Scan.Intent.Send -> launch {
                processSend(intent)
            }
            is Scan.Intent.Login -> launch {
                processLogin(intent)
            }
        }
    }

    private suspend fun processParse(
        intent: Scan.Intent.Parse
    ) {
        val input = intent.request.replace("\\u00A0", "").trim() // \u00A0 = '\n'

        // Is it a Lightning invoice ?
        readPaymentRequest(input)?.let { return when (it) {
            is Either.Left -> { // it.value: Scan.BadRequestReason
                model(Scan.Model.BadRequest(it.value))
            }
            is Either.Right -> {
                val paymentRequest = it.value
                val isDangerousAmountless = if (paymentRequest.amount != null) {
                    false
                } else {
                    // amountless invoice -> dangerous unless full trampoline is in effect
                    val features = Features(paymentRequest.features)
                    !features.hasFeature(Feature.TrampolinePayment)
                }
                if (isDangerousAmountless) {
                    model(Scan.Model.DangerousRequest(
                        reason = Scan.DangerousRequestReason.IsAmountlessInvoice,
                        request = intent.request,
                        paymentRequest = paymentRequest
                    ))
                } else if (paymentRequest.nodeId == peerManager.getPeer().nodeParams.nodeId) {
                    model(Scan.Model.DangerousRequest(
                        reason = Scan.DangerousRequestReason.IsOwnInvoice,
                        request = intent.request,
                        paymentRequest = paymentRequest
                    ))
                } else {
                    model(makeValidateRequest(intent.request, paymentRequest))
                }
            }
        }}

        // Is it an LNURL ?
        readLNURL(input)?.let { return when (it) {
            is Either.Left -> { // it.value: LnUrl.Auth
                prefetchPublicSuffixListTask = appConfigManager.prefetchPublicSuffixList()
                model(Scan.Model.LoginRequest(auth = it.value))
            }
            is Either.Right -> { // it.value: Url
                model(Scan.Model.BadRequest(Scan.BadRequestReason.UnsupportedLnUrl))
            }
        }}

        // Is it a bitcoin address ?
        readBitcoinAddress(input).let { return when (it) {
            is Either.Left -> { // it.value: Scan.BadRequestReason
                model(Scan.Model.BadRequest(it.value))
            }
            is Either.Right -> {
                it.value.params.get("lightning")?.let { lnParam ->
                    processParse(Scan.Intent.Parse(lnParam))
                } ?: model(Scan.Model.BadRequest(Scan.BadRequestReason.IsBitcoinAddress))
            }
        }}
    }

    private suspend fun processConfirmDangerousRequest(
        intent: Scan.Intent.ConfirmDangerousRequest
    ) {
        model(makeValidateRequest(intent.request, intent.paymentRequest))
    }

    private suspend fun processSend(
        intent: Scan.Intent.Send
    ) {
        val paymentRequest = intent.paymentRequest
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

    @OptIn(ExperimentalTime::class)
    private suspend fun processLogin(
        intent: Scan.Intent.Login
    ) {
        model(Scan.Model.LoggingIn(auth = intent.auth))
        val start = TimeSource.Monotonic.markNow()
        val psl = prefetchPublicSuffixListTask?.await()
        if (psl == null) {
            model(Scan.Model.LoginResult(
                auth = intent.auth,
                error = Scan.LoginError.OtherError(details = LNUrl.Error.MissingPublicSuffixList)
            ))
            return
        }
        val error = try {
            lnurlManager.requestAuth(
                auth = intent.auth,
                publicSuffixList = PublicSuffixList(psl.first)
            )
            null
        } catch (e: LNUrl.Error.RemoteFailure.CouldNotConnect) {
            Scan.LoginError.NetworkError(details = e)
        } catch (e: LNUrl.Error.RemoteFailure) {
            Scan.LoginError.ServerError(details = e)
        } catch (e: Throwable) {
            Scan.LoginError.OtherError(details = e)
        }
        if (error != null) {
            model(Scan.Model.LoginResult(auth = intent.auth, error = error))
        } else {
            val pending = intent.minSuccessDelaySeconds.seconds - start.elapsedNow()
            if (pending > Duration.ZERO) {
                delay(pending)
            }
            model(Scan.Model.LoginResult(auth = intent.auth, error = error))
        }
    }

    private suspend fun makeValidateRequest(
        request: String,
        paymentRequest: PaymentRequest
    ): Scan.Model.ValidateRequest {
        val balanceMsat = balanceMsat(peerManager.getPeer().channels)
        val expiryTimestamp = paymentRequest.expirySeconds?.let {
            paymentRequest.timestampSeconds + it
        }
        return Scan.Model.ValidateRequest(
            request = request,
            paymentRequest = paymentRequest,
            amountMsat = paymentRequest.amount?.toLong(),
            expiryTimestamp = expiryTimestamp,
            requestDescription = paymentRequest.description,
            balanceMsat = balanceMsat
        )
    }

    private fun trimMatchingPrefix(
        input: String,
        prefixes: List<String>
    ): Pair<Boolean, String> {

        // The trimming is done in a case-insenstive manner.
        // Because often QR codes will use upper-case, such as:
        // LIGHTNING:LNURL1...

        val inputLowerCase = input.lowercase()
        for (prefix in prefixes) {
            if (inputLowerCase.startsWith(prefix.lowercase())) {
                return Pair(true, input.drop(prefix.length))
            }
        }
        return Pair(false, input)
    }

    private suspend fun readPaymentRequest(
        input: String
    ) : Either<Scan.BadRequestReason, PaymentRequest>? {

        val (_, request) = trimMatchingPrefix(input, listOf(
            "lightning://", "lightning:", "bitcoin://", "bitcoin:"
        ))

        val paymentRequest = try {
            PaymentRequest.read(request) // <- throws
        } catch (t: Throwable) {
            return null
        }

        val requestChain = when (paymentRequest.prefix) {
            "lnbc" -> Chain.Mainnet
            "lntb" -> Chain.Testnet
            "lnbcrt" -> Chain.Regtest
            else -> null
        }
        if (chain != requestChain) {
            return Either.Left(Scan.BadRequestReason.ChainMismatch(chain, requestChain))
        }

        val db = databaseManager.databases.filterNotNull().first()
        val previousInvoicePayment = db.payments.listOutgoingPayments(paymentRequest.paymentHash).find {
            it.status is OutgoingPayment.Status.Completed.Succeeded
        }
        return if (previousInvoicePayment != null) {
            Either.Left(Scan.BadRequestReason.AlreadyPaidInvoice)
        } else {
            Either.Right(paymentRequest)
        }
    }

    private fun readLNURL(input: String): Either<LNUrl.Auth, Url>? {

        val (_, request) = trimMatchingPrefix(input, listOf(
            "lightning://", "lightning:"
        ))

        return try {
            lnurlManager.interactiveExtractLNUrl(request)
        } catch (t: Throwable) {
            return null
        }
    }

    private fun readBitcoinAddress(
        input: String
    ): Either<Scan.BadRequestReason, BitcoinAddressInfo> {

        return when (val result = utilities.parseBitcoinAddress(input)) {
            is Either.Left -> {
                when (val reason = result.value) {
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
                // Yup, it's a bitcoin address
                Either.Right(result.value)
            }
        }
    }
}
