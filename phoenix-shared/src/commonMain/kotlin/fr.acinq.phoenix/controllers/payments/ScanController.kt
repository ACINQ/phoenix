package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Feature
import fr.acinq.lightning.Features
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.ReceivePayment
import fr.acinq.lightning.io.SendPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.secure
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.LNUrlManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.kodein.log.LoggerFactory
import kotlin.random.Random
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
    private val chain: Chain
) : AppController<Scan.Model, Scan.Intent>(
    loggerFactory = loggerFactory,
    firstModel = firstModel ?: Scan.Model.Ready
) {
    private var prefetchPublicSuffixListTask: Deferred<Pair<String, Long>?>? = null

    /** Arbitraty identifier used to track the current lnurl task. Those tasks are asynchronous and can be cancelled. We use this field to track which one is in progress. */
    private var lnurlRequestId = 1

    /** Tracks the task fetching information for a given lnurl. Use this field to cancel the task (see [cancelLnurlFetch]). */
    private var continueLnurlTask: Deferred<LNUrl>? = null

    /** Tracks the task requesting an invoice to a Lnurl service. Use this field to cancel the task (see [cancelLnurlPay]). */
    private var requestPayInvoiceTask: Deferred<LNUrl.PayInvoice>? = null

    /** Tracks the task that send an invoice we generated to a Lnurl service, in order to make a withdrawal. Use this field to cancel the task (see [cancelLnurlWithdraw]). */
    private var sendWithdrawInvoiceTask: Deferred<JsonObject>? = null

    constructor(business: PhoenixBusiness, firstModel: Scan.Model?) : this(
        loggerFactory = business.loggerFactory,
        firstModel = firstModel,
        peerManager = business.peerManager,
        lnurlManager = business.lnUrlManager,
        databaseManager = business.databaseManager,
        appConfigManager = business.appConfigurationManager,
        chain = business.chain,
    )

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                val balance = calculateBalance(channels)
                model {
                    when (this) {
                        is Scan.Model.InvoiceFlow.InvoiceRequest -> {
                            this.copy(balanceMsat = balance.msat)
                        }
                        is Scan.Model.LnurlPayFlow.LnurlPayRequest -> {
                            this.copy(balanceMsat = balance.msat)
                        }
                        is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest -> {
                            this.copy(balanceMsat = balance.msat)
                        }
                        is Scan.Model.LnurlPayFlow.LnurlPayFetch -> {
                            this.copy(balanceMsat = balance.msat)
                        }
                        else -> this
                    }
                }
            }
        }
    }

    private suspend fun getBalance(): MilliSatoshi {
        return calculateBalance(peerManager.getPeer().channels)
    }

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> launch { processScannedInput(intent) }
            is Scan.Intent.InvoiceFlow.ConfirmDangerousRequest -> launch { confirmAmountlessInvoice(intent) }
            is Scan.Intent.InvoiceFlow.SendInvoicePayment -> launch {
                payInvoice(
                    amountToSend = intent.amount,
                    paymentRequest = intent.paymentRequest,
                    customMaxFees = intent.maxFees,
                    lnurlPayMetadata = null
                )
                model(Scan.Model.InvoiceFlow.Sending)
            }
            is Scan.Intent.CancelLnurlServiceFetch -> launch { cancelLnurlFetch() }
            is Scan.Intent.LnurlPayFlow.SendLnurlPayment -> launch { processLnurlPay(intent) }
            is Scan.Intent.LnurlPayFlow.CancelLnurlPayment -> launch { cancelLnurlPay(intent) }
            is Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw -> launch { processLnurlWithdraw(intent) }
            is Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw -> launch { cancelLnurlWithdraw(intent) }
            is Scan.Intent.LnurlAuthFlow.Login -> launch { processLnurlAuth(intent) }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processScannedInput(
        intent: Scan.Intent.Parse
    ) {
        val input = Parser.removeExcessInput(intent.request)

        Parser.readPaymentRequest(input)?.let {
            processLightningInvoice(it)
        } ?: readLNURL(input)?.let {
            processLnurlData(it)
        } ?: Parser.readBitcoinAddress(chain, input).let {
            processBitcoinAddress(it)
        }
    }

    /** Inspects the Lightning invoice for errors and update the model with the adequate value. */
    private suspend fun processLightningInvoice(paymentRequest: PaymentRequest) {
        val model = checkForBadRequest(paymentRequest)?.let {
            Scan.Model.BadRequest(it)
        } ?: checkForDangerousRequest(paymentRequest)?.let {
            Scan.Model.InvoiceFlow.DangerousRequest(paymentRequest.write(), paymentRequest, it)
        } ?: Scan.Model.InvoiceFlow.InvoiceRequest(
            request = paymentRequest.write(),
            paymentRequest = paymentRequest,
            balanceMsat = getBalance().msat
        )
        model(model)
    }

    /** Return the adequate model for a Bitcoin address result. */
    private suspend fun processBitcoinAddress(data: Either<BitcoinAddressError, BitcoinAddressInfo>) {
        logger.info { "processing bitcoin address=$data" }
        val model = when (data) {
            is Either.Right -> {
                val address = data.value
                if (address.paymentRequest != null) {
                    // address contains a valid payment request
                    Scan.Model.InvoiceFlow.InvoiceRequest(
                        request = address.paymentRequest.write(),
                        paymentRequest = address.paymentRequest,
                        balanceMsat = getBalance().msat
                    )
                } else {
                    // we can't pay on-chain addresses yet.
                    Scan.Model.BadRequest(Scan.BadRequestReason.IsBitcoinAddress)
                }
            }
            is Either.Left -> {
                val error = data.value
                if (error is BitcoinAddressError.ChainMismatch) {
                    Scan.Model.BadRequest(reason = Scan.BadRequestReason.ChainMismatch(myChain = error.myChain, requestChain = error.addrChain))
                } else {
                    Scan.Model.BadRequest(reason = Scan.BadRequestReason.UnknownFormat)
                }
            }
        }
        model(model)
    }

    private suspend fun <T, U> executeLnurlAction(action: suspend () -> Either<T, U>): Either<T, U>? {
        val requestId = lnurlRequestId
        val result = action()
        return if (requestId == lnurlRequestId) {
            result
        } else {
            null
        }
    }

    private suspend fun processLnurlData(data: Either<LNUrl.Auth, Url>) {
        when (data) {
            is Either.Left -> {
                // proceed to lnurl-auth flow
                prefetchPublicSuffixListTask = appConfigManager.fetchPublicSuffixListAsync()
                model(Scan.Model.LnurlAuthFlow.LoginRequest(auth = data.value))
            }
            is Either.Right -> { // it.value: Url
                val url = data.value
                model(Scan.Model.LnurlServiceFetch)
                val result = executeLnurlAction {
                    val task = lnurlManager.continueLnurlAsync(url)
                    continueLnurlTask = task
                    try {
                        Either.Right(task.await())
                    } catch (e: Exception) {
                        when (e) {
                            is LNUrl.Error.RemoteFailure -> Either.Left(
                                Scan.BadRequestReason.ServiceError(url, e)
                            )
                            else -> Either.Left(
                                Scan.BadRequestReason.InvalidLnUrl(url)
                            )
                        }
                    }
                }
                when (result) {
                    null -> {} // do nothing, this request has been cancelled.
                    is Either.Left -> { // result: BadRequestReason
                        model(Scan.Model.BadRequest(result.value))
                    }
                    is Either.Right -> { // result: LNUrl
                        when (val lnurl = result.value) {
                            is LNUrl.Pay -> {
                                val balance = getBalance()
                                model(
                                    Scan.Model.LnurlPayFlow.LnurlPayRequest(
                                        lnurlPay = lnurl,
                                        balanceMsat = balance.msat,
                                        error = null
                                    )
                                )
                            }
                            is LNUrl.Withdraw -> {
                                val balance = getBalance()
                                model(
                                    Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                                        lnurlWithdraw = lnurl,
                                        balanceMsat = balance.msat,
                                        error = null
                                    )
                                )
                            }
                            else -> {
                                model(Scan.Model.BadRequest(Scan.BadRequestReason.UnsupportedLnUrl(url)))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun confirmAmountlessInvoice(
        intent: Scan.Intent.InvoiceFlow.ConfirmDangerousRequest
    ) {
        val balance = getBalance()
        model(
            Scan.Model.InvoiceFlow.InvoiceRequest(
                request = intent.request,
                paymentRequest = intent.paymentRequest,
                balanceMsat = balance.msat
            )
        )
    }

    /** Extract invoice and send it to the Peer to make the payment, attaching custom trampoline fees if needed. */
    private suspend fun payInvoice(
        amountToSend: MilliSatoshi,
        paymentRequest: PaymentRequest,
        customMaxFees: MaxFees?,
        lnurlPayMetadata: LnurlPayMetadata?
    ) {
        val paymentId = UUID.randomUUID()
        val peer = peerManager.getPeer()

        // save lnurl metadata if any
        lnurlPayMetadata?.let { WalletPaymentMetadataRow.serialize(WalletPaymentMetadata(it)) }?.let { row ->
            databaseManager.paymentsDb().enqueueMetadata(
                row = row,
                id = WalletPaymentId.OutgoingPaymentId(paymentId)
            )
        }

        // compute new trampoline fees if a custom max has been set
        val trampolineFees = customMaxFees?.let {
            createTrampolineFees(
                defaultFees = peer.walletParams.trampolineFees,
                maxFees = it
            )
        }

        peer.send(
            SendPayment(
                paymentId = paymentId,
                amount = amountToSend,
                recipient = paymentRequest.nodeId,
                details = OutgoingPayment.Details.Normal(paymentRequest),
                trampolineFeesOverride = trampolineFees
            )
        )
    }

    /** Cancel the current lnurl task fetching data from a service. */
    private suspend fun cancelLnurlFetch() {
        lnurlRequestId += 1
        continueLnurlTask?.cancel()
        continueLnurlTask = null
        model(Scan.Model.Ready)
    }

    private suspend fun processLnurlPay(
        intent: Scan.Intent.LnurlPayFlow.SendLnurlPayment
    ) {
        model(Scan.Model.LnurlPayFlow.LnurlPayFetch(lnurlPay = intent.lnurlPay, balanceMsat = getBalance().msat))
        val result = executeLnurlAction {
            val task = lnurlManager.requestPayInvoiceAsync(
                lnurlPay = intent.lnurlPay,
                amount = intent.amount,
                comment = intent.comment
            )
            requestPayInvoiceTask = task
            try {
                val invoice = task.await()
                when (val check = checkForBadRequest(invoice.paymentRequest)) {
                    is Scan.BadRequestReason.ChainMismatch -> Either.Left(
                        Scan.LnurlPayError.ChainMismatch(chain, check.requestChain)
                    )
                    is Scan.BadRequestReason.AlreadyPaidInvoice -> Either.Left(
                        Scan.LnurlPayError.AlreadyPaidInvoice
                    )
                    else -> Either.Right(invoice)
                }
            } catch (err: Throwable) {
                when (err) {
                    is LNUrl.Error.RemoteFailure -> Either.Left(
                        Scan.LnurlPayError.RemoteError(err)
                    )
                    is LNUrl.Error.PayInvoice -> Either.Left(
                        Scan.LnurlPayError.BadResponseError(err)
                    )
                    else -> Either.Left(
                        Scan.LnurlPayError.RemoteError(LNUrl.Error.RemoteFailure.Unreadable(origin = intent.lnurlPay.callback.host))
                    )
                }
            }
        }

        when (result) {
            null -> Unit
            is Either.Left -> {
                val balance = getBalance()
                model(
                    Scan.Model.LnurlPayFlow.LnurlPayRequest(
                        lnurlPay = intent.lnurlPay,
                        balanceMsat = balance.msat,
                        error = result.value
                    )
                )
            }
            is Either.Right -> {
                payInvoice(
                    amountToSend = intent.amount,
                    paymentRequest = result.value.paymentRequest,
                    customMaxFees = intent.maxFees,
                    lnurlPayMetadata = LnurlPayMetadata(
                        pay = intent.lnurlPay,
                        description = intent.lnurlPay.metadata.plainText,
                        successAction = result.value.successAction
                    )
                )
                model(Scan.Model.LnurlPayFlow.Sending)
            }
        }
    }

    private suspend fun cancelLnurlPay(
        intent: Scan.Intent.LnurlPayFlow.CancelLnurlPayment
    ) {
        lnurlRequestId += 1
        requestPayInvoiceTask?.cancel()
        requestPayInvoiceTask = null
        val balance = getBalance()
        model(
            Scan.Model.LnurlPayFlow.LnurlPayRequest(
                lnurlPay = intent.lnurlPay,
                balanceMsat = balance.msat,
                error = null
            )
        )
    }

    private suspend fun processLnurlWithdraw(
        intent: Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw
    ) {
        val requestId = lnurlRequestId
        run { // scoping
            val balance = getBalance()
            model(
                Scan.Model.LnurlWithdrawFlow.LnurlWithdrawFetch(
                    lnurlWithdraw = intent.lnurlWithdraw,
                    balanceMsat = balance.msat
                )
            )
        }

        val deferred = CompletableDeferred<PaymentRequest>()
        val preimage = ByteVector32(Random.secure().nextBytes(32))
        peerManager.getPeer().send(
            ReceivePayment(
                paymentPreimage = preimage,
                amount = intent.amount,
                description = intent.description ?: intent.lnurlWithdraw.defaultDescription,
                expirySeconds = (3600 * 24 * 7).toLong(), // one week
                result = deferred
            )
        )
        val paymentRequest = deferred.await()
        if (requestId != lnurlRequestId) {
            // Intent.LnurlWithdrawFlow.CancelLnurlWithdraw has been issued
            return
        }
        val task = lnurlManager.sendWithdrawInvoiceAsync(
            lnurlWithdraw = intent.lnurlWithdraw,
            paymentRequest = paymentRequest
        )
        sendWithdrawInvoiceTask = task
        val error: Scan.LnurlWithdrawError? = try {
            task.await()
            null
        } catch (err: Throwable) {
            when (err) {
                is LNUrl.Error.RemoteFailure -> {
                    Scan.LnurlWithdrawError.RemoteError(err)
                }
                else -> { // unexpected exception: map to generic error
                    Scan.LnurlWithdrawError.RemoteError(
                        LNUrl.Error.RemoteFailure.Unreadable(
                            origin = intent.lnurlWithdraw.callback.host
                        )
                    )
                }
            }
        }
        if (requestId != lnurlRequestId) {
            // Intent.LnurlWithdrawFlow.CancelLnurlWithdraw has been issued
            return
        }
        if (error != null) {
            val balance = getBalance()
            model(
                Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                    lnurlWithdraw = intent.lnurlWithdraw,
                    balanceMsat = balance.msat,
                    error = error
                )
            )
        } else {
            model(
                Scan.Model.LnurlWithdrawFlow.Receiving(
                    lnurlWithdraw = intent.lnurlWithdraw,
                    amount = intent.amount,
                    description = intent.description,
                    paymentHash = paymentRequest.paymentHash.toHex()
                )
            )
        }
    }

    private suspend fun cancelLnurlWithdraw(
        intent: Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw
    ) {
        lnurlRequestId += 1
        sendWithdrawInvoiceTask?.cancel()
        sendWithdrawInvoiceTask = null
        val balance = getBalance()
        model(
            Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                lnurlWithdraw = intent.lnurlWithdraw,
                balanceMsat = balance.msat,
                error = null
            )
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processLnurlAuth(
        intent: Scan.Intent.LnurlAuthFlow.Login
    ) {
        model(Scan.Model.LnurlAuthFlow.LoggingIn(auth = intent.auth))
        val start = TimeSource.Monotonic.markNow()
        val psl = prefetchPublicSuffixListTask?.await()
        if (psl == null) {
            model(
                Scan.Model.LnurlAuthFlow.LoginResult(
                    auth = intent.auth,
                    error = Scan.LoginError.OtherError(
                        details = LNUrl.Error.Auth.MissingPublicSuffixList
                    )
                )
            )
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

    /** Directly called by swift code in iOS app. */
    suspend fun inspectClipboard(string: String): Scan.ClipboardContent? {
        val input = Parser.removeExcessInput(string)

        // Is it a Lightning invoice ?
        Parser.readPaymentRequest(input)?.let {
            return Scan.ClipboardContent.InvoiceRequest(it)
        }

        // Is it an LNURL ?
        readLNURL(input)?.let {
            return when (it) {
                is Either.Left -> { // it.value: LnUrl.Auth
                    Scan.ClipboardContent.LoginRequest(it.value)
                }
                is Either.Right -> { // it.value: Url
                    Scan.ClipboardContent.LnurlRequest(it.value)
                }
            }
        }

        // Is it a bitcoin address ?
        readBitcoinAddress(input).let {
            if (it is Either.Right) {
                return it.value.paymentRequest?.let { Scan.ClipboardContent.InvoiceRequest(it) }
            }
        }

        return null
    }

    /** Check that the invoice is on same chain and has not already been paid. */
    private suspend fun checkForBadRequest(
        paymentRequest: PaymentRequest
    ): Scan.BadRequestReason? {

        val requestChain = paymentRequest.chain()
        if (chain != requestChain) {
            return Scan.BadRequestReason.ChainMismatch(chain, requestChain)
        }

        val db = databaseManager.databases.filterNotNull().first()
        return if (db.payments.listOutgoingPayments(paymentRequest.paymentHash).any { it.status is OutgoingPayment.Status.Completed.Succeeded }) {
            Scan.BadRequestReason.AlreadyPaidInvoice
        } else {
            null
        }
    }

    private suspend fun checkForDangerousRequest(
        paymentRequest: PaymentRequest
    ): Scan.DangerousRequestReason? {

        if (paymentRequest.amount == null) {
            // amountless invoice -> dangerous unless full trampoline is in effect
            val features = Features(paymentRequest.features)
            if (!features.hasFeature(Feature.TrampolinePayment)) {
                return Scan.DangerousRequestReason.IsAmountlessInvoice
            }
        }
        if (paymentRequest.nodeId == peerManager.getPeer().nodeParams.nodeId) {
            return Scan.DangerousRequestReason.IsOwnInvoice
        }
        return null
    }

    private fun readLNURL(input: String): Either<LNUrl.Auth, Url>? = try {
        lnurlManager.interactiveExtractLnurl(Parser.trimMatchingPrefix(input, listOf("lightning://", "lightning:")))
    } catch (t: Throwable) {
        null
    }

    private fun readBitcoinAddress(
        input: String
    ): Either<Scan.BadRequestReason, BitcoinAddressInfo> = when (val result = Parser.readBitcoinAddress(chain, input)) {
        is Either.Left -> {
            when (val reason = result.value) {
                is BitcoinAddressError.ChainMismatch -> {
                    Either.Left(
                        Scan.BadRequestReason.ChainMismatch(
                            myChain = reason.myChain,
                            requestChain = reason.addrChain
                        )
                    )
                }
                else -> {
                    Either.Left(Scan.BadRequestReason.UnknownFormat)
                }
            }
        }
        is Either.Right -> {
            Either.Right(result.value)
        }
    }
}
