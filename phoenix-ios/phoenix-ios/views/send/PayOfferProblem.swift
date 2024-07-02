import Foundation
import PhoenixShared

enum PayOfferProblem {
	case noResponse
	case errorFromRecipient
	case invoiceMismatch
	case malformedResponse
	case other
	
	func localizedDescription() -> String {
		
		switch self {
		case .noResponse:
			return String(localized: "Could not retrieve payment details within a reasonable time. The recipient may be offline or unreachable.")
		case .errorFromRecipient: fallthrough
		case .invoiceMismatch: fallthrough
		case .malformedResponse:
			return String(localized: "Could not retrieve payment details. Received invalid response from node.")
		case .other:
			return String(localized: "Unknown offer error")
		}
	}
	
	static func fromResponse(
		_ response: Lightning_kmpOfferNotPaid?
	) -> PayOfferProblem? {
		
		guard let response else {
			return nil
		}
		
		switch onEnum(of: response.reason) {
			case .noResponse(_)         : return PayOfferProblem.noResponse
			case .errorFromRecipient(_) : return PayOfferProblem.errorFromRecipient
			case .invoiceMismatch(_)    : return PayOfferProblem.invoiceMismatch
			case .malformedResponse(_)  : return PayOfferProblem.malformedResponse
		}
	}
}
