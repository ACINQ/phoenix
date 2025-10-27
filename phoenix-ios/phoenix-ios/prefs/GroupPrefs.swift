import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "GroupPrefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = GroupPrefsKey

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
		return wallet(walletId.standardKeyId)
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
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) -> Void {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "40") {
			performMigration_toBuild40()
		}
		if targetBuild.isVersion(equalTo: "65") {
			performMigration_toBuild65()
		}
		if targetBuild.isVersion(equalTo: "96") {
			performMigration_toBuild96()
		}
	}
	
	private static func performMigration_toBuild40() {
		log.trace(#function)
		
		migrateToGroup(Key.currencyType)
		migrateToGroup(Key.bitcoinUnit)
		migrateToGroup(Key.fiatCurrency)
		migrateToGroup(Key.currencyConverterList)
		migrateToGroup(Key.electrumConfig)
	}
	
	private static func performMigration_toBuild65() {
		log.trace(#function)
		
		migrateToGroup(Key.liquidityPolicy)
	}
	
	private static func migrateToGroup(_ key: Key) {
		
		let savedGrp = UserDefaults.group.value(forKey: key.deprecatedValue)
		if savedGrp == nil {
			
			let savedStd = UserDefaults.standard.value(forKey: key.deprecatedValue)
			if savedStd != nil {
				
				UserDefaults.group.set(savedStd, forKey: key.deprecatedValue)
				UserDefaults.standard.removeObject(forKey: key.deprecatedValue)
			}
		}
	}
	
	private static func performMigration_toBuild96() {
		log.trace(#function)
		
		// Migration to a per-wallet design:
		//
		// The migration is split into 2 steps.
		// Step 1 is performed here:
		//
		// - Move all keys from key "keyName" to "keyName-default"
		//
		// Note that step 1 is performed as soon as the app is launched,
		// during the normal migration process.
		// That is, before we've unlocked the wallet, and before we know the walletId.
		//
		// For step 2, see "didLoadWallet".
		
		let d = self.defaults
		
		for key in Key.allCases {
			let oldKey = key.deprecatedValue
			if let value = d.object(forKey: oldKey) {
				
				let newId = (key.group == .global) ? PREFS_GLOBAL_ID : PREFS_DEFAULT_ID
				let newKey = key.value(newId)
				if d.object(forKey: newKey) == nil {
					log.debug("move: \(oldKey) > \(newKey)")
					d.set(value, forKey: newKey)
				} else {
					log.debug("delete: \(oldKey)")
				}
				
				d.removeObject(forKey: oldKey)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Load Wallet
	// --------------------------------------------------
	
	static func didLoadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		// Migration to a per-wallet design:
		//
		// In general, each pref should be keyed to the corresponding walletId.
		// E.g. "foo-<walletId>" = "bar"
		//
		// However, there are also preferences that the user may want to configure
		// BEFORE they've created/restored their wallet. For example:
		// - tor settings
		// - electrum settings
		//
		// We allow this by also having a "default" set of preferences.
		// E.g. "foo-default" = "bar"
		//
		// And once the user loads a wallet, then we simply copy these "default" values
		// into the corresponding wallet:
		//
		// COPY: "foo-default" => "foo-<walletId>"
		
		let d = Self.defaults
		let oldId = PREFS_DEFAULT_ID
		let newId = walletId.standardKeyId
		
		for key in Key.allCases {
			let oldKey = key.value(oldId)
			if let value = d.object(forKey: oldKey) {
				
				let newKey = key.value(newId)
				if d.object(forKey: newKey) == nil {
					log.debug("move: \(oldKey) > \(newKey)")
					d.set(value, forKey: newKey)
				} else {
					log.debug("delete: \(oldKey)")
				}
				
				d.removeObject(forKey: oldKey)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	static func didResetWallet(_ id: String) {
		log.trace(#function)
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
			instances.removeValue(forKey: id)
		}
	}
}
