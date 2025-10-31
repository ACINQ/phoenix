import Foundation
import PhoenixShared

fileprivate let filename = "PushNotification"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

enum PushNotification {
	case fcm(notification: FcmPushNotification)
	case lnurlWithdraw(notification: LnurlWithdrawNotification)
	
	static func parse(_ userInfo: [AnyHashable: Any]) -> PushNotification? {
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
	
	private static func parse_fcm(_ userInfo: [AnyHashable: Any]) -> PushNotification? {
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
		
		let reason: FcmPushNotification.Reason
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
		
		let nodeIdHash: String?
		if let value = userInfo["node_id_hash"] as? String {
			nodeIdHash = value
		} else {
			nodeIdHash = nil
		}
		log.debug("userInfo.nodeIdHash = \(nodeIdHash ?? "<nil>")")
		
		return PushNotification.fcm(notification: FcmPushNotification(
			reason     : reason,
			nodeIdHash : nodeIdHash?.lowercased()
		))
	}
	
	private static func parse_aws(_ userInfo: [AnyHashable: Any]) -> PushNotification? {
		log.trace(#function)
		
		// Function reserved for other debugging uses.
		// We sometimes trigger custom push notifications from AWS during debug sessions.
		
		return parse_aws_lnurlWithdraw(userInfo: userInfo)
	}
	
	private static func parse_aws_lnurlWithdraw(userInfo: [AnyHashable : Any]) -> PushNotification? {
		log.trace(#function)
		
		// It should look like this:
		//
		// acinq: {
		//   t    : "withdraw",
		//   n    : "<node_id>",
		//   picc : "<picc_data_hex_string>",
		//   cmac : "<cmac_hex_string>",
		//   invc : "<bolt_11_invoice>",
		//   ts   : <timestamp_in_milliseconds>
		// }
		
		guard
			let acinq = userInfo["acinq"] as? [String: Any],
			let t     = acinq["t"]    as? String,
			let n     = acinq["n"]    as? String,
			let picc  = acinq["picc"] as? String,
			let cmac  = acinq["cmac"] as? String,
			let invc  = acinq["invc"] as? String,
			let ts    = acinq["ts"]   as? Int64
		else {
			log.debug("\(#function): missing one or more parameters")
			return nil
		}
		
		guard t == "withdraw" else {
			log.debug("\(#function): t != withdraw")
			return nil
		}
		guard let piccData = Data(fromHex: picc) else {
			log.debug("\(#function): picc is not hexadecimal")
			return nil
		}
		guard let cmacData = Data(fromHex: cmac) else {
			log.debug("\(#function): cmac is not hexadecimal")
			return nil
		}
		guard let invoice = Parser.shared.readBolt11Invoice(input: invc) else {
			log.debug("\(#function): invc is not Bolt11Invoice")
			return nil
		}
		guard let invoiceAmount = invoice.amount else {
			log.debug("\(#function): invoice.amount is nil")
			return nil
		}
		
		return PushNotification.lnurlWithdraw(notification: LnurlWithdrawNotification(
			nodeId        : n,
			piccData      : piccData,
			cmac          : cmacData,
			invoice       : invoice,
			invoiceAmount : invoiceAmount,
			invoiceString : invc,
			timestamp     : ts.toDate(from: .milliseconds)
		))
	}
}
