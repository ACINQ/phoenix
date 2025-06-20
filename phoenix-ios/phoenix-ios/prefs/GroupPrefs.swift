import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "GroupPrefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension UserDefaults {
	static var group: UserDefaults {
		return UserDefaults(suiteName: "group.co.acinq.phoenix")!
	}
}

/// Group preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
///
class GroupPrefs {
	
	private static var instances: [String: GroupPrefs_Wallet] = [:]
	
	static func wallet(_ walletId: WalletIdentifier) -> GroupPrefs_Wallet {
		return wallet(walletId.prefsKeySuffix)
	}
	
	static func wallet(_ id: String) -> GroupPrefs_Wallet {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = GroupPrefs_Wallet(id: id)
			instances[id] = instance
			return instance
		}
	}
	
	static var global: GroupPrefs_Global {
		return GroupPrefs_Global.shared
	}
	
	static var defaults: UserDefaults {
		return UserDefaults.group
	}
	
	// --------------------------------------------------
	// MARK: Proxy
	// --------------------------------------------------
	
	static func loadWallet(_ walletId: WalletIdentifier) {
		GroupPrefs_Wallet.loadWallet(walletId)
	}
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
		GroupPrefs_Wallet.performMigration(targetBuild, completionPublisher)
	}
	
	static func printAllKeyValues() {
		GroupPrefs_Wallet.printAllKeyValues()
	}
}
