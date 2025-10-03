import Foundation
import PhoenixShared

struct CardResponse: Hashable {
	let code: Int64
	let message: String
	let requestId: Bitcoin_kmpByteVector
	
	var errorCode: ErrorCode? {
		return ErrorCode(rawValue: code)
	}
	
	static func fromOnionMessage(_ onionMsg: Lightning_kmp_coreCardPaymentResponseReceived) -> CardResponse {
		
		let rawMsg = onionMsg.message
		
		let splits = rawMsg.split(separator: ":", maxSplits: 1)
		if splits.count == 2 {
			// The first item should be an integer
			let codeStr = splits[0].trimmingCharacters(in: .whitespacesAndNewlines)
			if let code = Int64.init(codeStr) {
				let msg = splits[1].trimmingCharacters(in: .whitespacesAndNewlines)
				
				return CardResponse(code: code, message: msg, requestId: onionMsg.requestId)
			}
		}
		
		// Couldn't parse a code, so will use default value
		return CardResponse(code: 0, message: rawMsg, requestId: onionMsg.requestId)
	}
	
	/// Standardized error codes from specification.
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
