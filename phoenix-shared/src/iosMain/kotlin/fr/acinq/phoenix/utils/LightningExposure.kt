package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.ByteVector64
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.Script
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.TxIn
import fr.acinq.bitcoin.TxOut
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.DefaultSwapInParams
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.Lightning.randomKey
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.NodeEvents
import fr.acinq.lightning.SensitiveTaskEvents
import fr.acinq.lightning.SwapInParams
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumMiniWallet
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.ChannelFundingResponse
import fr.acinq.lightning.channel.states.Aborted
import fr.acinq.lightning.channel.states.ChannelState
import fr.acinq.lightning.channel.states.Closed
import fr.acinq.lightning.channel.states.Closing
import fr.acinq.lightning.channel.states.Offline
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.io.NativeSocketException
import fr.acinq.lightning.io.OfferInvoiceReceived
import fr.acinq.lightning.io.OfferNotPaid
import fr.acinq.lightning.io.PaymentNotSent
import fr.acinq.lightning.io.PaymentProgress
import fr.acinq.lightning.io.PaymentSent
import fr.acinq.lightning.io.PayOffer
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.io.PeerEvent
import fr.acinq.lightning.io.SendPaymentResult
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.copyTo
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteArray
import fr.acinq.lightning.utils.toNSData
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.lightning.wire.OfferTypes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import kotlin.time.Duration.Companion.seconds

/**
 * Class types from lightning-kmp & bitcoin-kmp are not exported to iOS unless we explicitly
 * reference them within PhoenixShared, either as a public parameter or return type.
 *
 * This problem is restricted to iOS, and does not affect Android.
 */

fun IncomingPayment.Origin.asInvoice(): IncomingPayment.Origin.Invoice? =
    (this as? IncomingPayment.Origin.Invoice)

fun IncomingPayment.Origin.asSwapIn(): IncomingPayment.Origin.SwapIn? =
    (this as? IncomingPayment.Origin.SwapIn)

fun IncomingPayment.Origin.asOnChain(): IncomingPayment.Origin.OnChain? =
    (this as? IncomingPayment.Origin.OnChain)

fun IncomingPayment.ReceivedWith.asLightningPayment():
        IncomingPayment.ReceivedWith.LightningPayment? =
    (this as? IncomingPayment.ReceivedWith.LightningPayment)

fun IncomingPayment.ReceivedWith.asNewChannel(): IncomingPayment.ReceivedWith.NewChannel? =
    (this as? IncomingPayment.ReceivedWith.NewChannel)

fun IncomingPayment.ReceivedWith.asSpliceIn(): IncomingPayment.ReceivedWith.SpliceIn? =
    (this as? IncomingPayment.ReceivedWith.SpliceIn)

fun LightningOutgoingPayment.outgoingPaymentFailure(): OutgoingPaymentFailure? {
    return (status as? LightningOutgoingPayment.Status.Completed.Failed)?.let { status ->
        OutgoingPaymentFailure(
            reason = status.reason,
            failures = parts.map { it.status }
                .filterIsInstance<LightningOutgoingPayment.Part.Status.Failed>()
        )
    }
}

fun LightningOutgoingPayment.explainAsPartFailure(): LightningOutgoingPayment.Part.Status.Failure? {
    return outgoingPaymentFailure()?.let { paymentFailure ->
        when (val result = paymentFailure.explain()) {
            is Either.Left -> result.value
            else -> null
        }
    }
}

fun LightningOutgoingPayment.explainAsFinalFailure(): FinalFailure? {
    return outgoingPaymentFailure()?.let { paymentFailure ->
        when (val result = paymentFailure.explain()) {
            is Either.Right -> result.value
            else -> null
        }
    }
}

fun LightningOutgoingPayment.Details.asNormal(): LightningOutgoingPayment.Details.Normal? =
    (this as? LightningOutgoingPayment.Details.Normal)

fun LightningOutgoingPayment.Details.asSwapOut(): LightningOutgoingPayment.Details.SwapOut? =
    (this as? LightningOutgoingPayment.Details.SwapOut)

fun LightningOutgoingPayment.Status.asPending(): LightningOutgoingPayment.Status.Pending? =
    (this as? LightningOutgoingPayment.Status.Pending)

fun LightningOutgoingPayment.Status.asFailed(): LightningOutgoingPayment.Status.Completed.Failed? =
    (this as? LightningOutgoingPayment.Status.Completed.Failed)

fun LightningOutgoingPayment.Status.asSucceeded():
        LightningOutgoingPayment.Status.Completed.Succeeded? =
    (this as? LightningOutgoingPayment.Status.Completed.Succeeded)

fun LightningOutgoingPayment.Status.asOffChain():
        LightningOutgoingPayment.Status.Completed.Succeeded.OffChain? =
    (this as? LightningOutgoingPayment.Status.Completed.Succeeded.OffChain)

fun ChannelState.asOffline(): Offline? = (this as? Offline)
fun ChannelState.asClosing(): Closing? = (this as? Closing)
fun ChannelState.asClosed(): Closed? = (this as? Closed)
fun ChannelState.asAborted(): Aborted? = (this as? Aborted)

fun TcpSocket.TLS.asDisabled(): TcpSocket.TLS.DISABLED? =
    (this as? TcpSocket.TLS.DISABLED)

fun TcpSocket.TLS.asTrustedCertificates(): TcpSocket.TLS.TRUSTED_CERTIFICATES? =
    (this as? TcpSocket.TLS.TRUSTED_CERTIFICATES)

fun TcpSocket.TLS.asPinnedPublicKey(): TcpSocket.TLS.PINNED_PUBLIC_KEY? =
    (this as? TcpSocket.TLS.PINNED_PUBLIC_KEY)

fun TcpSocket.TLS.asUnsafeCertificates(): TcpSocket.TLS.UNSAFE_CERTIFICATES? =
    (this as? TcpSocket.TLS.UNSAFE_CERTIFICATES)

fun Connection.asClosed(): Connection.CLOSED? = (this as? Connection.CLOSED)
fun Connection.asEstablishing(): Connection.ESTABLISHING? = (this as? Connection.ESTABLISHING)
fun Connection.asEstablished(): Connection.ESTABLISHED? = (this as? Connection.ESTABLISHED)

fun NativeSocketException.asPOSIX(): NativeSocketException.POSIX? =
    (this as? NativeSocketException.POSIX)

fun NativeSocketException.asDNS(): NativeSocketException.DNS? =
    (this as? NativeSocketException.DNS)

fun NativeSocketException.asTLS(): NativeSocketException.TLS? =
    (this as? NativeSocketException.TLS)

fun ElectrumMiniWallet.currentWalletState(): WalletState = this.walletStateFlow.value

fun NodeEvents.asChannelEvents(): ChannelEvents? = (this as? ChannelEvents)

fun ChannelEvents.asCreating(): ChannelEvents.Creating? = (this as? ChannelEvents.Creating)
fun ChannelEvents.asCreated(): ChannelEvents.Created? = (this as? ChannelEvents.Created)
fun ChannelEvents.asConfirmed(): ChannelEvents.Confirmed? = (this as? ChannelEvents.Confirmed)

fun LiquidityEvents.Rejected.Reason.asOverAbsoluteFee():
        LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee? =
    (this as? LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee)

fun LiquidityEvents.Rejected.Reason.asOverRelativeFee():
        LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee? =
    (this as? LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee)

fun LiquidityPolicy.asDisable(): LiquidityPolicy.Disable? = (this as? LiquidityPolicy.Disable)
fun LiquidityPolicy.asAuto(): LiquidityPolicy.Auto? = (this as? LiquidityPolicy.Auto)

fun ChannelFundingResponse.asSuccess(): ChannelFundingResponse.Success? =
    (this as? ChannelFundingResponse.Success)

fun ChannelFundingResponse.asFailure(): ChannelFundingResponse.Failure? =
    (this as? ChannelFundingResponse.Failure)

fun ChannelFundingResponse.Failure.asInsufficientFunds(): ChannelFundingResponse.Failure.InsufficientFunds? =
    (this as? ChannelFundingResponse.Failure.InsufficientFunds)

fun ChannelFundingResponse.Failure.asInvalidSpliceOutPubKeyScript(): ChannelFundingResponse.Failure.InvalidSpliceOutPubKeyScript? =
    (this as? ChannelFundingResponse.Failure.InvalidSpliceOutPubKeyScript)

fun ChannelFundingResponse.Failure.asSpliceAlreadyInProgress(): ChannelFundingResponse.Failure.SpliceAlreadyInProgress? =
    (this as? ChannelFundingResponse.Failure.SpliceAlreadyInProgress)

fun ChannelFundingResponse.Failure.asConcurrentRemoteSplice(): ChannelFundingResponse.Failure.ConcurrentRemoteSplice? =
    (this as? ChannelFundingResponse.Failure.ConcurrentRemoteSplice)

fun ChannelFundingResponse.Failure.asChannelNotQuiescent(): ChannelFundingResponse.Failure.ChannelNotQuiescent? =
    (this as? ChannelFundingResponse.Failure.ChannelNotQuiescent)

fun ChannelFundingResponse.Failure.asInvalidChannelParameters(): ChannelFundingResponse.Failure.InvalidChannelParameters? =
    (this as? ChannelFundingResponse.Failure.InvalidChannelParameters)

fun ChannelFundingResponse.Failure.asInvalidLiquidityAds(): ChannelFundingResponse.Failure.InvalidLiquidityAds? =
    (this as? ChannelFundingResponse.Failure.InvalidLiquidityAds)

fun ChannelFundingResponse.Failure.asFundingFailure(): ChannelFundingResponse.Failure.FundingFailure? =
    (this as? ChannelFundingResponse.Failure.FundingFailure)

fun ChannelFundingResponse.Failure.asCannotStartSession(): ChannelFundingResponse.Failure.CannotStartSession? =
    (this as? ChannelFundingResponse.Failure.CannotStartSession)

fun ChannelFundingResponse.Failure.asInteractiveTxSessionFailed(): ChannelFundingResponse.Failure.InteractiveTxSessionFailed? =
    (this as? ChannelFundingResponse.Failure.InteractiveTxSessionFailed)

fun ChannelFundingResponse.Failure.asCannotCreateCommitTx(): ChannelFundingResponse.Failure.CannotCreateCommitTx? =
    (this as? ChannelFundingResponse.Failure.CannotCreateCommitTx)

fun ChannelFundingResponse.Failure.asAbortedByPeer(): ChannelFundingResponse.Failure.AbortedByPeer? =
    (this as? ChannelFundingResponse.Failure.AbortedByPeer)

fun ChannelFundingResponse.Failure.asUnexpectedMessage(): ChannelFundingResponse.Failure.UnexpectedMessage? =
    (this as? ChannelFundingResponse.Failure.UnexpectedMessage)

fun ChannelFundingResponse.Failure.asDisconnected(): ChannelFundingResponse.Failure.Disconnected? =
    (this as? ChannelFundingResponse.Failure.Disconnected)

suspend fun ElectrumClient.kotlin_getConfirmations(txid: TxId): Int? {
    return this.getConfirmations(txid)
}

fun defaultSwapInParams(): SwapInParams {
    return SwapInParams(
        minConfirmations = DefaultSwapInParams.MinConfirmations,
        maxConfirmations = DefaultSwapInParams.MaxConfirmations,
        refundDelay = DefaultSwapInParams.RefundDelay
    )
}

fun SensitiveTaskEvents.asTaskStarted(): SensitiveTaskEvents.TaskStarted? =
    (this as? SensitiveTaskEvents.TaskStarted)

fun SensitiveTaskEvents.asTaskEnded(): SensitiveTaskEvents.TaskEnded? =
    (this as? SensitiveTaskEvents.TaskEnded)

fun SensitiveTaskEvents.TaskIdentifier.asInteractiveTx():
        SensitiveTaskEvents.TaskIdentifier.InteractiveTx? =
    (this as? SensitiveTaskEvents.TaskIdentifier.InteractiveTx)

fun PeerEvent.asPaymentProgress(): PaymentProgress? =
    (this as? PaymentProgress)

fun PeerEvent.asPaymentSent(): PaymentSent? =
    (this as? PaymentSent)

fun PeerEvent.asPaymentNotSent(): PaymentNotSent? =
    (this as? PaymentNotSent)

fun FinalFailure.asAlreadyPaid(): FinalFailure.AlreadyPaid? =
    (this as? FinalFailure.AlreadyPaid)

fun FinalFailure.asInvalidPaymentAmount(): FinalFailure.InvalidPaymentAmount? =
    (this as? FinalFailure.InvalidPaymentAmount)

fun FinalFailure.asFeaturesNotSupported(): FinalFailure.FeaturesNotSupported? =
    (this as? FinalFailure.FeaturesNotSupported)

fun FinalFailure.asInvalidPaymentId(): FinalFailure.InvalidPaymentId? =
    (this as? FinalFailure.InvalidPaymentId)

fun FinalFailure.asChannelNotConnected(): FinalFailure.ChannelNotConnected? =
    (this as? FinalFailure.ChannelNotConnected)

fun FinalFailure.asChannelOpening (): FinalFailure.ChannelOpening? =
    (this as? FinalFailure.ChannelOpening)

fun FinalFailure.asChannelClosing (): FinalFailure.ChannelClosing? =
    (this as? FinalFailure.ChannelClosing)

fun FinalFailure.asNoAvailableChannels (): FinalFailure.NoAvailableChannels? =
    (this as? FinalFailure.NoAvailableChannels)

fun FinalFailure.asInsufficientBalance (): FinalFailure.InsufficientBalance? =
    (this as? FinalFailure.InsufficientBalance)

fun FinalFailure.asRecipientUnreachable (): FinalFailure.RecipientUnreachable? =
    (this as? FinalFailure.RecipientUnreachable)

fun FinalFailure.asRetryExhausted (): FinalFailure.RetryExhausted? =
    (this as? FinalFailure.RetryExhausted)

fun FinalFailure.asWalletRestarted (): FinalFailure.WalletRestarted? =
    (this as? FinalFailure.WalletRestarted)

fun FinalFailure.asUnknownError (): FinalFailure.UnknownError? =
    (this as? FinalFailure.UnknownError)

fun LightningOutgoingPayment.Part.Status.Failure.asUninterpretable():
        LightningOutgoingPayment.Part.Status.Failure.Uninterpretable? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.Uninterpretable)

fun LightningOutgoingPayment.Part.Status.Failure.asChannelIsClosing():
        LightningOutgoingPayment.Part.Status.Failure.ChannelIsClosing? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.ChannelIsClosing)

fun LightningOutgoingPayment.Part.Status.Failure.asChannelIsSplicing():
        LightningOutgoingPayment.Part.Status.Failure.ChannelIsSplicing? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.ChannelIsSplicing)

fun LightningOutgoingPayment.Part.Status.Failure.asNotEnoughFees():
        LightningOutgoingPayment.Part.Status.Failure.NotEnoughFees? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.NotEnoughFees)

fun LightningOutgoingPayment.Part.Status.Failure.asNotEnoughFunds():
        LightningOutgoingPayment.Part.Status.Failure.NotEnoughFunds? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.NotEnoughFunds)

fun LightningOutgoingPayment.Part.Status.Failure.asPaymentAmountTooBig():
        LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooBig? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooBig)

fun LightningOutgoingPayment.Part.Status.Failure.asPaymentAmountTooSmall():
        LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooSmall? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooSmall)

fun LightningOutgoingPayment.Part.Status.Failure.asPaymentExpiryTooBig():
        LightningOutgoingPayment.Part.Status.Failure.PaymentExpiryTooBig? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.PaymentExpiryTooBig)

fun LightningOutgoingPayment.Part.Status.Failure.asRecipientRejectedPayment():
        LightningOutgoingPayment.Part.Status.Failure.RecipientRejectedPayment? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.RecipientRejectedPayment)

fun LightningOutgoingPayment.Part.Status.Failure.asRecipientIsOffline():
        LightningOutgoingPayment.Part.Status.Failure.RecipientIsOffline? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.RecipientIsOffline)

fun LightningOutgoingPayment.Part.Status.Failure.asRecipientLiquidityIssue():
        LightningOutgoingPayment.Part.Status.Failure.RecipientLiquidityIssue? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.RecipientLiquidityIssue)

fun LightningOutgoingPayment.Part.Status.Failure.asTemporaryRemoteFailure():
        LightningOutgoingPayment.Part.Status.Failure.TemporaryRemoteFailure? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.TemporaryRemoteFailure)

fun LightningOutgoingPayment.Part.Status.Failure.asTooManyPendingPayments():
        LightningOutgoingPayment.Part.Status.Failure.TooManyPendingPayments? =
    (this as? LightningOutgoingPayment.Part.Status.Failure.TooManyPendingPayments)

fun Lightning_randomBytes32(): ByteVector32 = Lightning.randomBytes32()
fun Lightning_randomBytes64(): ByteVector64 = Lightning.randomBytes64()
fun Lightning_randomKey(): PrivateKey = Lightning.randomKey()

fun NSData_toByteArray(data: NSData): ByteArray = data.toByteArray()
fun NSData_copyTo(data: NSData, buffer: ByteArray, offset: Int = 0) = data.copyTo(buffer, offset)
fun ByteArray_toNSDataSlice(buffer: ByteArray, offset: Int, length: Int): NSData = buffer.toNSData(offset = offset, length = length)
fun ByteArray_toNSData(buffer: ByteArray): NSData = buffer.toNSData()

fun WalletState.WalletWithConfirmations._spendExpiredSwapIn(
    swapInKeys: KeyManager.SwapInOnChainKeys,
    scriptPubKey: ByteVector,
    feerate: FeeratePerKw
): Pair<Transaction, Satoshi>? {
    return this.spendExpiredSwapIn(swapInKeys, scriptPubKey, feerate)
}

suspend fun Peer.fundingRate(amount: Satoshi): LiquidityAds.FundingRate? {
    return this.remoteFundingRates.filterNotNull().first().findRate(amount)
}

suspend fun Peer.altPayOffer(
    paymentId: UUID,
    amount: MilliSatoshi,
    offer: OfferTypes.Offer,
    payerKey: PrivateKey,
    payerNote: String?,
    fetchInvoiceTimeoutInSeconds: Int
): SendPaymentResult {
    val res = CompletableDeferred<SendPaymentResult>()
    this.launch {
        res.complete(eventsFlow
            .filterIsInstance<SendPaymentResult>()
            .filter { it.request.paymentId == paymentId }
            .first()
        )
    }
    send(PayOffer(paymentId, payerKey, payerNote, amount, offer, fetchInvoiceTimeoutInSeconds.seconds))
    return res.await()
}

suspend fun Peer.betterPayOffer(
    paymentId: UUID,
    amount: MilliSatoshi,
    offer: OfferTypes.Offer,
    payerKey: PrivateKey,
    payerNote: String?,
    fetchInvoiceTimeoutInSeconds: Int
): OfferNotPaid? {
    val res = CompletableDeferred<OfferNotPaid?>()
    launch {
        eventsFlow.collect {
            if (it is OfferNotPaid && it.request.paymentId == paymentId) {
                res.complete(it)
                cancel()
            } else if (it is OfferInvoiceReceived && it.request.paymentId == paymentId) {
                res.complete(null)
                cancel()
            }
        }
    }
    send(PayOffer(paymentId, payerKey, payerNote, amount, offer, fetchInvoiceTimeoutInSeconds.seconds))
    return res.await()
}
