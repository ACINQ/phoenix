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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Feature
import fr.acinq.lightning.Features
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.secure
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.SwapOutRequest
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.LNUrlManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.PublicSuffixList
import fr.acinq.phoenix.utils.chain
import fr.acinq.phoenix.utils.createTrampolineFees
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
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
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is SwapOutResponseEvent -> {
                        val currentModel = models.value
                        if (currentModel is Scan.Model.SwapOutFlow.RequestingSwapout) {
                            model(
                                Scan.Model.SwapOutFlow.SwapOutReady(
                                    address = currentModel.address,
                                    initialUserAmount = event.swapOutResponse.amount,
                                    fee = event.swapOutResponse.fee,
                                    paymentRequest = PaymentRequest.read(event.swapOutResponse.paymentRequest)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> launch { processScannedInput(intent) }
            is Scan.Intent.InvoiceFlow.ConfirmDangerousRequest -> launch { confirmAmountlessInvoice(intent) }
            is Scan.Intent.InvoiceFlow.SendInvoicePayment -> launch {
                sendPayment(
                    amountToSend = intent.amount,
                    paymentRequest = intent.paymentRequest,
                    customMaxFees = intent.maxFees,
                    metadata = null,
                    swapOutData = null
                )
                model(Scan.Model.InvoiceFlow.Sending)
            }
            is Scan.Intent.CancelLnurlServiceFetch -> launch { cancelLnurlFetch() }
            is Scan.Intent.LnurlPayFlow.SendLnurlPayment -> launch { processLnurlPay(intent) }
            is Scan.Intent.LnurlPayFlow.CancelLnurlPayment -> launch { cancelLnurlPay(intent) }
            is Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw -> launch { processLnurlWithdraw(intent) }
            is Scan.Intent.LnurlWithdrawFlow.CancelLnurlWithdraw -> launch { cancelLnurlWithdraw(intent) }
            is Scan.Intent.LnurlAuthFlow.Login -> launch { processLnurlAuth(intent) }
            is Scan.Intent.SwapOutFlow.Invalidate -> launch { model(Scan.Model.SwapOutFlow.Init(intent.address)) }
            is Scan.Intent.SwapOutFlow.PrepareSwapOut -> launch { prepareSwapOutTransaction(intent) }
            is Scan.Intent.SwapOutFlow.SendSwapOut -> launch {
                sendPayment(
                    amountToSend = intent.amount.toMilliSatoshi(),
                    paymentRequest = intent.paymentRequest,
                    customMaxFees = intent.maxFees,
                    metadata = null,
                    swapOutData = intent.swapOutFee to intent.address.address
                )
                model(
                    Scan.Model.SwapOutFlow.SendingSwapOut(
                        address = intent.address,
                        paymentRequest = intent.paymentRequest
                    )
                )
            }
        }
    }

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
            paymentRequest = paymentRequest
        )
        model(model)
    }

    /** Return the adequate model for a Bitcoin address result. */
    private suspend fun processBitcoinAddress(data: Either<BitcoinAddressError, BitcoinAddressInfo>) {
        logger.info { "processing bitcoin address=$data" }
        val model = when (data) {
            is Either.Right -> Scan.Model.SwapOutFlow.Init(address = data.value)
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

    private suspend fun processLnurlData(data: Either<LNUrl.Auth, Url>) {
        when (data) {
            // lnurl-auths are not called automatically. The user must initiate the request.
            is Either.Left -> {
                // proceed to lnurl-auth flow
                prefetchPublicSuffixListTask = appConfigManager.fetchPublicSuffixListAsync()
                model(Scan.Model.LnurlAuthFlow.LoginRequest(auth = data.value))
            }
            // this is a standard url, and it can be executed immediately in order to get the actual
            // lnurl details from the service (the service should return either a LNUrl.Pay or a LNUrl.Withdraw).
            is Either.Right -> {
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
                when {
                    result == null -> {} // do nothing, this request has been cancelled.
                    result is Either.Left -> model(Scan.Model.BadRequest(result.value))
                    result is Either.Right -> { // result: LNUrl
                        when (val lnurl = result.value) {
                            is LNUrl.Pay -> {
                                model(
                                    Scan.Model.LnurlPayFlow.LnurlPayRequest(
                                        lnurlPay = lnurl,
                                        error = null
                                    )
                                )
                            }
                            is LNUrl.Withdraw -> {
                                model(
                                    Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest(
                                        lnurlWithdraw = lnurl,
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
        model(
            Scan.Model.InvoiceFlow.InvoiceRequest(
                request = intent.request,
                paymentRequest = intent.paymentRequest,
            )
        )
    }

    /** Extract invoice and send it to the Peer to make the payment, attaching custom trampoline fees if needed. */
    private suspend fun sendPayment(
        amountToSend: MilliSatoshi,
        paymentRequest: PaymentRequest,
        customMaxFees: MaxFees?,
        metadata: WalletPaymentMetadata?,
        swapOutData: Pair<Satoshi, String>?,
    ) {
        val paymentId = UUID.randomUUID()
        val peer = peerManager.getPeer()

        // save lnurl metadata if any
        metadata?.let { WalletPaymentMetadataRow.serialize(it) }?.let { row ->
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
            if (swapOutData != null) {
                SendPaymentSwapOut(
                    paymentId = paymentId,
                    amount = amountToSend,
                    recipient = paymentRequest.nodeId,
                    details = OutgoingPayment.Details.SwapOut(
                        address = swapOutData.second,
                        paymentRequest = paymentRequest,
                        swapOutFee = swapOutData.first
                    ),
                    trampolineFeesOverride = trampolineFees
                )
            } else {
                SendPaymentNormal(
                    paymentId = paymentId,
                    amount = amountToSend,
                    recipient = paymentRequest.nodeId,
                    details = OutgoingPayment.Details.Normal(
                        paymentRequest = paymentRequest
                    ),
                    trampolineFeesOverride = trampolineFees
                )
            }
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
        model(Scan.Model.LnurlPayFlow.LnurlPayFetch(lnurlPay = intent.lnurlPay))
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
                model(
                    Scan.Model.LnurlPayFlow.LnurlPayRequest(
                        lnurlPay = intent.lnurlPay,
                        error = result.value
                    )
                )
            }
            is Either.Right -> {
                sendPayment(
                    amountToSend = intent.amount,
                    paymentRequest = result.value.paymentRequest,
                    customMaxFees = intent.maxFees,
                    metadata = WalletPaymentMetadata(
                        lnurl = LnurlPayMetadata(
                            pay = intent.lnurlPay,
                            description = intent.lnurlPay.metadata.plainText,
                            successAction = result.value.successAction
                        ),
                        userNotes = intent.comment
                    ),
                    swapOutData = null,
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
        model(
            Scan.Model.LnurlPayFlow.LnurlPayRequest(
                lnurlPay = intent.lnurlPay,
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

    private suspend fun prepareSwapOutTransaction(
        intent: Scan.Intent.SwapOutFlow.PrepareSwapOut
    ) {
        val feeRate = FeeratePerKw(FeeratePerByte((appConfigManager.chainContext.value?.swapOut?.v1?.minFeerateSatByte ?: 20).sat))
        peerManager.getPeer().sendToPeer(
            SwapOutRequest(
                chainHash = chain.chainHash,
                amount = intent.amount,
                bitcoinAddress = intent.address.address,
                feePerKw = feeRate.toLong()
            )
        )
        model(Scan.Model.SwapOutFlow.RequestingSwapout(intent.address))
    }

    /** Directly called by swift code in iOS app. Parses the data looking for a Lightning invoice, Lnurl, or Bitcoin address. */
    fun inspectClipboard(data: String): Scan.ClipboardContent? {
        val input = Parser.removeExcessInput(data)

        return Parser.readPaymentRequest(input)?.let {
            Scan.ClipboardContent.InvoiceRequest(it)
        } ?: readLNURL(input)?.let {
            when (it) {
                is Either.Left -> Scan.ClipboardContent.LoginRequest(it.value)
                is Either.Right -> Scan.ClipboardContent.LnurlRequest(it.value)
            }
        } ?: Parser.readBitcoinAddress(chain, input).let {
            when (it) {
                is Either.Left -> null
                is Either.Right -> Scan.ClipboardContent.BitcoinRequest(it.value)
            }
        }
    }

    /** Checks that the invoice is on same chain and has not already been paid. */
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

    /** Checks for payment request that should not be made: amountless invoice without trampoline ; pay-to-self... */
    private suspend fun checkForDangerousRequest(pr: PaymentRequest): Scan.DangerousRequestReason? = when {
        pr.amount == null && !Features(pr.features).hasFeature(Feature.TrampolinePayment) -> Scan.DangerousRequestReason.IsAmountlessInvoice
        pr.nodeId == peerManager.getPeer().nodeParams.nodeId -> Scan.DangerousRequestReason.IsOwnInvoice
        else -> null
    }

    /** Reads a lnurl and return either a lnurl-auth (i.e. a http query that must not be called automatically), or the actual url embedded in the lnurl (that can be called afterwards). */
    private fun readLNURL(input: String): Either<LNUrl.Auth, Url>? = try {
        lnurlManager.interactiveExtractLnurl(Parser.trimMatchingPrefix(input, listOf("lightning://", "lightning:")))
    } catch (t: Throwable) {
        null
    }
}
