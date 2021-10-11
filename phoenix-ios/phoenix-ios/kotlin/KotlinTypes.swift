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
	
	typealias Model_InvoiceFlow_DangerousRequest = ModelInvoiceFlowDangerousRequest
	typealias Model_InvoiceFlow_InvoiceRequest = ModelInvoiceFlowInvoiceRequest
	typealias Model_InvoiceFlow_Sending = ModelInvoiceFlowSending
	
	typealias Model_LnurlServiceFetch = ModelLnurlServiceFetch
	
	typealias Model_LnurlPayFlow_LnurlPayRequest = ModelLnurlPayFlowLnurlPayRequest
	typealias Model_LnurlPayFlow_LnUrlPayFetch = ModelLnurlPayFlowLnUrlPayFetch
	typealias Model_LnurlPayFlow_Sending = ModelLnurlPayFlowSending
	
	typealias Model_LnurlAuthFlow_LoginRequest = ModelLnurlAuthFlowLoginRequest
	typealias Model_LnurlAuthFlow_LoggingIn = ModelLnurlAuthFlowLoggingIn
	typealias Model_LnurlAuthFlow_LoginResult = ModelLnurlAuthFlowLoginResult
	
	typealias Intent_Parse = IntentParse
	
	typealias Intent_InvoiceFlow_ConfirmDangerousRequest = IntentInvoiceFlowConfirmDangerousRequest
	typealias Intent_InvoiceFlow_SendInvoicePayment = IntentInvoiceFlowSendInvoicePayment
	
	typealias Intent_CancelLnurlServiceFetch = IntentCancelLnurlServiceFetch
	
	typealias Intent_LnurlPayFlow_SendLnurlPayment = IntentLnurlPayFlowSendLnurlPayment
	typealias Intent_LnurlPayFlow_CancelLnurlPayment = IntentLnurlPayFlowCancelLnurlPayment
	
	typealias Intent_LnurlAuthFlow_Login = IntentLnurlAuthFlowLogin
	
	typealias LNUrlPay_Error_RemoteError = LNUrlPayErrorRemoteError
	typealias LNUrlPay_Error_BadResponseError = LNUrlPayErrorBadResponseError
	typealias LNUrlPay_Error_ChainMismatch = LNUrlPayErrorChainMismatch
	typealias LNUrlPay_Error_AlreadyPaidInvoice = LNUrlPayErrorAlreadyPaidInvoice
}

extension LNUrl {
	
	typealias PayInvoice_SuccessAction = PayInvoiceSuccessAction
	typealias PayInvoice_SuccessAction_Message = PayInvoiceSuccessActionMessage
	typealias PayInvoice_SuccessAction_Url = PayInvoiceSuccessActionUrl
	typealias PayInvoice_SuccessAction_Aes = PayInvoiceSuccessActionAes
	
	typealias PayInvoice_SuccessAction_Aes_Decrypted = PayInvoiceSuccessActionAesDecrypted
}
