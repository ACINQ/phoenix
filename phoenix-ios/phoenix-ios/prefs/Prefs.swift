import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "Prefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey
fileprivate typealias KeyDeprecated = PrefsKeyDeprecated

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// - Note:
/// The values here are NOT shared with other extensions bundled in the app, such as the
/// notification-service-extension. For preferences shared with extensions, see `GroupPrefs`.
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
		return wallet(walletId.standardKeyId)
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
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "44") {
			performMigration_toBuild44()
		}
		if targetBuild.isVersion(equalTo: "96") {
			performMigration_toBuild96()
		}
	}
	
	private static func performMigration_toBuild44() {
		log.trace(#function)
		
		let d = self.defaults
		let oldKey = KeyDeprecated.recentPaymentSeconds.rawValue
		let newKey = Key.recentPaymentsConfig.deprecatedValue
		
		if d.object(forKey: oldKey) != nil {
			let seconds = d.integer(forKey: oldKey)
			if seconds <= 0 {
				let newValue = RecentPaymentsConfig.inFlightOnly
				d.set(newValue.jsonEncode(), forKey: newKey)
			} else {
				let newValue = RecentPaymentsConfig.withinTime(seconds: seconds)
				d.set(newValue.jsonEncode(), forKey: newKey)
			}
			
			d.removeObject(forKey: oldKey)
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
		
		let d = self.defaults
		do {
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
		
		// Migration of special keys
		do {
			let oldId = walletId.deprecatedKeyId
			let newId = walletId.standardKeyId
			
			for specialKey in Key.specialMigrations() {
				let oldKey = specialKey.value(oldId)
				if let value = d.object(forKey: oldKey) {
					
					let newKey = specialKey.value(newId)
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
