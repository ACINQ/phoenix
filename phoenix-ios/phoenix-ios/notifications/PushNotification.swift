import Foundation
import PhoenixShared

fileprivate let filename = "PushNotification"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class PushNotification {
	
	static func isFCM(userInfo: [AnyHashable : Any]) -> Bool {
		
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
	
	static func parseWithdrawRequest(userInfo: [AnyHashable : Any]) -> WithdrawRequest? {
		log.trace("parseWithdrawRequest()")
		
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
			log.debug("parseLnurlWithdraw: missing one or more parameters")
			return nil
		}
		
		guard t == "withdraw" else {
			log.debug("parseLnurlWithdraw: t != withdraw")
			return nil
		}
		
		guard let piccData = Data(fromHex: picc) else {
			log.debug("parseLnurlWithdraw: picc is not hexadecimal")
			return nil
		}
				
		guard let cmacData = Data(fromHex: cmac) else {
			log.debug("parseLnurlWithdraw: cmac is not hexadecimal")
			return nil
		}
		
		return WithdrawRequest(
			nodeId    : n,
			piccData  : piccData,
			cmac      : cmacData,
			invoice   : invc,
			timestamp : ts.toDate(from: .milliseconds)
		)
	}
}