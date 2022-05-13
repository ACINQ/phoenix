import Foundation
import Combine
import Network
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TLSConnectionCheck"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum TLSConnectionStatus {
	case trusted
	case untrusted(cert: SecCertificate)
}

enum TLSConnectionError: Error {
	case invalidPort
	case cancelled
	case network(error: NWError)
}

class TLSConnectionCheck {
	
	static func check(
		host: String,
		port: UInt16,
		completion: @escaping (Result<TLSConnectionStatus, TLSConnectionError>) -> Void
	) -> AnyCancellable {
		
		let cancelledPublisher = PassthroughSubject<Void, Never>()
		let cancellable = AnyCancellable.init({
			cancelledPublisher.send()
		})
		
		let promise = {(result: Result<TLSConnectionStatus, TLSConnectionError>) in
			DispatchQueue.main.async {
				completion(result)
			}
		}
		
		let ep_host = NWEndpoint.Host(host)
		guard let ep_port = NWEndpoint.Port(rawValue: port) else {
			promise(.failure(.invalidPort))
			return cancellable
		}
			
		let tlsOptions = NWProtocolTLS.Options()
		let tcpOptions = NWProtocolTCP.Options()
			
		var isTrusted: Bool = false
		var untrustedCert: SecCertificate? = nil
		
		let verify_queue = DispatchQueue.global()
		let verify_block: sec_protocol_verify_t = {(
			metadata   : sec_protocol_metadata_t,
			trust      : sec_trust_t,
			completion : @escaping sec_protocol_verify_complete_t
		) in
			
			let sec_trust = sec_trust_copy_ref(trust).takeRetainedValue()
			var error: CFError?
			let result = SecTrustEvaluateWithError(sec_trust, &error)
			
			if result {
				isTrusted = true
			} else {
				log.debug("SecTrustEvaluate: \(String(describing: error))")
				
				// The certificate is not fully trusted.
				// This could be for any number of reasons:
				// - cert is expired
				// - cert is self-signed
				// - a parent cert in the chain is self-signed
				// - [... very long list of possible reasons ...]
				
				let certs: [SecCertificate]
				if #available(iOS 15, macOS 12, tvOS 15, watchOS 8, *) {
					certs = SecTrustCopyCertificateChain(sec_trust) as? [SecCertificate] ?? []
					
				} else {
					certs = (0 ..< SecTrustGetCertificateCount(sec_trust)).compactMap { index in
						SecTrustGetCertificateAtIndex(sec_trust, index)
					}
				}
				untrustedCert = certs.first
			}
			
			completion(true)
		}
		
		sec_protocol_options_set_verify_block(tlsOptions.securityProtocolOptions, verify_block, verify_queue)
		
		let options = NWParameters(tls: tlsOptions, tcp: tcpOptions)
		let connection = NWConnection(host: ep_host, port: ep_port, using: options)
		
		var cancellables = Set<AnyCancellable>()
		connection.stateUpdateHandler = {(state: NWConnection.State) in
			
			switch state {
				case .failed(let error):
					promise(.failure(.network(error: error)))
					cancellables.removeAll()
				case .ready:
					if isTrusted {
						promise(.success(.trusted))
					} else if let untrustedCert = untrustedCert {
						promise(.success(.untrusted(cert: untrustedCert)))
					} else {
						promise(.failure(.network(error: NWError.tls(OSStatus.zero))))
					}
					cancellables.removeAll()
				case .cancelled:
					log.debug("NWConnection cancelled")
				default:
					break
			}
			
		} // </stateUpdateHandler>
		
		connection.start(queue: DispatchQueue.global(qos: .userInitiated))
		
		cancelledPublisher.sink { _ in
			connection.cancel()
		}.store(in: &cancellables)
			
		return cancellable
	}
	
	// MARK: Debug
	
	#if DEBUG
	static func debug(
		result: Result<TLSConnectionStatus, TLSConnectionError>,
		delay: TimeInterval,
		completion: @escaping (Result<TLSConnectionStatus, TLSConnectionError>) -> Void
	) -> AnyCancellable {
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			completion(result)
		}
		return AnyCancellable.init({
			// do nothing
		})
	}
	
	static func debugCert() -> SecCertificate {
		
		let base64 =
		"""
		MIIDnzCCAocCFGw8AU6bWkI4JTKj/TvtS6nuHdiiMA0GCSqGSIb3DQEBCwUAMIGL
		MQswCQYDVQQGEwJERTEVMBMGA1UECAwMTmllZGVyc2FjaGVuMRkwFwYDVQQKDBBl
		bGVjdHJ1bS5lbXp5LmRlMRIwEAYDVQQLDAllbGVjdHJ1bXgxGTAXBgNVBAMMEGVs
		ZWN0cnVtLmVtenkuZGUxGzAZBgkqhkiG9w0BCQEWDGVtenlAZW16eS5kZTAeFw0y
		MTEwMTIyMTA2MjdaFw0yNjEwMTEyMTA2MjdaMIGLMQswCQYDVQQGEwJERTEVMBMG
		A1UECAwMTmllZGVyc2FjaGVuMRkwFwYDVQQKDBBlbGVjdHJ1bS5lbXp5LmRlMRIw
		EAYDVQQLDAllbGVjdHJ1bXgxGTAXBgNVBAMMEGVsZWN0cnVtLmVtenkuZGUxGzAZ
		BgkqhkiG9w0BCQEWDGVtenlAZW16eS5kZTCCASIwDQYJKoZIhvcNAQEBBQADggEP
		ADCCAQoCggEBALn6g79JySAiT6D/OsDj2DP1yHxbOr5laxhHgWZsvTJjDbm7pYZ8
		hPrFrhYZyF/tLrhZSdfCyWBTiINHwGhXTecYeL2oATVz/XQnMX1XzBn7/cQUGX1o
		Sqw4wuAwifNp9yvkOEaqSv8kvRiGSHKqxt68RCwWfDR1TyF73ltD52oBnkN9TQ3T
		gKvTASoikXJnynpuXWlBKP8TOJ07frTk8ZebB4nHUEgnjx13sDF85pDP1cNazCWS
		nk8Jy1+OE7KaRIuO311BXHbgdvgNcR0CIBPcGuAanNmaFLSigWsnnlNNgtkgRYFc
		bQFk+mBEnXDz/LvqUe5QAwO0GTVFTSjLzDcCAwEAATANBgkqhkiG9w0BAQsFAAOC
		AQEAlraiQDHY/knqRlij+O268yos3rOC+ARKzEDKbONc9EP5fOp+ld4+sd4gQVfW
		/SsB+FKx0zCLNG1uCC9NJ0F5Ukbt+dJ8u0B1pKPWs6dId6zAQKk0+yl+DfZvoJ6R
		tebT8iI1RVrvxyV488CqbTHOSKZGlJS8SiPkXzu0+Onyr/uNpayAKvwE/NhFbU3h
		z8b3TQ9WNGli5Sslx1nMjO0at/gtbUk5DsXvUZnmy3wXmsylnVi7R6+troyuVmP8
		CfsTNyoJPvgKQsSNoCRBIPjIx1eTPKQAaFwIu2Ckxo2cykBnTJLsZFKq7yhjjVZO
		qiVb2Ts1gAR16RIdK1Ni0T5d5Q==
		"""
		
		let data: Data = Data(base64Encoded: base64, options: .ignoreUnknownCharacters)!
		let cert: SecCertificate = SecCertificateCreateWithData(nil, data as CFData)!
		
		return cert
	}
	#endif
}
