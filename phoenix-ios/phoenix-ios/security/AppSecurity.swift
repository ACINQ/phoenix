import Foundation
import Combine
import CommonCrypto
import CryptoKit
import SwiftUI

fileprivate let filename = "AppSecurity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = AppSecurityKey

class AppSecurity {
	
	private static var instances: [String: AppSecurity_Wallet] = [:]
	
	static var current: AppSecurity_Wallet {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet(PREFS_DEFAULT_ID)
		}
	}
	
	static func wallet(_ walletId: WalletIdentifier) -> AppSecurity_Wallet {
		return wallet(walletId.prefsKeySuffix)
	}
	
	static func wallet(_ id: String) -> AppSecurity_Wallet {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = AppSecurity_Wallet(id: id)
			instances[id] = instance
			return instance
		}
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
		if targetBuild.isVersion(equalTo: "41") {
			performMigration_toBuild41(completionPublisher)
		}
		if targetBuild.isVersion(equalTo: "92") {
			performMigration_toBuild92(completionPublisher)
		}
	}
	
	private static func performMigration_toBuild40() {
		log.trace("performMigration_toBuild40()")
		
		// Step 1 of 2:
		// Migrate "security.json" file to group container directory.
		
		let fm = FileManager.default
		
		if let appSupportDir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first,
		   let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix")
		{
			let oldFile = appSupportDir.appendingPathComponent("security.json", isDirectory: false)
			let newFile = groupDir.appendingPathComponent("security.json", isDirectory: false)
			
			if fm.fileExists(atPath: oldFile.path) {
				
				try? fm.moveItem(at: oldFile, to: newFile)
			}
		}
		
		// Step 2 of 2:
		// Migrate keychain entry to group container.
		
		migrateKeychainItemToSharedGroup()
	}
	
	private static func performMigration_toBuild41(
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
		log.trace("performMigration_toBuild41()")
		
		// There was a bug in versions prior to build 41,
		// where we didn't check the UIApplication's `isProtectedDataAvailable` flag.
		//
		// If that value happened to be false, and we attempted to read from the keychain,
		// we would have received an item-not-found error.
		//
		// If this occurred during performMigration_toBuild40(),
		// this would have resulted in the app failing to read the keychain item forever (in build 40).
		// So in build 41, we have to perform this migration again,
		// but this time not until `isProtectedDataAvailable` is true.
		
		runWhenProtectedDataAvailable(completionPublisher) {
			self.migrateKeychainItemToSharedGroup()
		}
	}
	
	private static func migrateKeychainItemToSharedGroup() {
		log.trace("migrateKeychainItemToSharedGroup()")
		
		let keychain = GenericPasswordStore()
		let account = Key.lockingKey_keychain.deprecatedValue
		
		let oldAccessGroup = AccessGroup.appOnly.value
		let newAccessGroup = AccessGroup.appAndExtensions.value
		
		// Step 1 of 4:
		// - Read the OLD keychain item.
		// - If it exists, then we need to migrate it to the new location.
		var savedKey: SymmetricKey? = nil
		do {
			savedKey = try keychain.readKey(
				account     : account,
				accessGroup : oldAccessGroup // <- old location
			)
		} catch {
			log.error("keychain.readKey(account: keychain, group: nil): error: \(error)")
		}
		
		if let lockingKey = savedKey {
			// The OLD keychain item exists, so we're going to migrate it.
			
			var migrated = false
			do {
				// Step 2 of 4:
				// - Delete the NEW keychain item.
				// - It shouldn't exist, but if it does it will cause an error on the next step.
				try keychain.deleteKey(
					account     : account,
					accessGroup : newAccessGroup // <- new location
				)
			} catch {
				log.error("keychain.deleteKey(account: keychain, group: shared): error: \(error)")
			}
			do {
				var mixins = [String: Any]()
				mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
				
				// Step 3 of 4:
				// - Copy the OLD keychain item to the NEW location.
				// - If this step fails, an exception is thrown, and we do NOT advance to step 4.
				try keychain.storeKey( lockingKey,
				              account: account,
				          accessGroup: newAccessGroup, // <- new location
				               mixins: mixins
				)
				migrated = true
				
				// Step 4 of 4:
				// - Finally, delete the OLD keychain item.
				// - This prevents any duplicate migration attempts in the future.
				try keychain.deleteKey(
					account     : account,
					accessGroup : oldAccessGroup // <- old location
				)
				
			} catch {
				if !migrated {
					log.error("keychain.storeKey(account: keychain, group: shared): error: \(error)")
				} else {
					log.error("keychain.deleteKey(account: keychain, group: private): error: \(error)")
				}
			}
		}
	}
	
	private static func performMigration_toBuild92(
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
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
		// For step 2, see "loadWallet".
		
		let keychain = GenericPasswordStore()
		
		let migrateKey = {(key: Key) in
			
			let oldAccount = key.deprecatedValue
			let accessGroup = key.accessGroup.value
			
			var value: Data? = nil
			do {
				value = try keychain.readKey(account: oldAccount, accessGroup: accessGroup)
			} catch {
				log.error(
					"""
					keychain.readKey(acct: \(oldAccount), grp: \(key.accessGroup.debugName)): \
					error: \(error)
					""")
				return
			}
			
			guard let value else {
				return
			}
			
			let newAccount = key.value(PREFS_DEFAULT_ID)
			var dstExists = false
			do {
				dstExists = try keychain.keyExists(account: newAccount, accessGroup: accessGroup)
			} catch {
				log.error(
					"""
					keychain.keyExists(acct: \(newAccount), grp: \(key.accessGroup.debugName)): \
					error: \(error)
					""")
				return
			}
			
			if dstExists {
				log.debug("keychain.deleteKey: \(oldAccount)")
			} else {
				log.debug("keychain.moveKey: \(oldAccount) => \(newAccount)")
				
				do {
					try keychain.storeKey(value, account: newAccount, accessGroup: accessGroup)
				} catch {
					log.error(
						"""
						keychain.storeKey(acct: \(newAccount), grp: \(key.accessGroup.debugName)): \
						error: \(error)
						""")
					return
				}
			}
			
			do {
				try keychain.deleteKey(account: oldAccount, accessGroup: accessGroup)
			} catch {
				log.error(
					"""
					keychain.deleteKey(acct: \(oldAccount), grp: \(key.accessGroup.debugName)): \
					error: \(error)
					""")
			}
		}
		
		runWhenProtectedDataAvailable(completionPublisher) {
			
			for key in Key.allCases {
				migrateKey(key)
			}
		}
	}
	
	private static func runWhenProtectedDataAvailable(
		_ completionPublisher: CurrentValueSubject<Int, Never>,
		_ block: @escaping () -> Void
	) {
		if UIApplication.shared.isProtectedDataAvailable {
			block()
			
		} else {
			
			completionPublisher.value += 1
			var cancellables = Set<AnyCancellable>()
			
			let nc = NotificationCenter.default
			nc.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification).sink { _ in
				
				// Apple doesn't specify which thread this notification is posted on.
				// Should be the main thread, but just in case, let's be safe.
				runOnMainThread {
					block()
					completionPublisher.value -= 1
				}
				
				cancellables.removeAll()
			}.store(in: &cancellables)
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
