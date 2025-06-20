import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "Prefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif



/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are NOT shared with other extensions bundled in the app,
/// such as the notification-service-extension. For preferences shared with extensions, see GroupPrefs.
///
class Prefs {
	
	private static var instances: [String: Prefs_Wallet] = [:]
	
	static var current: Prefs_Wallet {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet(PREFS_DEFAULT_ID)
		}
	}
	
	static func wallet(_ walletId: WalletIdentifier) -> Prefs_Wallet {
		return wallet(walletId.prefsKeySuffix)
	}
	
	static func wallet(_ id: String) -> Prefs_Wallet {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = Prefs_Wallet(id: id)
			instances[id] = instance
			return instance
		}
	}
	
	static var global: Prefs_Global {
		return Prefs_Global.shared
	}
	
	static var defaults: UserDefaults {
		return UserDefaults.standard
	}
	
	// --------------------------------------------------
	// MARK: Proxy
	// --------------------------------------------------
	
	static func loadWallet(_ walletId: WalletIdentifier) {
		Prefs_Wallet.loadWallet(walletId)
	}
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
		Prefs_Wallet.performMigration(targetBuild, completionPublisher)
	}
	
	static func printAllKeyValues() {
		Prefs_Wallet.printAllKeyValues()
	}
}
