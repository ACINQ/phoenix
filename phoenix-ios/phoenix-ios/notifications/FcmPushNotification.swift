import Foundation
import PhoenixShared

struct FcmPushNotification {
	let reason: Reason
	let nodeIdHash: String?
	
	enum Reason: CustomStringConvertible {
		case incomingPayment
		case incomingOnionMessage
		case pendingSettlement
		case unknown
		
		var description: String { switch self {
			case .incomingPayment      : "incomingPayment"
			case .incomingOnionMessage : "incomingOnionMessage"
			case .pendingSettlement    : "pendingSettlement"
			case .unknown              : "unknown"
		}}
	}
}
