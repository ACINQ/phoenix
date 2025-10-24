import Foundation

fileprivate let filename = "DeepLinkManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

enum DeepLink: Equatable, CustomStringConvertible {
	case payment(paymentId: Lightning_kmpUUID)
	case paymentHistory
	case backup
	case drainWallet
	case electrum
	case backgroundPayments
	case liquiditySettings
	case torSettings
	case forceCloseChannels
	case swapInWallet
	case finalWallet
	case appAccess
	case walletMetadata
	case bip353Registration
	
	var description: String {
		return switch self {
			case .payment(let id)    : "payment(\(id))"
			case .paymentHistory     : "paymentHistory"
			case .backup             : "backup"
			case .drainWallet        : "drainWallet"
			case .electrum           : "electrum"
			case .backgroundPayments : "backgroundPayments"
			case .liquiditySettings  : "liquiditySettings"
			case .torSettings        : "torSettings"
			case .forceCloseChannels : "forceCloseChannels"
			case .swapInWallet       : "swapInWallet"
			case .finalWallet        : "finalWallet"
			case .appAccess          : "appAccess"
			case .walletMetadata     : "walletMetadata"
			case .bip353Registration : "bip353Registration"
		}
	}
}

class DeepLinkManager: ObservableObject {
	
	@Published var deepLink: DeepLink? = nil
	@Published var iOS16Workaround = UUID()
	
	private var deepLinkIdx = 0
	
	func broadcast(_ value: DeepLink) {
		log.trace("broadcast(\(value))")
		
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
		
		if #unavailable(iOS 17.0) {
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
		log.trace("unbroadcast(\(value))")
		
		if self.deepLink == value {
		
			self.deepLinkIdx += 1
			self.deepLink = nil
		}
	}
}

enum PopToDestination: CustomStringConvertible {
	case RootView(followedBy: DeepLink? = nil)
	case ConfigurationView(followedBy: DeepLink? = nil)
	case TransactionsView
	
	var followedBy: DeepLink? {
		switch self {
			case .RootView(let followedBy)          : return followedBy
			case .ConfigurationView(let followedBy) : return followedBy
			case .TransactionsView                  : return nil
		}
	}
	
	public var description: String {
		switch self {
		case .RootView(let followedBy):
			return "RootView(followedBy: \(followedBy?.description ?? "nil"))"
		case .ConfigurationView(let followedBy):
			return "ConfigurationView(follwedBy: \(followedBy?.description ?? "nil"))"
		case .TransactionsView:
			return "TransactionsView"
		}
	}
}
