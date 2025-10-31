import Foundation
import PhoenixShared
import CryptoKit

fileprivate let filename = "LnurlWithdrawNotification"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LnurlWithdrawNotification {
	let nodeId: String
	let piccData: Data
	let cmac: Data
	let invoice: Lightning_kmpBolt11Invoice
	let invoiceAmount: Lightning_kmpMilliSatoshi
	let invoiceString: String
	let timestamp: Date
	let withdrawHash: String
	
	init(
		nodeId: String,
		piccData: Data,
		cmac: Data,
		invoice: Lightning_kmpBolt11Invoice,
		invoiceAmount: Lightning_kmpMilliSatoshi,
		invoiceString: String,
		timestamp: Date
	) {
		self.nodeId = nodeId
		self.piccData = piccData
		self.cmac = cmac
		self.invoice = invoice
		self.invoiceAmount = invoiceAmount
		self.invoiceString = invoiceString
		self.timestamp = timestamp
		self.withdrawHash = Self.calculateWithdrawHash(
			nodeId: nodeId, piccData: piccData, cmac: cmac, invoice: invoiceString
		)
	}
	
	/// The withdrawHash is used by the server to refer to the request.
	/// When we post a response, we send the hash (as opposed to sending the entire request).
	///
	/// We are expected to calculate the withdrawHash in the same way the server does.
	/// So do not change this method unless you change the server code also.
	///
	private static func calculateWithdrawHash(
		nodeId   : String,
		piccData : Data,
		cmac     : Data,
		invoice  : String
	) -> String {
		
		var hashMe = Data()
		hashMe.append(nodeId.lowercased().data(using: .utf8)!)
		hashMe.append(piccData.toHex(.lowerCase).data(using: .utf8)!)
		hashMe.append(cmac.toHex(.lowerCase).data(using: .utf8)!)
		hashMe.append(invoice.data(using: .utf8)!)
		
		let digest = SHA256.hash(data: hashMe)
		return digest.toHex(.lowerCase)
	}
	
	func postResponse(errorReason: String?) async -> Bool {
		log.trace("postResponse(\(errorReason ?? "<nil>"))")
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlw/response")!
		
		var body: [String: String] = [
			"node_id"       : self.nodeId,
			"withdraw_hash" : self.withdrawHash,
		]
		if let errorReason {
			body["err_message"] = errorReason
		}
		
		let bodyData = try? JSONSerialization.data(
			withJSONObject: body,
			options: []
		)
		
		var request = URLRequest(url: url)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		do {
			log.debug("/v1/pub/lnurlw/response: sending...")
			let (data, response) = try await URLSession.shared.data(for: request)
			
			var statusCode = 418
			var success = false
			if let httpResponse = response as? HTTPURLResponse {
				statusCode = httpResponse.statusCode
				if statusCode >= 200 && statusCode < 300 {
					success = true
				}
			}
			
			if success {
				log.debug("/v1/pub/lnurlw/response: success")
			} else {
				log.debug("/v1/pub/lnurlw/response: statusCode: \(statusCode)")
				if let dataString = String(data: data, encoding: .utf8) {
					log.debug("/v1/pub/lnurlw/response: response:\n\(dataString)")
				}
			}
			
			return success
		} catch {
			log.debug("/v1/pub/lnurlw/response: error: \(String(describing: error))")
			return false
		}
	}
	
	func toWithdrawRequest() -> WithdrawRequest {
		return WithdrawRequest(
			piccData: self.piccData,
			cmac: self.cmac,
			method: .bolt11Invoice(invoice: self.invoice),
			amount: self.invoiceAmount
		)
	}
}
