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
	case backgroundPayments
}

class DeepLinkManager: ObservableObject {
	
	@Published var deepLink: DeepLink? = nil
	@Published var iOS16Workaround = UUID()
	
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
		
		if #available(iOS 16, *) {
			// In iOS 16, Apple deprecated NavigationLink(destination:tag:selection:label:).
			//
			// The sugggested replacement is to use NavigationLink(value:label:) paired
			// with navigationDestination(for:destination:).
			// However that solution was only half-baked when Apple released it, and it's riddled with bugs:
			// https://github.com/ACINQ/phoenix/pull/333
			//
			// The deprecated solution still works for now, but has issues.
			// One of which is that manual navigation is fragile.
			// Manually setting the `navLinkTag` will often highlight the NavigationLink,
			// but won't successfully trigger a navigation.
			// If you keep trying (by causing a view refresh) it will work properly.
			//
			// (This is related to manual navigation via a DeepLink)
			
			for idx in 1...50 {
				DispatchQueue.main.asyncAfter(deadline: .now() + (Double(idx) * 0.100)) {
					self.iOS16Workaround = UUID()
				}
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
