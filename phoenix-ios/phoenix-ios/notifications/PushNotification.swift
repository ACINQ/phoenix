import Foundation
import PhoenixShared

fileprivate let filename = "PushNotification"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PushNotification {
	let source: Source
	let reason: Reason
	let nodeId: String?
	let nodeIdHash: String?
	let chain: Bitcoin_kmpChain?
	
	enum Source: CustomStringConvertible {
		case googleFCM
		case aws
		
		var description: String {
			return switch self {
				case .googleFCM : "googleFCM"
				case .aws       : "aws"
			}
		}
	}
	
	enum Reason: CustomStringConvertible {
		case incomingPayment
		case incomingOnionMessage
		case pendingSettlement
		case unknown
		
		var description: String {
			return switch self {
				case .incomingPayment      : "incomingPayment"
				case .incomingOnionMessage : "incomingOnionMessage"
				case .pendingSettlement    : "pendingSettlement"
				case .unknown              : "unknown"
			}
		}
	}
	
	static func parse(_ userInfo: [AnyHashable: Any]) -> PushNotification {
		log.trace(#function)
		
		// This could be a push notification coming from either:
		// - Google's Firebase Cloud Messaging (FCM)
		// - Amazon Web Services (AWS) (only used for debugging)
		
		if isFCM(userInfo) {
			return parse_fcm(userInfo)
		} else {
			return parse_aws(userInfo)
		}
	}
	
	private static func isFCM(_ userInfo: [AnyHashable: Any]) -> Bool {
			
		/* This could be a push notification coming from Google Firebase or from AWS.
		 *
		 * Example from Google FCM:
		 * {
		 *   "aps": {
		 *     "alert": {
		 *       "title": "foobar"
		 *     }
		 *     "mutable-content": 1
		 *   },
		 *   "reason": "IncomingPayment",
		 *   "gcm.message_id": 1676919817341932,
		 *   "google.c.a.e": 1,
		 *   "google.c.fid": "f7Wfr_yqG00Gt6B9O7qI13",
		 *   "google.c.sender.id": 358118532563
		 * }
		 *
		 * Example from AWS:
		 * {
		 *   "aps": {
		 *     "alert": {
		 *       "title": "Missed incoming payment"
		 *     }
		 *     "mutable-content": 1
		 *   },
		 *   "acinq": {
		 *     "amt": 120000,
		 *     "h": "d48bf163c0e24d68567e80b10cc7dd583e2f44390c9592df56a61f79559611e6",
		 *     "n": "02ed721545840184d1544328059e8b20c01965b73b301a7d03fc89d3d84aba0642",
		 *     "t": "invoice",
		 *     "ts": 1676920273561
		 *   }
		 * }
		 */
		
		return userInfo["gcm.message_id"]     != nil ||
				 userInfo["google.c.a.e"]       != nil ||
				 userInfo["google.c.fid"]       != nil ||
				 userInfo["google.c.sender.id"] != nil ||
				 userInfo["reason"]             != nil // just in-case google changes format
	}
	
	private static func parse_fcm(_ userInfo: [AnyHashable: Any]) -> PushNotification {
		log.trace(#function)
		
		// Example:
		// {
		//   "gcm.message_id": 1605136272123442,
		//   "google.c.sender.id": 458618232423,
		//   "google.c.a.e": 1,
		//   "google.c.fid": "dRLLO-mxUxbDvmV1urj5Tt",
		//   "reason": "IncomingPayment",
		//   "aps": {
		//     "alert": {
		//       "title": "Missed incoming payment",
		//     },
		//     "mutable-content": 1
		//   }
		// }
		
		let reason: Reason
		if let reasonStr = userInfo["reason"] as? String {
			log.debug("userInfo.reason: '\(reasonStr)'")
			
			// The server currently sends the string "IncomingOnionMessage$",
			// which is a minor bug that will probably be fixed soon.
			// So we support both the fixed & unfixed version.
			
			switch reasonStr {
				case "IncomingPayment"       : reason = .incomingPayment
				case "IncomingOnionMessage$" : reason = .incomingOnionMessage
				case "IncomingOnionMessage"  : reason = .incomingOnionMessage
				case "PendingSettlement"     : reason = .pendingSettlement
				default                      : reason = .unknown
			}
		} else {
			log.debug("userInfo.reason: !string")
			reason = .unknown
		}
		
		log.debug("reason = \(reason)")
		
		let nodeId: String?
		if let value = userInfo["nodeId"] as? String {
			nodeId = value
		} else if let value = userInfo["node"] as? String {
			nodeId = value
		} else if let value = userInfo["n"] as? String {
			nodeId = value
		} else {
			nodeId = nil
		}
		log.debug("userInfo.nodeId = \(nodeId ?? "<nil>")")
		
		let chainStr: String?
		if let value = userInfo["chain"] as? String {
			chainStr = value
		} else if let value = userInfo["c"] as? String {
			chainStr = value
		} else {
			chainStr = nil
		}
		log.debug("userInfo.chain = \(chainStr ?? "<nil>")")
		
		let nodeIdHash: String? = if let nodeId { calculateNodeIdHash(nodeId) } else { nil }
		
		let chain: Bitcoin_kmpChain? = if let chainStr {
			Bitcoin_kmpChain.fromString(chainStr)
		} else {
			Bitcoin_kmpChain.Mainnet()
		}
		
		if let chainStr, chain == nil {
			log.warning("Invalid chain name: \(chainStr)")
		}
		
		return PushNotification(
			source     : .googleFCM,
			reason     : reason,
			nodeId     : nodeId?.lowercased(),
			nodeIdHash : nodeIdHash,
			chain      : chain
		)
	}
	
	private static func parse_aws(_ userInfo: [AnyHashable: Any]) -> PushNotification {
		
		let reason: Reason = .unknown // AWS only used for debugging (e.g. testing new features)
		
		let nodeId: String?
		if let value = userInfo["n"] as? String {
			nodeId = value
		} else {
			nodeId = nil
		}
		log.debug("userInfo.n(odeId) = \(nodeId ?? "<nil>")")
		
		let chainStr: String?
		if let value = userInfo["c"] as? String {
			chainStr = value
		} else {
			chainStr = nil
		}
		log.debug("userInfo.c(hain) = \(chainStr ?? "<nil>")")
		
		let nodeIdHash: String? = if let nodeId { calculateNodeIdHash(nodeId) } else { nil }
		
		let chain: Bitcoin_kmpChain? = if let chainStr {
			Bitcoin_kmpChain.fromString(chainStr)
		} else {
			Bitcoin_kmpChain.Mainnet()
		}
		
		if let chainStr, chain == nil {
			log.warning("Invalid chain name: \(chainStr)")
		}
		
		return PushNotification(
			source     : .aws,
			reason     : reason,
			nodeId     : nodeId?.lowercased(),
			nodeIdHash : nodeIdHash,
			chain      : chain
		)
	}
	
	private static func calculateNodeIdHash(_ nodeId: String) -> String? {
		
		switch hash160(nodeId: nodeId) {
		case .success(let value):
			return value
		
		case .failure(let reason):
			log.warning("hash160(): \(reason.description)")
			return nil
		}
	}
}
