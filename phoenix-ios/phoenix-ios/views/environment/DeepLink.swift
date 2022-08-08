import Foundation
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "DeepLinkManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum DeepLink: String, Equatable {
	case paymentHistory
	case backup
	case drainWallet
	case electrum
}

class DeepLinkManager: ObservableObject {
	
	@Published var deepLink: DeepLink? = nil
	private var deepLinkIdx = 0
	
	func broadcast(_ value: DeepLink) {
		log.trace("broadcast(\(value.rawValue))")
		
		self.deepLinkIdx += 1
		self.deepLink = value
		
		// The value should get unset when the UI reaches the final destination.
		// But if anything prevents that for any reason, this acts as a backup.
		
		let idx = self.deepLinkIdx
		DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
			if self.deepLinkIdx == idx {
				self.deepLink = nil
			}
		}
	}
	
	func unbroadcast(_ value: DeepLink) {
		log.trace("unbroadcast(\(value.rawValue))")
		
		if self.deepLink == value {
		
			self.deepLinkIdx += 1
			self.deepLink = nil
		}
	}
}
