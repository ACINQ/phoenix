import Foundation
import CoreNFC

fileprivate let filename = "NFCReaderError+Ignore"
#if DEBUG
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension NFCReaderError {
	
	func isIgnorable() -> Bool {
		
		switch self.code {
		case .readerSessionInvalidationErrorUserCanceled:
			// User tapped "cancel" button
			log.debug("readerSessionInvalidationErrorUserCanceled")
			return true
			
		case .readerSessionInvalidationErrorSessionTimeout:
			// User didn't present a card to the reader.
			// The NFC reader automatically cancelled after 60 seconds.
			log.debug("readerSessionInvalidationErrorSessionTimeout")
			return true
			
		case .readerSessionInvalidationErrorSessionTerminatedUnexpectedly:
			// User locked the phone, which automatically terminates the NFC reader.
			log.debug("readerSessionInvalidationErrorSessionTerminatedUnexpectedly")
			return true
			
		default:
			return false
		}
		
	}
}
