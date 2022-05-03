package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.Feature
import fr.acinq.lightning.Features
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.ReceivePayment
import fr.acinq.lightning.io.SendPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.secure
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.managers.*
import fr.acinq.phoenix.managers.Utilities.BitcoinAddressInfo
import fr.acinq.phoenix.utils.PublicSuffixList
import fr.acinq.phoenix.utils.chain
import fr.acinq.phoenix.utils.calculateBalance
import fr.acinq.phoenix.utils.createTrampolineFees
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
    private val utilities: Utilities,
    private val chain: Chain
) : AppController<Scan.Model, Scan.Intent>(
    loggerFactory = loggerFactory,
    firstModel = firstModel ?: Scan.Model.Ready
) {
    private var prefetchPublicSuffixListTask: Deferred<Pair<String, Long>?>? = null
    private var lnurlRequestId = 1

    private var continueLnurlTask: Deferred<LNUrl>? = null
    private var requestPayInvoiceTask: Deferred<LNUrl.PayInvoice>? = null
    private var sendWithdrawInvoiceTask: Deferred<JsonObject>? = null

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
                    is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest -> {
                        this.copy(balanceMsat = balance.msat)
                    }
                    is Scan.Model.LnurlPayFlow.LnurlPayFetch -> {
                        this.copy(balanceMsat = balance.msat)
                    }
                    else -> this
                }}
            }
        }
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
            is Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw -> launch {
                processIntent(intent)
            }
            is Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw -> launch {
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
        val input = stringPrep(intent.request)

        // Is it a Lightning invoice ?
        readPaymentRequest(input)?.let { paymentRequest ->
            checkForBadRequest(paymentRequest)?.let {
                return model(Scan.Model.BadRequest(it))
            }
            checkForDangerousRequest(paymentRequest)?.let {
                return model(Scan.Model.InvoiceFlow.DangerousRequest(
                    reason = it,
                    request = intent.request,
                    paymentRequest = paymentRequest
                ))
            }

            val balance = getBalance()
            return model(Scan.Model.InvoiceFlow.InvoiceRequest(
                request = intent.request,
                paymentRequest = paymentRequest,
                balanceMsat = balance.msat
            ))
        }

        // Is it an LNURL ?
        readLNURL(input)?.let { return when (it) {
            is Either.Left -> { // it.value: LnUrl.Auth
                prefetchPublicSuffixListTask = appConfigManager.fetchPublicSuffixListAsync()
                model(Scan.Model.LnurlAuthFlow.LoginRequest(auth = it.value))
            }
            is Either.Right -> { // it.value: Url
                val url = it.value
                val requestId = lnurlRequestId
                model(Scan.Model.LnurlServiceFetch)
                val task = lnurlManager.continueLnurlAsync(url)
                continueLnurlTask = task
                val result: Either<Scan.BadRequestReason, LNUrl> = try {
                    Either.Right(task.await())
                } catch (e: Exception) {
                    when (e) {
                        is LNUrl.Error.RemoteFailure -> {
                            Either.Left(Scan.BadRequestReason.ServiceError(url, e))
                        } else -> {
                            Either.Left(Scan.BadRequestReason.InvalidLnUrl(url))
                        }
                    }
                }
                if (requestId != lnurlRequestId) {
                    // Intent.CancelLnurlServiceFetch has been issued
                    return
                }
                when (result) {
                    is Either.Left -> { // result: BadRequestReason
                        model(Scan.Model.BadRequest(result.value))
                    }
                    is Either.Right -> { // result: LNUrl
                        when (val lnurl = result.value) {
                            is LNUrl.Pay -> {
                                val balance = getBalance()
                                model(Scan.Model.LnurlPayFlow.LnurlPayRequest(
                                    lnurlPay = lnurl,
                                    balanceMsat = balance.msat,
                                    error = null
                                ))
                            }
                            is LNUrl.Withdraw -> {
                                val balance = getBalance()
                                model(Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                                    lnurlWithdraw = lnurl,
                                    balanceMsat = balance.msat,
                                    error = null
                                ))
                            }
                            else -> {
                                model(Scan.Model.BadRequest(
                                    Scan.BadRequestReason.UnsupportedLnUrl(url))
                                )
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
        val peer = peerManager.getPeer()
        val trampolineFees = intent.maxFees?.let { maxFees ->
            createTrampolineFees(
                defaultFees = peer.walletParams.trampolineFees,
                maxFees = maxFees
            )
        }
        peer.send(
            SendPayment(
                paymentId = paymentId,
                amount = intent.amount,
                recipient = paymentRequest.nodeId,
                details = OutgoingPayment.Details.Normal(paymentRequest),
                trampolineFeesOverride = trampolineFees
            )
        )
        model(Scan.Model.InvoiceFlow.Sending)
    }

    private suspend fun processIntent(
        @Suppress("UNUSED_PARAMETER")
        intent: Scan.Intent.CancelLnurlServiceFetch
    ) {
        lnurlRequestId += 1
        continueLnurlTask?.cancel()
        continueLnurlTask = null
        model(Scan.Model.Ready)
    }

    private suspend fun processIntent(
        intent: Scan.Intent.LnurlPayFlow.SendLnurlPayment
    ) {
        val requestId = lnurlRequestId
        run { // scoping
            val balance = getBalance()
            model(Scan.Model.LnurlPayFlow.LnurlPayFetch(
                lnurlPay = intent.lnurlPay,
                balanceMsat = balance.msat
            ))
        }

        val task = lnurlManager.requestPayInvoiceAsync(
            lnurlPay = intent.lnurlPay,
            amount = intent.amount,
            comment = intent.comment
        )
        requestPayInvoiceTask = task
        val result: Either<Scan.LnurlPayError, LNUrl.PayInvoice> = try {
            val invoice = task.await()

            val requestChain = invoice.paymentRequest.chain()
            if (chain != requestChain) {
                Either.Left(Scan.LnurlPayError.ChainMismatch(chain, requestChain))
            } else {
                val db = databaseManager.databases.filterNotNull().first()
                val previousPayment = db.payments.listOutgoingPayments(
                    paymentHash = invoice.paymentRequest.paymentHash
                ).find {
                    it.status is OutgoingPayment.Status.Completed.Succeeded
                }
                if (previousPayment != null) {
                    Either.Left(Scan.LnurlPayError.AlreadyPaidInvoice)
                } else {
                    Either.Right(invoice)
                }
            }
        } catch (err: Throwable) { when (err) {
            is LNUrl.Error.RemoteFailure -> {
                Either.Left(Scan.LnurlPayError.RemoteError(err))
            }
            is LNUrl.Error.PayInvoice -> {
                Either.Left(Scan.LnurlPayError.BadResponseError(err))
            }
            else -> { // unexpected exception: map to generic error
                Either.Left(Scan.LnurlPayError.RemoteError(
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
                val metadata = WalletPaymentMetadata(
                    lnurl = LnurlPayMetadata(
                        pay = intent.lnurlPay,
                        description = intent.lnurlPay.metadata.plainText,
                        successAction = result.value.successAction
                    )
                )
                WalletPaymentMetadataRow.serialize(metadata)?.let { row ->
                    databaseManager.paymentsDb().enqueueMetadata(
                        row = row,
                        id = WalletPaymentId.OutgoingPaymentId(paymentId)
                    )
                }
                val peer = peerManager.getPeer()
                val trampolineFees = intent.maxFees?.let { maxFees ->
                    createTrampolineFees(
                        defaultFees = peer.walletParams.trampolineFees,
                        maxFees = maxFees
                    )
                }
                peer.send(
                    SendPayment(
                        paymentId = paymentId,
                        amount = intent.amount,
                        recipient = paymentRequest.nodeId,
                        details = OutgoingPayment.Details.Normal(paymentRequest),
                        trampolineFeesOverride = trampolineFees
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
        requestPayInvoiceTask?.cancel()
        requestPayInvoiceTask = null
        val balance = getBalance()
        model(Scan.Model.LnurlPayFlow.LnurlPayRequest(
            lnurlPay = intent.lnurlPay,
            balanceMsat = balance.msat,
            error = null
        ))
    }

    private suspend fun processIntent(
        intent: Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw
    ) {
        val requestId = lnurlRequestId
        run { // scoping
            val balance = getBalance()
            model(Scan.Model.LnurlWithdrawFlow.LnurlWithdrawFetch(
                lnurlWithdraw = intent.lnurlWithdraw,
                balanceMsat = balance.msat
            ))
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
        } catch (err: Throwable) { when (err) {
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
        }}
        if (requestId != lnurlRequestId) {
            // Intent.LnurlWithdrawFlow.CancelLnurlWithdraw has been issued
            return
        }
        if (error != null) {
            val balance = getBalance()
            model(Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                lnurlWithdraw = intent.lnurlWithdraw,
                balanceMsat = balance.msat,
                error = error
            ))
        } else {
            model(Scan.Model.LnurlWithdrawFlow.Receiving(
                lnurlWithdraw = intent.lnurlWithdraw,
                amount = intent.amount,
                description = intent.description,
                paymentHash = paymentRequest.paymentHash.toHex()
            ))
        }
    }

    private suspend fun processIntent(
        intent: Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw
    ) {
        lnurlRequestId += 1
        sendWithdrawInvoiceTask?.cancel()
        sendWithdrawInvoiceTask = null
        val balance = getBalance()
        model(Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
            lnurlWithdraw = intent.lnurlWithdraw,
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
                error = Scan.LoginError.OtherError(
                    details = LNUrl.Error.Auth.MissingPublicSuffixList
                )
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

    suspend fun inspectClipboard(string: String): Scan.ClipboardContent? {
        val input = stringPrep(string)

        // Is it a Lightning invoice ?
        readPaymentRequest(input)?.let {
            return Scan.ClipboardContent.InvoiceRequest(it)
        }

        // Is it an LNURL ?
        readLNURL(input)?.let { return when (it) {
            is Either.Left -> { // it.value: LnUrl.Auth
                Scan.ClipboardContent.LoginRequest(it.value)
            }
            is Either.Right -> { // it.value: Url
                Scan.ClipboardContent.LnurlRequest(it.value)
            }
        }}

        // Is it a bitcoin address ?
        readBitcoinAddress(input).let { when (it) {
            is Either.Left -> { // it.value: Scan.BadRequestReason
                // ignore/continue
            }
            is Either.Right -> { // it.value: Utilities.BitcoinAddressInfo
                it.value.params.get("lightning")?.let { lnParam ->
                    return inspectClipboard(lnParam)
                }
            }
        }}

        return null
    }

    private fun stringPrep(input: String): String {
        return input
            .replace("\\u00A0", "") // \u00A0 = '\n'
            .trim() // leading & trailing whitespace
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
    ) : PaymentRequest? {

        val (_, request) = trimMatchingPrefix(input, listOf(
            "lightning://", "lightning:", "bitcoin://", "bitcoin:"
        ))

        return try {
            PaymentRequest.read(request) // <- throws
        } catch (t: Throwable) {
            null
        }
    }

    private suspend fun checkForBadRequest(
        paymentRequest: PaymentRequest
    ): Scan.BadRequestReason? {

        val requestChain = paymentRequest.chain()
        if (chain != requestChain) {
            return Scan.BadRequestReason.ChainMismatch(chain, requestChain)
        }

        val db = databaseManager.databases.filterNotNull().first()
        val previousInvoicePayment = db.payments.listOutgoingPayments(paymentRequest.paymentHash).find {
            it.status is OutgoingPayment.Status.Completed.Succeeded
        }
        return if (previousInvoicePayment != null) {
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

    private fun readLNURL(input: String): Either<LNUrl.Auth, Url>? {

        val (_, request) = trimMatchingPrefix(input, listOf(
            "lightning://", "lightning:"
        ))

        return try {
            lnurlManager.interactiveExtractLnurl(request)
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
