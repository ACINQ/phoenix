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
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.managers.*
import fr.acinq.phoenix.managers.Utilities.BitcoinAddressInfo
import fr.acinq.phoenix.utils.PublicSuffixList
import fr.acinq.phoenix.utils.chain
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
    private var lnurlRequestId = 1

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
                val balance = calculateBalance(channels)
                model { when (this) {
                    is Scan.Model.InvoiceFlow.InvoiceRequest -> {
                        this.copy(balanceMsat = balance.msat)
                    }
                    is Scan.Model.LnurlPayFlow.LnurlPayRequest -> {
                        this.copy(balanceMsat = balance.msat)
                    }
                    is Scan.Model.LnurlPayFlow.LnUrlPayFetch -> {
                        this.copy(balanceMsat = balance.msat)
                    }
                    else -> this
                }}
            }
        }
    }

    private fun calculateBalance(channels: Map<ByteVector32, ChannelState>): MilliSatoshi {
        return channels.values.map {
            when (it) {
                is Closing -> MilliSatoshi(0)
                is Closed -> MilliSatoshi(0)
                is Aborted -> MilliSatoshi(0)
                is ErrorInformationLeak -> MilliSatoshi(0)
                else -> it.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
            }
        }.sum()
    }

    private suspend fun getBalance(): MilliSatoshi {
        return calculateBalance(peerManager.getPeer().channels)
    }

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> launch {
                processIntent(intent)
            }
            is Scan.Intent.InvoiceFlow.ConfirmDangerousRequest -> launch {
                processIntent(intent)
            }
            is Scan.Intent.InvoiceFlow.SendInvoicePayment -> launch {
                processIntent(intent)
            }
            is Scan.Intent.CancelLnurlServiceFetch -> launch {
                processIntent(intent)
            }
            is Scan.Intent.LnurlPayFlow.SendLnurlPayment -> launch {
                processIntent(intent)
            }
            is Scan.Intent.LnurlPayFlow.CancelLnurlPayment -> launch {
                processIntent(intent)
            }
            is Scan.Intent.LnurlAuthFlow.Login -> launch {
                processIntent(intent)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processIntent(
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
                when {
                    isDangerousAmountless -> {
                        model(Scan.Model.InvoiceFlow.DangerousRequest(
                            reason = Scan.DangerousRequestReason.IsAmountlessInvoice,
                            request = intent.request,
                            paymentRequest = paymentRequest
                        ))
                    }
                    paymentRequest.nodeId == peerManager.getPeer().nodeParams.nodeId -> {
                        model(Scan.Model.InvoiceFlow.DangerousRequest(
                            reason = Scan.DangerousRequestReason.IsOwnInvoice,
                            request = intent.request,
                            paymentRequest = paymentRequest
                        ))
                    }
                    else -> {
                        val balance = getBalance()
                        model(Scan.Model.InvoiceFlow.InvoiceRequest(
                            request = intent.request,
                            paymentRequest = paymentRequest,
                            balanceMsat = balance.msat
                        ))
                    }
                }
            }
        }}

        // Is it an LNURL ?
        readLNURL(input)?.let { return when (it) {
            is Either.Left -> { // it.value: LnUrl.Auth
                prefetchPublicSuffixListTask = appConfigManager.fetchPublicSuffixListAsync()
                model(Scan.Model.LnurlAuthFlow.LoginRequest(auth = it.value))
            }
            is Either.Right -> { // it.value: Url
                val requestId = lnurlRequestId
                model(Scan.Model.LnurlServiceFetch)
                val result: Either<Scan.BadRequestReason, LNUrl> = try {
                    val lnurl = lnurlManager.continueLnUrl(it.value)
                    Either.Right(lnurl)
                } catch (t: Throwable) {
                    Either.Left(Scan.BadRequestReason.InvalidLnUrl)
                }
                if (requestId != lnurlRequestId) {
                    // Intent.CancelLnurlServiceFetch has been issued
                    return
                }
                when (result) {
                    is Either.Left -> {
                        model(Scan.Model.BadRequest(result.value))
                    }
                    is Either.Right -> {
                        when (val lnurl = result.value) {
                            is LNUrl.Pay -> {
                                val balance = getBalance()
                                model(Scan.Model.LnurlPayFlow.LnurlPayRequest(
                                    lnurlPay = lnurl,
                                    balanceMsat = balance.msat,
                                    error = null
                                ))
                            }
                            else -> {
                                model(Scan.Model.BadRequest(Scan.BadRequestReason.UnsupportedLnUrl))
                            }
                        }
                    }
                }
            }
        }}

        // Is it a bitcoin address ?
        readBitcoinAddress(input).let { return when (it) {
            is Either.Left -> { // it.value: Scan.BadRequestReason
                model(Scan.Model.BadRequest(it.value))
            }
            is Either.Right -> {
                it.value.params.get("lightning")?.let { lnParam ->
                    processIntent(Scan.Intent.Parse(lnParam))
                } ?: model(Scan.Model.BadRequest(Scan.BadRequestReason.IsBitcoinAddress))
            }
        }}
    }

    private suspend fun processIntent(
        intent: Scan.Intent.InvoiceFlow.ConfirmDangerousRequest
    ) {
        val balance = getBalance()
        model(Scan.Model.InvoiceFlow.InvoiceRequest(
            request = intent.request,
            paymentRequest = intent.paymentRequest,
            balanceMsat = balance.msat
        ))
    }

    private suspend fun processIntent(
        intent: Scan.Intent.InvoiceFlow.SendInvoicePayment
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
        model(Scan.Model.InvoiceFlow.Sending)
    }

    private suspend fun processIntent(
        @Suppress("UNUSED_PARAMETER")
        intent: Scan.Intent.CancelLnurlServiceFetch
    ) {
        lnurlRequestId += 1
        model(Scan.Model.Ready)
    }

    private suspend fun processIntent(
        intent: Scan.Intent.LnurlPayFlow.SendLnurlPayment
    ) {
        run { // scoping
            val balance = getBalance()
            model(Scan.Model.LnurlPayFlow.LnUrlPayFetch(
                lnurlPay = intent.lnurlPay,
                balanceMsat = balance.msat
            ))
        }

        val requestId = lnurlRequestId
        val result: Either<Scan.LNUrlPayError, LNUrl.PayInvoice> = try {
            val invoice = lnurlManager.requestPayInvoice(
                lnurlPay = intent.lnurlPay,
                amount = intent.amount,
                comment = intent.comment
            )

            val requestChain = invoice.paymentRequest.chain()
            if (chain != requestChain) {
                Either.Left(Scan.LNUrlPayError.ChainMismatch(chain, requestChain))
            } else {
                val db = databaseManager.databases.filterNotNull().first()
                val previousPayment = db.payments.listOutgoingPayments(
                    paymentHash = invoice.paymentRequest.paymentHash
                ).find {
                    it.status is OutgoingPayment.Status.Completed.Succeeded
                }
                if (previousPayment != null) {
                    Either.Left(Scan.LNUrlPayError.AlreadyPaidInvoice)
                } else {
                    Either.Right(invoice)
                }
            }
        } catch (err: Throwable) { when (err) {
            is LNUrl.Error.RemoteFailure -> {
                Either.Left(Scan.LNUrlPayError.RemoteError(err))
            }
            is LNUrl.Error.PayInvoice -> {
                Either.Left(Scan.LNUrlPayError.BadResponseError(err))
            }
            else -> { // unexpected exception: map to generic error
                Either.Left(Scan.LNUrlPayError.RemoteError(
                    LNUrl.Error.RemoteFailure.Unreadable(
                        origin = intent.lnurlPay.callback.host
                    )
                ))
            }
        }}
        if (requestId != lnurlRequestId) {
            // Intent.LnurlPayFlow.CancelLnurlPayment has been issued
            return
        }
        when (result) {
            is Either.Left -> {
                val balance = getBalance()
                model(Scan.Model.LnurlPayFlow.LnurlPayRequest(
                    lnurlPay = intent.lnurlPay,
                    balanceMsat = balance.msat,
                    error = result.value
                ))
            }
            is Either.Right -> {
                val paymentRequest = result.value.paymentRequest
                val paymentId = UUID.randomUUID()
                databaseManager.paymentsDb().enqueueMetadata(
                    row = WalletPaymentMetadataRow.serialize(
                        pay = intent.lnurlPay,
                        successAction = result.value.successAction
                    ),
                    id = WalletPaymentId.OutgoingPaymentId(paymentId)
                )
                peerManager.getPeer().send(
                    SendPayment(
                        paymentId = paymentId,
                        amount = intent.amount,
                        recipient = paymentRequest.nodeId,
                        details = OutgoingPayment.Details.Normal(paymentRequest)
                    )
                )
                model(Scan.Model.LnurlPayFlow.Sending)
            }
        }
    }

    private suspend fun processIntent(
        intent: Scan.Intent.LnurlPayFlow.CancelLnurlPayment
    ) {
        lnurlRequestId += 1
        val balance = getBalance()
        model(Scan.Model.LnurlPayFlow.LnurlPayRequest(
            lnurlPay = intent.lnurlPay,
            balanceMsat = balance.msat,
            error = null
        ))
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processIntent(
        intent: Scan.Intent.LnurlAuthFlow.Login
    ) {
        model(Scan.Model.LnurlAuthFlow.LoggingIn(auth = intent.auth))
        val start = TimeSource.Monotonic.markNow()
        val psl = prefetchPublicSuffixListTask?.await()
        if (psl == null) {
            model(Scan.Model.LnurlAuthFlow.LoginResult(
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
            model(Scan.Model.LnurlAuthFlow.LoginResult(auth = intent.auth, error = error))
        } else {
            val pending = Duration.seconds(intent.minSuccessDelaySeconds) - start.elapsedNow()
            if (pending > Duration.ZERO) {
                delay(pending)
            }
            model(Scan.Model.LnurlAuthFlow.LoginResult(auth = intent.auth, error = error))
        }
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

        val requestChain = paymentRequest.chain()
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
