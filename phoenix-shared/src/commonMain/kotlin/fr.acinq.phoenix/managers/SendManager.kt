package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.data.lnurl.Lnurl
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.data.lnurl.LnurlWithdraw
import fr.acinq.phoenix.utils.DnsResolvers
import fr.acinq.phoenix.utils.Parser
import io.ktor.http.Url
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

class SendManager(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val lnurlManager: LnurlManager,
    private val databaseManager: DatabaseManager,
    private val chain: Chain,
) {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        lnurlManager = business.lnurlManager,
        databaseManager = business.databaseManager,
        chain = business.chain
    )

    private val log = loggerFactory.newLogger(this::class)

    sealed class BadRequestReason : Exception() {
        object UnknownFormat : BadRequestReason()
        object AlreadyPaidInvoice : BadRequestReason()
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

    sealed class ParseProgress {
        data object LnurlServiceFetch: ParseProgress()
        data object ResolvingBip353: ParseProgress()
    }

    sealed class ParseResult {

        data class BadRequest(
            val request: String,
            val reason: BadRequestReason
        ): ParseResult()

        data class Bolt11Invoice(
            val request: String,
            val invoice: fr.acinq.lightning.payment.Bolt11Invoice
        ): ParseResult()

        data class Bolt12Offer(
            val offer: OfferTypes.Offer
        ): ParseResult()

        data class Uri(
            val uri: BitcoinUri
        ): ParseResult()

        sealed class Lnurl: ParseResult() {
            data class Pay(
                val paymentIntent: LnurlPay.Intent
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
                processOffer(it)
            } ?: readEmailLikeAddress(input, progress)?.let {
                when (it) {
                    is Either.Left -> processOffer(it.value)
                    is Either.Right -> processLnurl(it.value, progress)
                }
            } ?: readLnurl(input)?.let {
                processLnurl(it, progress)
            } ?: readBitcoinAddress(input)?.let {
                processBitcoinAddress(input, it)
            } ?: readLNURLFallback(input)?.let {
                processLnurl(it, progress)
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
        return if (db.payments.listLightningOutgoingPayments(invoice.paymentHash).any { it.status is LightningOutgoingPayment.Status.Completed.Succeeded }) {
            BadRequestReason.AlreadyPaidInvoice
        } else {
            null
        }
    }

    private fun processOffer(
        offer: OfferTypes.Offer
    ): ParseResult {

        return if (!offer.chains.contains(chain.chainHash)) {
            ParseResult.BadRequest(
                request = offer.encode(),
                reason = BadRequestReason.ChainMismatch(expected = chain)
            )
        } else {
            ParseResult.Bolt12Offer(offer = offer)
        }
    }

    @Throws(BadRequestReason::class, CancellationException::class)
    private suspend fun readEmailLikeAddress(
        input: String,
        progress: (p: ParseProgress) -> Unit
    ): Either<OfferTypes.Offer, Lnurl.Request>? {

        if (!input.contains("@", ignoreCase = true)) return null

        // Ignore excess input, including additional lines, and leading/trailing whitespace
        val line = input.lines().firstOrNull { it.isNotBlank() }?.trim()
        val token = line?.split("\\s+".toRegex())?.firstOrNull()

        if (token.isNullOrBlank()) return null

        val components = token.split("@")
        if (components.size != 2) {
            return null
        }

        val username = components[0].lowercase()
        val domain = components[1]

        val signalBip353 = username.startsWith("₿")
        val cleanUsername = username.dropWhile { it == '₿' }

        progress(ParseProgress.ResolvingBip353)
        val offer = resolveBip353Offer(cleanUsername, domain)
        return if (signalBip353) {
            offer?.let { Either.Left(it) } // skip lnurl resolution if it's a bip353 address
        } else {
            offer?.let { Either.Left(it) }
                ?: Either.Right(Lnurl.Request(Url("https://$domain/.well-known/lnurlp/$username"), tag = Lnurl.Tag.Pay))
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
                            ParseResult.Lnurl.Pay(paymentIntent = result)
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
                    bolt12 != null -> ParseResult.Bolt12Offer(offer = bolt12)
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
}