package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.io.OfferInvoiceReceived
import fr.acinq.lightning.io.OfferNotPaid
import fr.acinq.lightning.io.PayInvoice
import fr.acinq.lightning.io.PayOffer
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.data.LnurlPayMetadata
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.lnurl.Lnurl
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.data.lnurl.LnurlWithdraw
import fr.acinq.phoenix.utils.DnsResolvers
import fr.acinq.phoenix.utils.EmailLikeAddress
import fr.acinq.phoenix.utils.Parser
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SendManager(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val lnurlManager: LnurlManager,
    private val databaseManager: DatabaseManager,
    private val chain: Chain,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        lnurlManager = business.lnurlManager,
        databaseManager = business.databaseManager,
        chain = business.chain
    )

    private val log = loggerFactory.newLogger(this::class)

    sealed class BadRequestReason : Exception() {
        data object UnknownFormat : BadRequestReason()
        data object AlreadyPaidInvoice : BadRequestReason()
        data object PaymentPending : BadRequestReason()
        data class Expired(val timestampSeconds: Long, val expirySeconds: Long) : BadRequestReason()
        data class ChainMismatch(val expected: Chain) : BadRequestReason()
        data class ServiceError(val url: Url, val error: LnurlError.RemoteFailure) : BadRequestReason()
        data class InvalidLnurl(val url: Url) : BadRequestReason()
        data class Bip353NameNotFound(val username: String, val domain: String) : BadRequestReason()
        data class Bip353InvalidUri(val path: String) : BadRequestReason()
        data class Bip353InvalidOffer(val path: String) : BadRequestReason()
        data class Bip353NoDNSSEC(val path: String) : BadRequestReason()
        data class UnsupportedLnurl(val url: Url) : BadRequestReason()
    }

    sealed class LnurlPayError {
        data class RemoteError(val err: LnurlError.RemoteFailure) : LnurlPayError()
        data class BadResponseError(val err: LnurlError.Pay.Invoice) : LnurlPayError()
        data class ChainMismatch(val expected: Chain) : LnurlPayError()
        data object AlreadyPaidInvoice : LnurlPayError()
        data object PaymentPending : LnurlPayError()
    }

    sealed class LnurlWithdrawError {
        data class RemoteError(val err: LnurlError.RemoteFailure) : LnurlWithdrawError()
    }

    sealed class LnurlAuthError {
        data class ServerError(val details: LnurlError.RemoteFailure) : LnurlAuthError()
        data class NetworkError(val details: Throwable) : LnurlAuthError()
        data class OtherError(val details: Throwable) : LnurlAuthError()
    }

    sealed class ParseProgress {
        data object LnurlServiceFetch: ParseProgress()
        data object ResolvingBip353: ParseProgress()
    }

    sealed class ParseResult {

        data class BadRequest(
            val request: String,
            val reason: BadRequestReason
        ): ParseResult()

        sealed class Success : ParseResult()
        data class Bolt11Invoice(
            val request: String,
            val invoice: fr.acinq.lightning.payment.Bolt11Invoice
        ): Success()

        data class Bolt12Offer(
            val offer: OfferTypes.Offer,
            val lightningAddress: String?
        ): Success()

        data class Uri(
            val uri: BitcoinUri
        ): Success()

        sealed class Lnurl: Success() {
            data class Pay(
                val paymentIntent: LnurlPay.Intent,
                val lightningAddress: String?
            ): Lnurl()

            data class Withdraw(
                val lnurlWithdraw: LnurlWithdraw,
            ): Lnurl()

            data class Auth(
                val auth: LnurlAuth
            ): Lnurl()
        }
    }

    suspend fun parse(
        request: String,
        progress: (p: ParseProgress) -> Unit
    ): ParseResult {

        val input = Parser.removeExcessInput(request)
        return try {
            Parser.readBolt11Invoice(input)?.let {
                processBolt11Invoice(it)
            } ?: Parser.readOffer(input)?.let {
                processOffer(it, null)
            } ?: readEmailLikeAddress(input, progress)?.let {
                when (it) {
                    is Either.Left -> processOffer(it.value, input)
                    is Either.Right -> processLnurl(it.value, input, progress)
                }
            } ?: readLnurl(input)?.let {
                processLnurl(it, null, progress)
            } ?: readBitcoinAddress(input)?.let {
                processBitcoinAddress(input, it)
            } ?: readLNURLFallback(input)?.let {
                processLnurl(it, null, progress)
            } ?: run {
                ParseResult.BadRequest(
                    request = request,
                    reason = BadRequestReason.UnknownFormat
                )
            }
        } catch (e: Exception) {
            if (e is BadRequestReason) {
                ParseResult.BadRequest(
                    request = request,
                    reason = e
                )
            } else {
                ParseResult.BadRequest(
                    request = request,
                    reason = BadRequestReason.UnknownFormat
                )
            }
        }
    }

    /** Inspects the Lightning invoice for errors and update the model with the adequate value. */
    private suspend fun processBolt11Invoice(
        invoice: Bolt11Invoice
    ): ParseResult {

        return checkForBadBolt11Invoice(invoice)?.let {
            ParseResult.BadRequest(request = invoice.write(), reason = it)
        } ?: ParseResult.Bolt11Invoice(
            request = invoice.write(),
            invoice = invoice,
        )
    }

    private suspend fun checkForBadBolt11Invoice(
        invoice: Bolt11Invoice
    ): BadRequestReason? {

        val actualChain = invoice.chain
        if (chain != actualChain) {
            return BadRequestReason.ChainMismatch(expected = chain)
        }

        if (invoice.isExpired(currentTimestampSeconds())) {
            return BadRequestReason.Expired(invoice.timestampSeconds, invoice.expirySeconds ?: Bolt11Invoice.DEFAULT_EXPIRY_SECONDS.toLong())
        }

        val db = databaseManager.databases.filterNotNull().first()
        val similarPayments = db.payments.listLightningOutgoingPayments(invoice.paymentHash)
        // we MUST raise an error if this payment hash has already been paid, or is being paid.
        // parallel pending payments on the same payment hash can trigger force-closes
        // FIXME: this check should be done in lightning-kmp, not in Phoenix
        return when {
            similarPayments.any { it.status is LightningOutgoingPayment.Status.Succeeded || it.parts.any { part -> part.status is LightningOutgoingPayment.Part.Status.Succeeded } } ->
                BadRequestReason.AlreadyPaidInvoice
            similarPayments.any { it.status is LightningOutgoingPayment.Status.Pending || it.parts.any { part -> part.status is LightningOutgoingPayment.Part.Status.Pending } } ->
                BadRequestReason.PaymentPending
            else -> null
        }
    }

    private fun processOffer(
        offer: OfferTypes.Offer,
        lightningAddress: String?
    ): ParseResult {

        return if (!offer.chains.contains(chain.chainHash)) {
            ParseResult.BadRequest(
                request = offer.encode(),
                reason = BadRequestReason.ChainMismatch(expected = chain)
            )
        } else {
            ParseResult.Bolt12Offer(offer, lightningAddress)
        }
    }

    @Throws(BadRequestReason::class, CancellationException::class)
    private suspend fun readEmailLikeAddress(
        input: String,
        progress: (p: ParseProgress) -> Unit
    ): Either<OfferTypes.Offer, Lnurl.Request>? {

        if (!input.contains("@", ignoreCase = true)) return null

        val address = Parser.parseEmailLikeAddress(input) ?: return null

        return when (address) {
            is EmailLikeAddress.Bip353 -> {
                progress(ParseProgress.ResolvingBip353)
                resolveBip353Offer(address.username, address.domain)?.let { Either.Left(it) }
            }
            is EmailLikeAddress.LnurlBased -> {
                progress(ParseProgress.LnurlServiceFetch)
                Either.Right(address.url)
            }
            is EmailLikeAddress.UnknownType -> {
                resolveBip353Offer(address.username.dropWhile { it == '₿' }, address.domain)?.let { Either.Left(it) }
                    ?: Either.Right(EmailLikeAddress.LnurlBased(address.source, address.username, address.domain).url)
            }
        }
    }

    /**
     * Resolve dns-based offers.
     * See https://github.com/bitcoin/bips/blob/master/bip-0353.mediawiki.
     */
    @Throws(BadRequestReason::class, CancellationException::class)
    private suspend fun resolveBip353Offer(
        username: String,
        domain: String,
    ): OfferTypes.Offer? {

        val dnsPath = "$username.user._bitcoin-payment.$domain."

        val json = DnsResolvers.getRandom().getTxtRecord(dnsPath)
        log.debug { "dns resolved to ${json.toString().take(100)}" }

        val status = json["Status"]?.jsonPrimitive?.intOrNull
        // could be a [BadRequestReason.Bip353NameNotFound] it status == 3
        if (status == null || status > 0) return null

        val records = json["Answer"]?.jsonArray
        if (records.isNullOrEmpty()) {
            log.debug { "no answer for $dnsPath" }
            // TODO add test (see #599)
            return null
        }

        // check dnssec
        val ad = json["AD"]?.jsonPrimitive?.booleanOrNull
        if (ad != true) {
            log.debug { "AD false, abort dns lookup" }
            throw BadRequestReason.Bip353NoDNSSEC(dnsPath)
        }

        // check name matches records
        val matchingRecord = records.filterIsInstance<JsonObject>().firstOrNull {
            log.debug { "inspecting record=$it" }
            it["name"]?.jsonPrimitive?.content == dnsPath
        } ?: throw BadRequestReason.Bip353NameNotFound(username, domain)

        val data = matchingRecord["data"]?.jsonPrimitive?.content
            ?: throw BadRequestReason.Bip353InvalidUri(dnsPath)

        return when (val res = Parser.parseBip21Uri(chain, data)) {
            is Either.Left -> {
                val error = res.value
                if (error is BitcoinUriError.InvalidScript && error.error is BitcoinError.ChainHashMismatch) {
                    throw BadRequestReason.ChainMismatch(expected = chain)
                } else {
                    throw BadRequestReason.Bip353InvalidUri(dnsPath)
                }
            }
            is Either.Right -> {
                res.value.offer ?:
                    throw BadRequestReason.Bip353InvalidOffer(dnsPath)
            }
        }
    }

    private suspend fun processLnurl(
        lnurl: Lnurl,
        lightningAddress: String?,
        progress: (p: ParseProgress) -> Unit
    ): ParseResult? {
        return when (lnurl) {
            is LnurlAuth -> {
                ParseResult.Lnurl.Auth(auth = lnurl)
            }
            // this lnurl is a standard url that must be executed immediately in order to get the actual
            // details from the service (the service should return either a LnurlPay or a LnurlWithdraw).
            is Lnurl.Request -> {
                progress(ParseProgress.LnurlServiceFetch)

                val url = lnurl.initialUrl
                val task = lnurlManager.executeLnurl(url)
                try {
                    when (val result: Lnurl = task.await()) {
                        is LnurlPay.Intent -> {
                            ParseResult.Lnurl.Pay(paymentIntent = result, lightningAddress)
                        }
                        is LnurlWithdraw -> {
                            ParseResult.Lnurl.Withdraw(lnurlWithdraw = result)
                        }
                        else -> {
                            ParseResult.BadRequest(
                                request = url.toString(),
                                reason = BadRequestReason.UnsupportedLnurl(url)
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "failed to process lnurl=$lnurl" }
                    when (e) {
                        is LnurlError.RemoteFailure ->
                            ParseResult.BadRequest(
                                request = url.toString(),
                                reason = BadRequestReason.ServiceError(url, e)
                            )
                        else ->
                            ParseResult.BadRequest(
                                request = url.toString(),
                                reason = BadRequestReason.InvalidLnurl(url)
                            )
                    }
                }
            }
            else -> null
        }
    }

    /** Reads a lnurl and return either a lnurl-auth (i.e. a http query that must not be called automatically), or the actual url embedded in the lnurl (that can be called afterwards). */
    private fun readLnurl(input: String): Lnurl? = try {
        Lnurl.extractLnurl(input, log)
    } catch (t: Throwable) {
        null
    }

    /** Invokes `Parser.readBitcoinAddress`, but maps [BitcoinUriError.InvalidUri] to a null result instead of a fatal error. */
    private fun readBitcoinAddress(input: String): Either<BitcoinUriError, BitcoinUri>? {
        return when (val result = Parser.parseBip21Uri(chain, input)) {
            is Either.Left -> when (result.left) {
                is BitcoinUriError.InvalidUri -> null
                else -> result
            }
            is Either.Right -> result
        }
    }

    /** Return the adequate model for a Bitcoin address result. */
    private fun processBitcoinAddress(
        input: String,
        result: Either<BitcoinUriError, BitcoinUri>
    ): ParseResult {
        return when (result) {
            is Either.Right -> {
                val address = result.value.address
                val bolt11 = result.value.paymentRequest
                val bolt12 = result.value.offer
                when {
                    address.isNotBlank() -> ParseResult.Uri(uri = result.value)
                    bolt11 != null -> ParseResult.Bolt11Invoice(request = input, invoice = bolt11)
                    bolt12 != null -> ParseResult.Bolt12Offer(offer = bolt12, lightningAddress = null)
                    else -> ParseResult.BadRequest(request = input, reason = BadRequestReason.UnknownFormat)
                }
            }
            is Either.Left -> {
                val error = result.value
                if (error is BitcoinUriError.InvalidScript && error.error is BitcoinError.ChainHashMismatch) {
                    ParseResult.BadRequest(request = input, reason = BadRequestReason.ChainMismatch(expected = chain))
                } else {
                    ParseResult.BadRequest(request = input, reason = BadRequestReason.UnknownFormat)
                }
            }
        }
    }

    /**
     * Support for LNURL Fallback Scheme,
     * e.g. as used by Bitcoin Beach Wallet's static Paycode QR.
     * https://github.com/ACINQ/phoenix/issues/323
     */
    private fun readLNURLFallback(input: String): Lnurl? = try {
        val url = Url(input)
        url.parameters["lightning"]?.let { fallback ->
            Lnurl.extractLnurl(fallback, log)
        }
    } catch (t: Throwable) {
        null
    }

    /** Extract invoice and send it to the Peer to make the payment, attaching custom trampoline fees if needed. */
    suspend fun payBolt11Invoice(
        amountToSend: MilliSatoshi,
        trampolineFees: TrampolineFees,
        invoice: Bolt11Invoice,
        metadata: WalletPaymentMetadata?,
    ) {
        val paymentId = UUID.randomUUID()
        val peer = peerManager.getPeer()

        // save lnurl metadata if any
        metadata?.let { row ->
            databaseManager.metadataQueue.enqueue(row = row, id = paymentId)
        }

        peer.send(
            PayInvoice(
                paymentId = paymentId,
                amount = amountToSend,
                paymentDetails = LightningOutgoingPayment.Details.Normal(paymentRequest = invoice),
                trampolineFeesOverride = listOf(trampolineFees)
            )
        )
    }

    suspend fun payBolt12Offer(
        paymentId: UUID,
        amount: MilliSatoshi,
        offer: OfferTypes.Offer,
        lightningAddress: String?,
        payerKey: PrivateKey,
        payerNote: String?,
        fetchInvoiceTimeoutInSeconds: Int
    ): OfferNotPaid? {
        val peer = peerManager.getPeer()

        lightningAddress?.let {
            val metadata = WalletPaymentMetadata(lightningAddress = it)
            databaseManager.metadataQueue.enqueue(metadata, paymentId)
        }

        val res = CompletableDeferred<OfferNotPaid?>()
        launch {
            peer.eventsFlow.collect {
                if (it is OfferNotPaid && it.request.paymentId == paymentId) {
                    res.complete(it)
                    cancel()
                } else if (it is OfferInvoiceReceived && it.request.paymentId == paymentId) {
                    res.complete(null)
                    cancel()
                }
            }
        }
        peer.send(PayOffer(
            paymentId = paymentId,
            payerKey = payerKey,
            payerNote = payerNote,
            amount = amount,
            offer = offer,
            fetchInvoiceTimeout = fetchInvoiceTimeoutInSeconds.seconds
        ))
        return res.await()
    }

    /**
     * Step 1 of 2:
     * First call this function to convert the LnurlPay.Intent into a LnurlPay.Invoice.
     *
     * Note: This step is cancellable. The UI can simply ignore the result.
     */
    suspend fun lnurlPay_requestInvoice(
        pay: ParseResult.Lnurl.Pay,
        amount: MilliSatoshi,
        comment: String?
    ): Either<LnurlPayError, LnurlPay.Invoice> {
        val task = lnurlManager.requestPayInvoice(
            intent = pay.paymentIntent,
            amount = amount,
            comment = comment
        )
        return try {
            val invoice = task.await()
            when (checkForBadBolt11Invoice(invoice.invoice)) {
                is BadRequestReason.ChainMismatch -> Either.Left(LnurlPayError.ChainMismatch(expected = chain))
                is BadRequestReason.AlreadyPaidInvoice -> Either.Left(LnurlPayError.AlreadyPaidInvoice)
                is BadRequestReason.PaymentPending -> Either.Left(LnurlPayError.PaymentPending)
                else -> Either.Right(invoice)
            }
        } catch (err: Throwable) {
            when (err) {
                is LnurlError.RemoteFailure -> Either.Left(LnurlPayError.RemoteError(err))
                is LnurlError.Pay.Invoice -> Either.Left(LnurlPayError.BadResponseError(err))
                else -> Either.Left(
                    LnurlPayError.RemoteError(
                        LnurlError.RemoteFailure.Unreadable(
                            origin = pay.paymentIntent.callback.host
                        )
                    )
                )
            }
        }
    }

    /**
     * Step 2 of 2:
     * After fetching the LnurlPay.Invoice, use this function to send the payment.
     *
     * Note: This step is non-cancellable.
     */
    suspend fun lnurlPay_payInvoice(
        pay: ParseResult.Lnurl.Pay,
        amount: MilliSatoshi,
        comment: String?,
        invoice: LnurlPay.Invoice,
        trampolineFees: TrampolineFees
    ) {
        payBolt11Invoice(
            amountToSend = amount,
            trampolineFees = trampolineFees,
            invoice = invoice.invoice,
            metadata = WalletPaymentMetadata(
                lnurl = LnurlPayMetadata(
                    pay = pay.paymentIntent,
                    description = pay.paymentIntent.metadata.plainText,
                    successAction = invoice.successAction
                ),
                userNotes = comment,
                lightningAddress = pay.lightningAddress
            )
        )
    }

    /**
     * Step 1 of 2:
     * First call this function to convert the LnurlWithdraw into a Bolt11Invoice.
     *
     * Note: This step is cancellable. The UI can simply ignore the result.
     */
    suspend fun lnurlWithdraw_createInvoice(
        lnurlWithdraw: LnurlWithdraw,
        amount: MilliSatoshi,
        description: String?
    ): Bolt11Invoice {
        return peerManager.getPeer().createInvoice(
            paymentPreimage = Lightning.randomBytes32(),
            amount = amount,
            description = Either.Left(description ?: lnurlWithdraw.defaultDescription),
            expiry = 7.days,
        )
    }

    /**
     * Step 2 of 2:
     * Sends the Bolt11Invoice to the corresponding host.
     *
     * Todo: We probably want to return a Deferred<LnurlWithdrawError?> here instead.
     *       That would make it cancellable (to a certain degree).
     */
    suspend fun lnurlWithdraw_sendInvoice(
        lnurlWithdraw: LnurlWithdraw,
        invoice: Bolt11Invoice
    ): LnurlWithdrawError? {
        val task = lnurlManager.sendWithdrawInvoice(
            lnurlWithdraw = lnurlWithdraw,
            paymentRequest = invoice
        )
        return try {
            task.await()
            null
        } catch (err: Throwable) {
            when (err) {
                is LnurlError.RemoteFailure -> {
                    LnurlWithdrawError.RemoteError(err)
                }
                else -> { // unexpected exception: map to generic error
                    LnurlWithdrawError.RemoteError(
                        LnurlError.RemoteFailure.Unreadable(
                            origin = lnurlWithdraw.callback.host
                        )
                    )
                }
            }
        }
    }

    suspend fun lnurlAuth_signAndSend(
        auth: LnurlAuth,
        minSuccessDelaySeconds: Double = 0.0,
        scheme: LnurlAuth.Scheme
    ): LnurlAuthError? {
        return withContext(Dispatchers.Default) {
            val start = TimeSource.Monotonic.markNow()
            val error = try {
                lnurlManager.signAndSendAuthRequest(
                    auth = auth,
                    scheme = scheme
                )
                null
            } catch (e: LnurlError.RemoteFailure.CouldNotConnect) {
                LnurlAuthError.NetworkError(details = e)
            } catch (e: LnurlError.RemoteFailure) {
                LnurlAuthError.ServerError(details = e)
            } catch (e: Throwable) {
                LnurlAuthError.OtherError(details = e)
            }
            if (error != null) {
                return@withContext error
            } else {
                val pending = minSuccessDelaySeconds.seconds - start.elapsedNow()
                if (pending > Duration.ZERO) {
                    delay(pending)
                }
                return@withContext null
            }
        }
    }
}