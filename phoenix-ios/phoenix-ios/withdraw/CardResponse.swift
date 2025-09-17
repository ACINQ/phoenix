import Foundation
import PhoenixShared

struct CardResponse: Hashable {
	let code: Int64
	let message: String
	
	var errorCode: ErrorCode? {
		return ErrorCode(rawValue: code)
	}
	
	static func fromOnionMessage(_ msg: Lightning_kmp_coreCardPaymentResponseReceived) -> CardResponse {
		return CardResponse(
			code: msg.code,
			message: msg.msg
		)
	}
	
	static func fromWithdrawRequestError(_ err: WithdrawRequestError) -> CardResponse {
		let code: ErrorCode = switch err {
			case .unknownCard                : ErrorCode.unknownCard
			case .replayDetected(_)          : ErrorCode.replayDetected
			case .frozenCard(_)              : ErrorCode.frozenCard
			case .dailyLimitExceeded(_, _)   : ErrorCode.limitExceeded
			case .monthlyLimitExceeded(_, _) : ErrorCode.limitExceeded
			case .badInvoice(_, _)           : ErrorCode.badInvoice
			case .alreadyPaidInvoice(_)      : ErrorCode.alreadyPaidInvoice
			case .paymentPending(_)          : ErrorCode.paymentPending
			case .internalError(_, _)        : ErrorCode.internalError
		}
		
		return CardResponse(code: code.rawValue, message: err.description)
	}
	
	enum ErrorCode: Int64 {
		case unknownCard          = 1
		case replayDetected       = 2
		case frozenCard           = 3
		case limitExceeded        = 4
		case badInvoice           = 5
		case alreadyPaidInvoice   = 6
		case paymentPending       = 7
		case internalError        = 8
	}
}
