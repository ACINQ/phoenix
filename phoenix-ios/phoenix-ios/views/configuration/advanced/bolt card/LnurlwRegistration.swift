import Foundation

fileprivate let filename = "LnurlwRegistration"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// Developer Note:
/// This registration process will **NOT** be needed after we develop the new protocol.
///
class LnurlwRegistration {
	
	struct LnurlWithdrawRegisterResponse: Decodable {
		let node_id: String
		let hex_addr: String
	}
	
	static func existingRegistration() -> LnurlWithdrawRegistration? {
		log.trace("existingRegistration()")
		
		guard let nodeIdHash: String = Biz.nodeIdHash else {
			return nil
		}
		
		if let prvRegistration = Prefs.shared.lnurlWithdrawRegistration {
			if prvRegistration.nodeIdHash == nodeIdHash {
				// We've already registered.
				log.debug("LnurlWithdraw: already registered")
				return prvRegistration
			}
		}
		
		return nil
	}
	
	static func fetchRegistration() async -> LnurlWithdrawRegistration? {
		log.trace("fetchRegistration()")
		
		// **Developer Note**:
		// This registration process will NOT be needed after we develop the new protocol.
		
		guard
			let nodeId: String = Biz.nodeId,
			let nodeIdHash: String = Biz.nodeIdHash
		else {
			return nil
		}
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlw/me")
		guard let requestUrl = url else { return nil }
		
		let body = [
			"node_id": nodeId
		]
		let bodyData = try? JSONSerialization.data(
			withJSONObject: body,
			options: []
		)
		
		var request = URLRequest(url: requestUrl)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		var registration: LnurlWithdrawRegistration? = nil
		do {
			log.debug("/lnurlw/me: sending...")
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
				log.debug("/lnurlw/me: success")
				
				let response: LnurlWithdrawRegisterResponse
				do {
					response = try JSONDecoder().decode(LnurlWithdrawRegisterResponse.self, from: data)
					log.debug("/lnurlw/me: hex_addr: \(response.hex_addr)")
					
					// Store the value in Prefs so we can skip this step in the future
					registration = LnurlWithdrawRegistration(
						hexAddr: response.hex_addr,
						nodeIdHash: nodeIdHash,
						registrationDate: Date.now
					)
					Prefs.shared.lnurlWithdrawRegistration = registration
					
				} catch {
					log.debug("/lnurlw/me: JSON decoding error: \(error)")
				}
			} else {
				log.debug("/lnurlw/me: statusCode: \(statusCode)")
				if let dataString = String(data: data, encoding: .utf8) {
					log.debug("/lnurlw/me: response:\n\(dataString)")
				}
			}
			
		} catch {
			log.debug("/lnurlw/me: error: \(error)")
		}
		
		return registration
	}
}
