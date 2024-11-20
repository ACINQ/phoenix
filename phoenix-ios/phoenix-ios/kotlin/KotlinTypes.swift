import Foundation
import PhoenixShared

// Making Kotlin types more readable

extension Receive {
	typealias Model_Awaiting = ModelAwaiting
	typealias Model_Generating = ModelGenerating
	typealias Model_Generated = ModelGenerated
}

extension SendManager {
	
	typealias ParseProgress_LnurlServiceFetch = ParseProgressLnurlServiceFetch
	typealias ParseProgress_ResolvingBip353 = ParseProgressResolvingBip353
	
	typealias ParseResult_BadRequest = ParseResultBadRequest
	typealias ParseResult_Bolt11Invoice = ParseResultBolt11Invoice
	typealias ParseResult_Bolt12Offer = ParseResultBolt12Offer
	typealias ParseResult_Uri = ParseResultUri
	typealias ParseResult_Lnurl = ParseResultLnurl
	typealias ParseResult_Lnurl_Pay = ParseResultLnurlPay
	typealias ParseResult_Lnurl_Withdraw = ParseResultLnurlWithdraw
	typealias ParseResult_Lnurl_Auth = ParseResultLnurlAuth
	
	typealias BadRequestReason_AlreadyPaidInvoice = BadRequestReasonAlreadyPaidInvoice
	typealias BadRequestReason_PaymentPending = BadRequestReasonPaymentPending
	typealias BadRequestReason_Bip353InvalidOffer = BadRequestReasonBip353InvalidOffer
	typealias BadRequestReason_Bip353InvalidUri = BadRequestReasonBip353InvalidUri
	typealias BadRequestReason_Bip353NameNotFound = BadRequestReasonBip353NameNotFound
	typealias BadRequestReason_Bip353NoDNSSEC = BadRequestReasonBip353NoDNSSEC
	typealias BadRequestReason_ChainMismatch = BadRequestReasonChainMismatch
	typealias BadRequestReason_Expired = BadRequestReasonExpired
	typealias BadRequestReason_InvalidLnurl = BadRequestReasonInvalidLnurl
	typealias BadRequestReason_ServiceError = BadRequestReasonServiceError
	typealias BadRequestReason_UnknownFormat = BadRequestReasonUnknownFormat
	typealias BadRequestReason_UnsupportedLnurl = BadRequestReasonUnsupportedLnurl
	
	typealias LnurlPay_Error = LnurlPayError
	typealias LnurlPay_Error_RemoteError = LnurlPayErrorRemoteError
	typealias LnurlPay_Error_BadResponseError = LnurlPayErrorBadResponseError
	typealias LnurlPay_Error_ChainMismatch = LnurlPayErrorChainMismatch
	typealias LnurlPay_Error_AlreadyPaidInvoice = LnurlPayErrorAlreadyPaidInvoice
	typealias LnurlPay_Error_PaymentPending = LnurlPayErrorPaymentPending
	
	typealias LnurlWithdraw_Error = LnurlWithdrawError
	typealias LnurlWithdraw_Error_RemoteError = LnurlWithdrawErrorRemoteError
	
	typealias LnurlAuth_Error = LnurlAuthError
	typealias LnurlAuth_Error_ServerError = LnurlAuthErrorServerError
	typealias LnurlAuth_Error_NetworkError = LnurlAuthErrorNetworkError
	typealias LnurlAuth_Error_OtherError = LnurlAuthErrorOtherError
}

extension LnurlPay {
	typealias Invoice_SuccessAction = LnurlPay.InvoiceSuccessAction
	typealias Invoice_SuccessAction_Message = LnurlPay.InvoiceSuccessActionMessage
	typealias Invoice_SuccessAction_Url = LnurlPay.InvoiceSuccessActionUrl
	typealias Invoice_SuccessAction_Aes = LnurlPay.InvoiceSuccessActionAes
	typealias Invoice_SuccessAction_Aes_Decrypted = LnurlPay.InvoiceSuccessActionAesDecrypted
}

extension LnurlError {
	typealias RemoteFailure_Code = RemoteFailureCode
	typealias RemoteFailure_Detailed = RemoteFailureDetailed
	typealias RemoteFailure_Unreadable = RemoteFailureUnreadable
	typealias RemoteFailure_CouldNotConnect = RemoteFailureCouldNotConnect
	typealias RemoteFailure_LightningAddressError = RemoteFailureLightningAddressError
	typealias RemoteFailure_IsWebsite = RemoteFailureIsWebsite

	typealias Pay_Invoice = LnurlError.PayInvoice
	typealias Pay_Invoice_InvalidAmount = LnurlError.PayInvoiceInvalidAmount
	typealias Pay_Invoice_Malformed = LnurlError.PayInvoiceMalformed
}

extension LnurlAuth {
	typealias Scheme_DEFAULT = LnurlAuth.SchemeDEFAULT_SCHEME
	typealias Scheme_ANDROID_LEGACY = LnurlAuth.SchemeANDROID_LEGACY_SCHEME
}

extension Lightning_kmpIncomingPayment {
	
	typealias ReceivedWith_LightningPayment = ReceivedWithLightningPayment
	typealias ReceivedWith_AddedToFeeCredit = ReceivedWithAddedToFeeCredit
	typealias ReceivedWith_OnChainIncomingPayment = ReceivedWithOnChainIncomingPayment
	
	typealias ReceivedWith_SpliceIn = ReceivedWithSpliceIn
	typealias ReceivedWith_NewChannel = ReceivedWithNewChannel
}
