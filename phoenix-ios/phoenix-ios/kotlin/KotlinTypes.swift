import Foundation
import PhoenixShared

// Making Kotlin types more readable

extension Receive {
	
	typealias Model_Awaiting = ModelAwaiting
	typealias Model_Generating = ModelGenerating
	typealias Model_Generated = ModelGenerated
	typealias Model_SwapIn = ModelSwapIn
	typealias Model_SwapIn_Requesting = ModelSwapInRequesting
	typealias Model_SwapIn_Generated = ModelSwapInGenerated
}

extension Scan {
	typealias Model_Ready = ModelReady
	typealias Model_BadRequest = ModelBadRequest
	
	typealias Model_InvoiceFlow = ModelInvoiceFlow
	typealias Model_InvoiceFlow_DangerousRequest = ModelInvoiceFlowDangerousRequest
	typealias Model_InvoiceFlow_InvoiceRequest = ModelInvoiceFlowInvoiceRequest
	typealias Model_InvoiceFlow_Sending = ModelInvoiceFlowSending
	
	typealias Model_SwapOutFlow = ModelSwapOutFlow
	typealias Model_SwapOutFlow_Init = ModelSwapOutFlowInit
	typealias Model_SwapOutFlow_Requesting = ModelSwapOutFlowRequestingSwapout
	typealias Model_SwapOutFlow_Ready = ModelSwapOutFlowSwapOutReady
	typealias Model_SwapOutFlow_Sending = ModelSwapOutFlowSendingSwapOut
	
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
	
	typealias Intent_InvoiceFlow_ConfirmDangerousRequest = IntentInvoiceFlowConfirmDangerousRequest
	typealias Intent_InvoiceFlow_SendInvoicePayment = IntentInvoiceFlowSendInvoicePayment
	
	typealias Intent_SwapOutFlow_Invalidate = IntentSwapOutFlowInvalidate
	typealias Intent_SwapOutFlow_Prepare = IntentSwapOutFlowPrepareSwapOut
	typealias Intent_SwapOutFlow_Send = IntentSwapOutFlowSendSwapOut
	
	typealias Intent_CancelLnurlServiceFetch = IntentCancelLnurlServiceFetch
	
	typealias Intent_LnurlPayFlow_SendLnurlPayment = IntentLnurlPayFlowSendLnurlPayment
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
	
	typealias BadRequestReason_AlreadyPaidInvoice = BadRequestReasonAlreadyPaidInvoice
	typealias BadRequestReason_ChainMismatch = BadRequestReasonChainMismatch
	typealias BadRequestReason_InvalidLnUrl = BadRequestReasonInvalidLnUrl
	typealias BadRequestReason_ServiceError = BadRequestReasonServiceError
	typealias BadRequestReason_UnknownFormat = BadRequestReasonUnknownFormat
	typealias BadRequestReason_UnsupportedLnUrl = BadRequestReasonUnsupportedLnUrl
	
	typealias ClipboardContent_InvoiceRequest = ClipboardContentInvoiceRequest
	typealias ClipboardContent_BitcoinRequest = ClipboardContentBitcoinRequest
	typealias ClipboardContent_LoginRequest = ClipboardContentLoginRequest
	typealias ClipboardContent_LnurlRequest = ClipboardContentLnurlRequest
}

extension LNUrl {
	
	typealias PayInvoice_SuccessAction = PayInvoiceSuccessAction
	typealias PayInvoice_SuccessAction_Message = PayInvoiceSuccessActionMessage
	typealias PayInvoice_SuccessAction_Url = PayInvoiceSuccessActionUrl
	typealias PayInvoice_SuccessAction_Aes = PayInvoiceSuccessActionAes
	
	typealias PayInvoice_SuccessAction_Aes_Decrypted = PayInvoiceSuccessActionAesDecrypted
	
	typealias Error_RemoteFailure = ErrorRemoteFailure
	typealias Error_RemoteFailure_Code = ErrorRemoteFailureCode
	typealias Error_RemoteFailure_Detailed = ErrorRemoteFailureDetailed
	typealias Error_RemoteFailure_Unreadable = ErrorRemoteFailureUnreadable
	typealias Error_RemoteFailure_CouldNotConnect = ErrorRemoteFailureCouldNotConnect
	
	typealias Error_PayInvoice = ErrorPayInvoice
	typealias Error_PayInvoice_InvalidAmount = ErrorPayInvoiceInvalidAmount
	typealias Error_PayInvoice_InvalidHash = ErrorPayInvoiceInvalidHash
	typealias Error_PayInvoice_Malformed = ErrorPayInvoiceMalformed
	
	typealias AuthKeyType_DEFAULT_KEY_TYPE = AuthKeyTypeDEFAULT_KEY_TYPE
	typealias AuthKeyType_LEGACY_KEY_TYPE = AuthKeyTypeLEGACY_KEY_TYPE
}

extension LogsConfiguration {
	
	typealias Model_Awaiting = ModelAwaiting
	typealias Model_Exporting = ModelExporting
	typealias Model_Ready = ModelReady
	
	typealias Intent_Export = IntentExport
}
