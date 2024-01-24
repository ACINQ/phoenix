import Foundation
import PhoenixShared

// Making Kotlin types more readable

extension Receive {
	typealias Model_Awaiting = ModelAwaiting
	typealias Model_Generating = ModelGenerating
	typealias Model_Generated = ModelGenerated
	typealias Model_SwapIn = ModelSwapIn
}

extension Scan {
	typealias Model_Ready = ModelReady
	typealias Model_BadRequest = ModelBadRequest

	typealias Model_InvoiceFlow = ModelInvoiceFlow
	typealias Model_InvoiceFlow_InvoiceRequest = ModelInvoiceFlowInvoiceRequest
	typealias Model_InvoiceFlow_Sending = ModelInvoiceFlowSending

	typealias Model_OnChainFlow = ModelOnchainFlow

	typealias Model_LnurlServiceFetch = ModelLnurlServiceFetch

	typealias Model_LnurlPayFlow = ModelLnurlPayFlow
	typealias Model_LnurlPayFlow_LnurlPayRequest = ModelLnurlPayFlowLnurlPayRequest
	typealias Model_LnurlPayFlow_LnurlPayFetch = ModelLnurlPayFlowLnurlPayFetch
	typealias Model_LnurlPayFlow_Sending = ModelLnurlPayFlowSending

	typealias Model_LnurlWithdrawFlow = ModelLnurlWithdrawFlow
	typealias Model_LnurlWithdrawFlow_LnurlWithdrawRequest = ModelLnurlWithdrawFlowLnurlWithdrawRequest
	typealias Model_LnurlWithdrawFlow_LnurlWithdrawFetch = ModelLnurlWithdrawFlowLnurlWithdrawFetch
	typealias Model_LnurlWithdrawFlow_Receiving = ModelLnurlWithdrawFlowReceiving

	typealias Model_LnurlAuthFlow = ModelLnurlAuthFlow
	typealias Model_LnurlAuthFlow_LoginRequest = ModelLnurlAuthFlowLoginRequest
	typealias Model_LnurlAuthFlow_LoggingIn = ModelLnurlAuthFlowLoggingIn
	typealias Model_LnurlAuthFlow_LoginResult = ModelLnurlAuthFlowLoginResult

	typealias Intent_Parse = IntentParse

	typealias Intent_InvoiceFlow_SendInvoicePayment = IntentInvoiceFlowSendInvoicePayment

	typealias Intent_CancelLnurlServiceFetch = IntentCancelLnurlServiceFetch

	typealias Intent_LnurlPayFlow_RequestInvoice = IntentLnurlPayFlowRequestInvoice
	typealias Intent_LnurlPayFlow_CancelLnurlPayment = IntentLnurlPayFlowCancelLnurlPayment

	typealias Intent_LnurlWithdrawFlow_SendLnurlWithdraw = IntentLnurlWithdrawFlowSendLnurlWithdraw
	typealias Intent_LnurlWithdrawFlow_CancelLnurlWithdraw = IntentLnurlWithdrawFlowCancelLnurlWithdraw

	typealias Intent_LnurlAuthFlow_Login = IntentLnurlAuthFlowLogin

	typealias LnurlPay_Error = LnurlPayError
	typealias LnurlPay_Error_RemoteError = LnurlPayErrorRemoteError
	typealias LnurlPay_Error_BadResponseError = LnurlPayErrorBadResponseError
	typealias LnurlPay_Error_ChainMismatch = LnurlPayErrorChainMismatch
	typealias LnurlPay_Error_AlreadyPaidInvoice = LnurlPayErrorAlreadyPaidInvoice

	typealias LnurlWithdraw_Error = LnurlWithdrawError
	typealias LnurlWithdraw_Error_RemoteError = LnurlWithdrawErrorRemoteError

	typealias BadRequestReason_Expired = BadRequestReasonExpired
	typealias BadRequestReason_AlreadyPaidInvoice = BadRequestReasonAlreadyPaidInvoice
	typealias BadRequestReason_ChainMismatch = BadRequestReasonChainMismatch
	typealias BadRequestReason_InvalidLnurl = BadRequestReasonInvalidLnurl
	typealias BadRequestReason_ServiceError = BadRequestReasonServiceError
	typealias BadRequestReason_UnknownFormat = BadRequestReasonUnknownFormat
	typealias BadRequestReason_UnsupportedLnurl = BadRequestReasonUnsupportedLnurl

	typealias ClipboardContent_InvoiceRequest = ClipboardContentInvoiceRequest
	typealias ClipboardContent_BitcoinRequest = ClipboardContentBitcoinRequest
	typealias ClipboardContent_LoginRequest = ClipboardContentLoginRequest
	typealias ClipboardContent_LnurlRequest = ClipboardContentLnurlRequest
}

extension LnurlPay {
	typealias Invoice_SuccessAction = LnurlPay.InvoiceSuccessAction
	typealias Invoice_SuccessAction_Message = LnurlPay.InvoiceSuccessActionMessage
	typealias Invoice_SuccessAction_Url = LnurlPay.InvoiceSuccessActionUrl
	typealias Invoice_SuccessAction_Aes = LnurlPay.InvoiceSuccessActionAes
	typealias Invoice_SuccessAction_Aes_Decrypted = LnurlPay.InvoiceSuccessActionAesDecrypted
}

extension LnurlError {
	typealias RemoteFailure = LnurlError.RemoteFailure
	typealias RemoteFailure_Code = LnurlError.RemoteFailureCode
	typealias RemoteFailure_Detailed = LnurlError.RemoteFailureDetailed
	typealias RemoteFailure_Unreadable = LnurlError.RemoteFailureUnreadable
	typealias RemoteFailure_CouldNotConnect = LnurlError.RemoteFailureCouldNotConnect

	typealias Pay_Invoice = LnurlError.PayInvoice
	typealias Pay_Invoice_InvalidAmount = LnurlError.PayInvoiceInvalidAmount
	typealias Pay_Invoice_Malformed = LnurlError.PayInvoiceMalformed
}

extension LnurlAuth {
	typealias Scheme_DEFAULT = LnurlAuth.SchemeDEFAULT_SCHEME
	typealias Scheme_ANDROID_LEGACY = LnurlAuth.SchemeANDROID_LEGACY_SCHEME
}
