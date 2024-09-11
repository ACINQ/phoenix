/*
 * Copyright 2020 ACINQ SAS
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

import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.io.PayInvoice
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.data.LnurlPayMetadata
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.lnurl.Lnurl
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.data.lnurl.LnurlWithdraw
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.LnurlManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.DnsResolvers
import fr.acinq.phoenix.utils.EmailLikeAddress
import fr.acinq.phoenix.utils.Parser
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AppScanController(
    loggerFactory: LoggerFactory,
    firstModel: Scan.Model?,
    private val peerManager: PeerManager,
    private val lnurlManager: LnurlManager,
    private val databaseManager: DatabaseManager,
    private val chain: Chain,
) : AppController<Scan.Model, Scan.Intent>(
    loggerFactory = loggerFactory,
    firstModel = firstModel ?: Scan.Model.Ready
) {

    /** Arbitrary identifier used to track the current lnurl task. Those tasks are asynchronous and can be cancelled. We use this field to track which one is in progress. */
    private var lnurlRequestId = 1

    /** Tracks the task fetching information for a given lnurl. Use this field to cancel the task (see [cancelLnurlFetch]). */
    private var continueLnurlTask: Deferred<Lnurl>? = null

    /** Tracks the task requesting an invoice to a Lnurl service. Use this field to cancel the task (see [cancelLnurlPay]). */
    private var requestPayInvoiceTask: Deferred<LnurlPay.Invoice>? = null

    /** Tracks the task that send an invoice we generated to a Lnurl service, in order to make a withdrawal. Use this field to cancel the task (see [cancelLnurlWithdraw]). */
    private var sendWithdrawInvoiceTask: Deferred<JsonObject>? = null

    constructor(business: PhoenixBusiness, firstModel: Scan.Model?) : this(
        loggerFactory = business.loggerFactory,
        firstModel = firstModel,
        peerManager = business.peerManager,
        lnurlManager = business.lnurlManager,
        databaseManager = business.databaseManager,
        chain = business.chain,
    )

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Reset -> launch { model(Scan.Model.Ready) }
            is Scan.Intent.Parse -> launch { processScannedInput(intent) }
            is Scan.Intent.Bolt11InvoiceFlow.SendBolt11Invoice -> launch {
                payBolt11Invoice(
                    amountToSend = intent.amount,
                    trampolineFees = intent.trampolineFees,
                    invoice = intent.invoice,
                    metadata = null,
                )
                model(Scan.Model.Bolt11InvoiceFlow.Sending)
            }
            is Scan.Intent.CancelLnurlServiceFetch -> launch { cancelLnurlFetch() }
            is Scan.Intent.LnurlPayFlow.RequestInvoice -> launch { processLnurlPayRequestInvoice(intent) }
            is Scan.Intent.LnurlPayFlow.CancelLnurlPayment -> launch { cancelLnurlPay(intent) }
            is Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw -> launch { processLnurlWithdraw(intent) }
            is Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw -> launch { cancelLnurlWithdraw(intent) }
            is Scan.Intent.LnurlAuthFlow.Login -> launch { processLnurlAuth(intent) }
        }
    }

    private suspend fun processScannedInput(
        intent: Scan.Intent.Parse
    ) {
        val input = Parser.removeExcessInput(intent.request)

        try {
            Parser.readBolt11Invoice(input)?.let {
                processBolt11Invoice(it)
            } ?: Parser.readOffer(input)?.let {
                processOffer(it)
            } ?: readEmailLikeAddress(input)?.let {
                when (it) {
                    is Either.Left -> processOffer(it.value)
                    is Either.Right -> processLnurl(it.value)
                }
            } ?: readLnurl(input)?.let {
                processLnurl(it)
            } ?: readBitcoinAddress(input)?.let {
                processBitcoinAddress(input, it)
            } ?: readLNURLFallback(input)?.let {
                processLnurl(it)
            } ?: run {
                model(Scan.Model.BadRequest(request = intent.request, reason = Scan.BadRequestReason.UnknownFormat))
            }
        } catch (e: Exception) {
            if (e is Scan.BadRequestReason) {
                model(Scan.Model.BadRequest(request = intent.request, reason = e))
            } else {
                model(Scan.Model.BadRequest(request = intent.request, reason = Scan.BadRequestReason.UnknownFormat))
            }
        }
    }

    /** Inspects the Lightning invoice for errors and update the model with the adequate value. */
    private suspend fun processBolt11Invoice(invoice: Bolt11Invoice) {
        val model = checkForBadBolt11Invoice(invoice)?.let {
            Scan.Model.BadRequest(request = invoice.write(), reason = it)
        } ?: Scan.Model.Bolt11InvoiceFlow.Bolt11InvoiceRequest(
            request = invoice.write(),
            invoice = invoice,
        )
        model(model)
    }

    /** Inspects the offer for errors and update the model with the adequate value. */
    private suspend fun processOffer(offer: OfferTypes.Offer) {
        if (!offer.chains.contains(chain.chainHash)) {
            model(Scan.Model.BadRequest(request = offer.encode(), reason = Scan.BadRequestReason.ChainMismatch(expected = chain)))
        } else {
            model(Scan.Model.OfferFlow(offer))
        }
    }

    /** Return the adequate model for a Bitcoin address result. */
    private suspend fun processBitcoinAddress(
        input: String,
        result: Either<BitcoinUriError, BitcoinUri>
    ) {
        model(when (result) {
            is Either.Right -> {
                val address = result.value.address
                val bolt11 = result.value.paymentRequest
                val bolt12 = result.value.offer
                when {
                    address.isNotBlank() -> Scan.Model.OnchainFlow(uri = result.value)
                    bolt11 != null -> Scan.Model.Bolt11InvoiceFlow.Bolt11InvoiceRequest(request = input, invoice = bolt11)
                    bolt12 != null -> Scan.Model.OfferFlow(offer = bolt12)
                    else -> Scan.Model.BadRequest(request = input, reason = Scan.BadRequestReason.UnknownFormat)
                }
            }
            is Either.Left -> {
                val error = result.value
                if (error is BitcoinUriError.InvalidScript && error.error is BitcoinError.ChainHashMismatch) {
                    Scan.Model.BadRequest(request = input, reason = Scan.BadRequestReason.ChainMismatch(expected = chain))
                } else {
                    Scan.Model.BadRequest(request = input, reason = Scan.BadRequestReason.UnknownFormat)
                }
            }
        })
    }

    /** Utility method wrapping a cancellable lnurl task and updating the requestId field. */
    private suspend fun <T, U> executeLnurlAction(action: suspend () -> Either<T, U>): Either<T, U>? {
        val requestId = lnurlRequestId
        val result = action()
        return if (requestId == lnurlRequestId) {
            result
        } else {
            null
        }
    }

    private suspend fun processLnurl(lnurl: Lnurl) {
        when (lnurl) {
            is LnurlAuth -> {
                model(Scan.Model.LnurlAuthFlow.LoginRequest(auth = lnurl))
            }
            // this lnurl is a standard url that must be executed immediately in order to get the actual
            // details from the service (the service should return either a LnurlPay or a LnurlWithdraw).
            is Lnurl.Request -> {
                val url = lnurl.initialUrl
                model(Scan.Model.LnurlServiceFetch)
                val result = executeLnurlAction {
                    val task = lnurlManager.executeLnurl(url)
                    continueLnurlTask = task
                    try {
                        Either.Right(task.await())
                    } catch (e: Exception) {
                        logger.error(e) { "failed to process lnurl=$lnurl" }
                        when (e) {
                            is LnurlError.RemoteFailure -> Either.Left(Scan.BadRequestReason.ServiceError(url, e))
                            else -> Either.Left(Scan.BadRequestReason.InvalidLnurl(url))
                        }
                    }
                }
                when (result) {
                    null -> Unit
                    is Either.Left -> model(Scan.Model.BadRequest(request = url.toString(), reason = result.value))
                    is Either.Right -> { // result: Lnurl
                        when (val res = result.value) {
                            is LnurlPay.Intent -> {
                                model(Scan.Model.LnurlPayFlow.LnurlPayRequest(paymentIntent = res, error = null))
                            }
                            is LnurlWithdraw -> {
                                model(Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(lnurlWithdraw = res, error = null))
                            }
                            else -> {
                                model(Scan.Model.BadRequest(request = url.toString(), reason = Scan.BadRequestReason.UnsupportedLnurl(url)))
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    /** Extract invoice and send it to the Peer to make the payment, attaching custom trampoline fees if needed. */
    private suspend fun payBolt11Invoice(
        amountToSend: MilliSatoshi,
        trampolineFees: TrampolineFees,
        invoice: Bolt11Invoice,
        metadata: WalletPaymentMetadata?,
    ) {
        val paymentId = UUID.randomUUID()
        val peer = peerManager.getPeer()

        // save lnurl metadata if any
        metadata?.let { WalletPaymentMetadataRow.serialize(it) }?.let { row ->
            databaseManager.paymentsDb().enqueueMetadata(
                row = row,
                id = WalletPaymentId.LightningOutgoingPaymentId(paymentId)
            )
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

    /** Cancel the current lnurl task fetching data from a service. */
    private suspend fun cancelLnurlFetch() {
        lnurlRequestId += 1
        continueLnurlTask?.cancel()
        continueLnurlTask = null
        model(Scan.Model.Ready)
    }

    private suspend fun processLnurlPayRequestInvoice(
        intent: Scan.Intent.LnurlPayFlow.RequestInvoice
    ) {
        model(Scan.Model.LnurlPayFlow.LnurlPayFetch(paymentIntent = intent.paymentIntent))
        val result = executeLnurlAction {
            val task = lnurlManager.requestPayInvoice(
                intent = intent.paymentIntent,
                amount = intent.amount,
                comment = intent.comment
            )
            requestPayInvoiceTask = task
            try {
                val invoice = task.await()
                when (checkForBadBolt11Invoice(invoice.invoice)) {
                    is Scan.BadRequestReason.ChainMismatch -> Either.Left(
                        Scan.LnurlPayError.ChainMismatch(expected = chain)
                    )
                    is Scan.BadRequestReason.AlreadyPaidInvoice -> Either.Left(
                        Scan.LnurlPayError.AlreadyPaidInvoice
                    )
                    else -> Either.Right(invoice)
                }
            } catch (err: Throwable) {
                when (err) {
                    is LnurlError.RemoteFailure -> Either.Left(
                        Scan.LnurlPayError.RemoteError(err)
                    )
                    is LnurlError.Pay.Invoice -> Either.Left(
                        Scan.LnurlPayError.BadResponseError(err)
                    )
                    else -> Either.Left(
                        Scan.LnurlPayError.RemoteError(LnurlError.RemoteFailure.Unreadable(origin = intent.paymentIntent.callback.host))
                    )
                }
            }
        }

        when (result) {
            null -> Unit
            is Either.Left -> {
                logger.info { "lnurl-pay has failed with result=$result" }
                model(
                    Scan.Model.LnurlPayFlow.LnurlPayRequest(
                        paymentIntent = intent.paymentIntent,
                        error = result.value
                    )
                )
            }
            is Either.Right -> {
                payBolt11Invoice(
                    amountToSend = intent.amount,
                    trampolineFees = intent.trampolineFees,
                    invoice = result.value.invoice,
                    metadata = WalletPaymentMetadata(
                        lnurl = LnurlPayMetadata(
                            pay = intent.paymentIntent,
                            description = intent.paymentIntent.metadata.plainText,
                            successAction = result.value.successAction
                        ),
                        userNotes = intent.comment
                    ),
                )
                model(Scan.Model.LnurlPayFlow.Sending(intent.paymentIntent))
            }
        }
    }

    private suspend fun cancelLnurlPay(
        intent: Scan.Intent.LnurlPayFlow.CancelLnurlPayment
    ) {
        lnurlRequestId += 1
        requestPayInvoiceTask?.cancel()
        requestPayInvoiceTask = null
        model(
            Scan.Model.LnurlPayFlow.LnurlPayRequest(
                paymentIntent = intent.lnurlPay,
                error = null
            )
        )
    }

    private suspend fun processLnurlWithdraw(
        intent: Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw
    ) {
        val requestId = lnurlRequestId
        run { // scoping
            model(
                Scan.Model.LnurlWithdrawFlow.LnurlWithdrawFetch(
                    lnurlWithdraw = intent.lnurlWithdraw
                )
            )
        }

        val paymentRequest = peerManager.getPeer().createInvoice(
            paymentPreimage = Lightning.randomBytes32(),
            amount = intent.amount,
            description = Either.Left(intent.description ?: intent.lnurlWithdraw.defaultDescription),
            expirySeconds = (3600 * 24 * 7).toLong(), // one week
        )

        if (requestId != lnurlRequestId) {
            // Intent.LnurlWithdrawFlow.CancelLnurlWithdraw has been issued
            return
        }
        val task = lnurlManager.sendWithdrawInvoice(
            lnurlWithdraw = intent.lnurlWithdraw,
            paymentRequest = paymentRequest
        )
        sendWithdrawInvoiceTask = task
        val error: Scan.LnurlWithdrawError? = try {
            task.await()
            null
        } catch (err: Throwable) {
            when (err) {
                is LnurlError.RemoteFailure -> {
                    Scan.LnurlWithdrawError.RemoteError(err)
                }
                else -> { // unexpected exception: map to generic error
                    Scan.LnurlWithdrawError.RemoteError(
                        LnurlError.RemoteFailure.Unreadable(
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
            model(
                Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                    lnurlWithdraw = intent.lnurlWithdraw,
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
        model(
            Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                lnurlWithdraw = intent.lnurlWithdraw,
                error = null
            )
        )
    }

    private suspend fun processLnurlAuth(
        intent: Scan.Intent.LnurlAuthFlow.Login
    ) {
        withContext(Dispatchers.Default) {
            model(Scan.Model.LnurlAuthFlow.LoggingIn(auth = intent.auth))
            val start = TimeSource.Monotonic.markNow()
            val error = try {
                lnurlManager.signAndSendAuthRequest(
                    auth = intent.auth,
                    scheme = intent.scheme
                )
                null
            } catch (e: LnurlError.RemoteFailure.CouldNotConnect) {
                Scan.LoginError.NetworkError(details = e)
            } catch (e: LnurlError.RemoteFailure) {
                Scan.LoginError.ServerError(details = e)
            } catch (e: Throwable) {
                Scan.LoginError.OtherError(details = e)
            }
            if (error != null) {
                model(Scan.Model.LnurlAuthFlow.LoginResult(auth = intent.auth, error = error))
            } else {
                val pending = intent.minSuccessDelaySeconds.seconds - start.elapsedNow()
                if (pending > Duration.ZERO) {
                    delay(pending)
                }
                model(Scan.Model.LnurlAuthFlow.LoginResult(auth = intent.auth, error = error))
            }
        }
    }

    /** Directly called by swift code in iOS app. Parses the data looking for a Lightning invoice, Lnurl, or Bitcoin address. */
    fun inspectClipboard(data: String): Scan.ClipboardContent? {
        val input = Parser.removeExcessInput(data)

        return Parser.readBolt11Invoice(input)?.let {
            Scan.ClipboardContent.Bolt11InvoiceRequest(it)
        } ?: readLnurl(input)?.let {
            when (it) {
                is LnurlAuth -> Scan.ClipboardContent.LoginRequest(it)
                is Lnurl.Request -> Scan.ClipboardContent.LnurlRequest(it.initialUrl)
                else -> null
            }
        } ?: readBitcoinAddress(input)?.let {
            when (it) {
                is Either.Left -> null
                is Either.Right -> Scan.ClipboardContent.BitcoinRequest(it.value)
            }
        } ?: readLNURLFallback(input)?.let {
            when (it) {
                is LnurlAuth -> Scan.ClipboardContent.LoginRequest(it)
                is Lnurl.Request -> Scan.ClipboardContent.LnurlRequest(it.initialUrl)
                else -> null
            }
        }
    }

    /** Checks that the invoice is on same chain and has not already been paid. */
    private suspend fun checkForBadBolt11Invoice(
        invoice: Bolt11Invoice
    ): Scan.BadRequestReason? {

        val actualChain = invoice.chain
        if (chain != actualChain) {
            return Scan.BadRequestReason.ChainMismatch(expected = chain)
        }

        if (invoice.isExpired(currentTimestampSeconds())) {
            return Scan.BadRequestReason.Expired(invoice.timestampSeconds, invoice.expirySeconds ?: Bolt11Invoice.DEFAULT_EXPIRY_SECONDS.toLong())
        }

        val db = databaseManager.databases.filterNotNull().first()
        return if (db.payments.listLightningOutgoingPayments(invoice.paymentHash).any { it.status is LightningOutgoingPayment.Status.Completed.Succeeded }) {
            Scan.BadRequestReason.AlreadyPaidInvoice
        } else {
            null
        }
    }

    private suspend fun readEmailLikeAddress(input: String): Either<OfferTypes.Offer, Lnurl.Request>? {

        val address = Parser.parseEmailLikeAddress(input) ?: return null

        return when (address) {
            is EmailLikeAddress.Bip353 -> resolveBip353Offer(address.username, address.domain)?.let { Either.Left(it) }
            is EmailLikeAddress.LnurlBased -> Either.Right(address.url)
            is EmailLikeAddress.UnknownType -> {
                resolveBip353Offer(address.username.dropWhile { it == '₿' }, address.domain)?.let { Either.Left(it) }
                    ?: Either.Right(EmailLikeAddress.LnurlBased(address.source, address.username, address.domain).url)
            }
        }
    }

    /** Resolve dns-based offers. See https://github.com/bitcoin/bips/blob/master/bip-0353.mediawiki. */
    private suspend fun resolveBip353Offer(
        username: String,
        domain: String,
    ): OfferTypes.Offer? {
        model(Scan.Model.ResolvingBip353)
        val dnsPath = "$username.user._bitcoin-payment.$domain."

        val json = DnsResolvers.getRandom().getTxtRecord(dnsPath)
        logger.debug { "dns resolved to ${json.toString().take(100)}" }

        val status = json["Status"]?.jsonPrimitive?.intOrNull
        // could be a [BadRequestReason.Bip353NameNotFound] it status == 3
        if (status == null || status > 0) return null

        val records = json["Answer"]?.jsonArray
        if (records.isNullOrEmpty()) {
            logger.debug { "no answer for $dnsPath" }
            // TODO add test (see #599)
            return null
        }

        // check dnssec
        val ad = json["AD"]?.jsonPrimitive?.booleanOrNull
        if (ad != true) {
            logger.debug { "AD false, abort dns lookup" }
            throw Scan.BadRequestReason.Bip353NoDNSSEC(dnsPath)
        }

        // check name matches records
        val matchingRecord = records.filterIsInstance<JsonObject>().firstOrNull {
            logger.debug { "inspecting record=$it" }
            it["name"]?.jsonPrimitive?.content == dnsPath
        } ?: throw Scan.BadRequestReason.Bip353NameNotFound(username, domain)

        val data = matchingRecord["data"]?.jsonPrimitive?.content ?: throw Scan.BadRequestReason.Bip353InvalidUri(dnsPath)
        return when (val res = Parser.parseBip21Uri(chain, data)) {
            is Either.Left -> {
                val error = res.value
                if (error is BitcoinUriError.InvalidScript && error.error is BitcoinError.ChainHashMismatch) {
                    throw Scan.BadRequestReason.ChainMismatch(expected = chain)
                } else {
                    throw Scan.BadRequestReason.Bip353InvalidUri(dnsPath)
                }
            }
            is Either.Right -> res.value.offer ?: throw Scan.BadRequestReason.Bip353InvalidOffer(dnsPath)
        }
    }

    /** Reads a lnurl and return either a lnurl-auth (i.e. a http query that must not be called automatically), or the actual url embedded in the lnurl (that can be called afterwards). */
    private fun readLnurl(input: String): Lnurl? = try {
        Lnurl.extractLnurl(input, logger)
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

    /**
     * Support for LNURL Fallback Scheme,
     * e.g. as used by Bitcoin Beach Wallet's static Paycode QR.
     * https://github.com/ACINQ/phoenix/issues/323
     */
    private fun readLNURLFallback(input: String): Lnurl? = try {
        val url = Url(input)
        url.parameters["lightning"]?.let { fallback ->
            Lnurl.extractLnurl(fallback, logger)
        }
    } catch (t: Throwable) {
        null
    }
}
