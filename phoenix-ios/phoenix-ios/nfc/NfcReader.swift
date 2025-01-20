import Foundation
import CoreNFC

fileprivate let filename = "NfcReader"
#if DEBUG
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class NfcReader: NSObject, NFCNDEFReaderSessionDelegate {
	
	enum ReadError: Error {
		case readingNotAvailable
		case alreadyStarted
		case errorReadingTag
		case scanningTerminated(NFCReaderError)
	}
	
	static let shared = NfcReader()
	
	private let queue: DispatchQueue
	
	private var session: NFCNDEFReaderSession? = nil
	private var callback: ((Result<NFCNDEFMessage, ReadError>) -> Void)? = nil
	
	override init() {
		queue = DispatchQueue(label: "NfcReader")
	}
	
	func readCard(_ callback: @escaping (Result<NFCNDEFMessage, ReadError>) -> Void) {
		log.trace("readCard()")
		
		let fail = { (error: ReadError) in
			DispatchQueue.main.async {
				callback(Result.failure(error))
			}
		}
		
		queue.async { [self] in
			
			guard NFCReaderSession.readingAvailable else {
				log.error("NFCReaderSession.readingAvailable is false")
				return fail(.readingNotAvailable)
			}
			
			guard session == nil else {
				log.error("session is already started")
				return fail(.alreadyStarted)
			}
			
			session = NFCNDEFReaderSession(delegate: self, queue: queue, invalidateAfterFirstRead: true)
			session?.alertMessage = "Hold your card near the device to read it."
			
			self.callback = callback
			session?.begin()
			
			log.info("session is ready")
		}
	}
	
	// --------------------------------------------------
	// MARK: Private
	// --------------------------------------------------
	
	private func finishWithSuccess(_ message: NFCNDEFMessage) {
		
		guard let session, let callback else {
			return
		}
		log.trace("finishWithSuccess()")
		
		session.invalidate()
		self.session = nil
		self.callback = nil
		DispatchQueue.main.async {
			callback(.success(message))
		}
	}
	
	private func finishWithError(_ error: ReadError) {
		
		guard let session, let callback else {
			return
		}
		log.trace("finishWithError()")
		
		session.invalidate()
		self.session = nil
		self.callback = nil
		
		DispatchQueue.main.async {
			callback(.failure(error))
		}
	}
	
	// --------------------------------------------------
	// MARK: NFCNDEFReaderSessionDelegate
	// --------------------------------------------------
	
	func readerSessionDidBecomeActive(_ session: NFCNDEFReaderSession) {
		log.trace("readerSessionDidBecomeActive(_)")
	}
	
	func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: any Error) {
		log.trace("readerSession(_, didInvalidateWithError:)")
		log.trace("error: \(error)")
		
		let nfcError = (error as? NFCReaderError) ??                                   // this is always the case
			NFCReaderError(NFCReaderError.readerSessionInvalidationErrorSessionTimeout) // but just to be safe
		
		if let _ = error as? NFCReaderError {
			log.debug("is NFCReaderError")
		} else {
			log.debug("!is NFCReaderError")
		}
		finishWithError(.scanningTerminated(nfcError))
	}
	
	func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
		log.trace("readerSession(_, didDetectNDEFs:)")
		log.trace("messages.count = \(messages.count)")
		
		if messages.count > 1 {
			log.warning("NfcReader: Multiple messages detected: this is unsupported, only the first will be read")
		}
		
		if let message = messages.first {
			finishWithSuccess(message)
		}
	}
	
	func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [any NFCNDEFTag]) {
		log.trace("readerSession(_, didDetectTags:)")
		log.trace("messages.count = \(tags.count)")
		
		if tags.count > 1 {
			log.warning("NfcReader: Multiple tags detected: this is unsupported, only the first will be read")
		}
		
		if let tag = tags.first {
			tag.readNDEF { [self] (result, error) in
				
				if let result {
					finishWithSuccess(result)
					
				} else if let error {
					log.error("readNDEF: error = \(error)")
					finishWithError(.errorReadingTag)
				}
			}
		}
	}
}
