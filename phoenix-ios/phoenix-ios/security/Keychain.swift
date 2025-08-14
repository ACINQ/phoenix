import Foundation
import Combine
import CommonCrypto
import CryptoKit
import SwiftUI

fileprivate let filename = "Keychain"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = KeychainKey

class Keychain {
	
	private static var instances: [String: Keychain_Wallet] = [:]
	
	static var current: Keychain_Wallet {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet(KEYCHAIN_DEFAULT_ID)
		}
	}
	
	static func wallet(_ walletId: WalletIdentifier) -> Keychain_Wallet {
		return wallet(walletId.keychainKeyId)
	}
	
	static func wallet(_ id: String) -> Keychain_Wallet {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = Keychain_Wallet(id: id)
			instances[id] = instance
			return instance
		}
	}
	
	static var global: Keychain_Global {
		return Keychain_Global.shared
	}
	
	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------
	
	static func commonMixins() -> [String: Any] {
		
		var mixins = [String: Any]()
		
		// kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly:
		//
		// > After the first unlock, the data remains accessible until the next restart.
		// > This is recommended for items that need to be accessed by background applications.
		// > Items with this attribute do not migrate to a new device. Thus, after restoring
		// > from a backup of a different device, these items will not be present.
		//
		mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		
		return mixins
	}
	
	private static func migrateKey(oldAccount: String, newAccount: String, accessGroup: String) {
		
		let mixins = commonMixins()
		
		var value: Data? = nil
		do {
			value = try SystemKeychain.readItem(
				account     : oldAccount,
				accessGroup : accessGroup,
				mixins      : mixins
			)
		} catch {
			log.error("keychain.readItem(acct: \(oldAccount), grp: \(accessGroup)): error: \(error)")
			return
		}
		
		guard let value else {
			return
		}
		
		var dstExists = false
		do {
			dstExists = try SystemKeychain.itemExists(
				account     : newAccount,
				accessGroup : accessGroup,
				mixins      : mixins
			)
		} catch {
			log.error("keychain.exists(acct: \(newAccount), grp: \(accessGroup)): error: \(error)")
			return
		}
		
		if dstExists {
			log.debug("keychain.delete: \(oldAccount)")
		} else {
			log.debug("keychain.move: \(oldAccount) => \(newAccount)")
			
			do {
				try SystemKeychain.addItem(
					value       : value,
					account     : newAccount,
					accessGroup : accessGroup,
					mixins      : mixins
				)
			} catch {
				log.error("keychain.add(acct: \(newAccount), grp: \(accessGroup)): error: \(error)")
				return
			}
		}
		
		do {
			try SystemKeychain.deleteItem(account: oldAccount, accessGroup: accessGroup)
		} catch {
			log.error("keychain.delete(acct: \(oldAccount), grp: \(accessGroup)): error: \(error)")
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
		
		let account = Key.lockingKey.deprecatedValue
		
		let oldAccessGroup = AccessGroup.appOnly.value
		let newAccessGroup = AccessGroup.appAndExtensions.value
		
		var mixins = [String: Any]()
		mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		
		// Step 1 of 4:
		// - Read the OLD keychain item.
		// - If it exists, then we need to migrate it to the new location.
		var savedKey: SymmetricKey? = nil
		do {
			savedKey = try SystemKeychain.readItem(
				account     : account,
				accessGroup : oldAccessGroup, // <- old location
				mixins      : mixins
			)
		} catch {
			log.error("keychain.read(account: keychain, group: nil): error: \(error)")
		}
		
		if let lockingKey = savedKey {
			// The OLD keychain item exists, so we're going to migrate it.
			
			var migrated = false
			do {
				// Step 2 of 4:
				// - Delete the NEW keychain item.
				// - It shouldn't exist, but if it does it will cause an error on the next step.
				try SystemKeychain.deleteItem(
					account     : account,
					accessGroup : newAccessGroup // <- new location
				)
			} catch {
				log.error("keychain.delete(account: keychain, group: shared): error: \(error)")
			}
			do {
				// Step 3 of 4:
				// - Copy the OLD keychain item to the NEW location.
				// - If this step fails, an exception is thrown, and we do NOT advance to step 4.
				try SystemKeychain.storeItem(
					value       : lockingKey,
					account     : account,
					accessGroup : newAccessGroup, // <- new location
					mixins      : mixins
				)
				migrated = true
				
				// Step 4 of 4:
				// - Finally, delete the OLD keychain item.
				// - This prevents any duplicate migration attempts in the future.
				try SystemKeychain.deleteItem(
					account     : account,
					accessGroup : oldAccessGroup // <- old location
				)
				
			} catch {
				if !migrated {
					log.error("keychain.store(account: keychain, group: shared): error: \(error)")
				} else {
					log.error("keychain.delete(account: keychain, group: private): error: \(error)")
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
		
		runWhenProtectedDataAvailable(completionPublisher) {
			
			for key in Key.allCases {
				self.migrateKey(
					oldAccount  : key.deprecatedValue,
					newAccount  : key.account(KEYCHAIN_DEFAULT_ID),
					accessGroup : key.accessGroup.value
				)
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
	// MARK: Load Wallet
	// --------------------------------------------------
	
	static func didLoadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		// Migration to a per-wallet design:
		//
		// MOVE: "foo-default" => "foo-<walletId>"
		
		let oldId = KEYCHAIN_DEFAULT_ID
		let newId = walletId.keychainKeyId
		
		for key in Key.allCases {
			migrateKey(
				oldAccount  : key.account(oldId),
				newAccount  : key.account(newId),
				accessGroup : key.accessGroup.value
			)
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
