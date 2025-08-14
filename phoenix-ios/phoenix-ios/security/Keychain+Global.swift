import Foundation
import Combine

fileprivate let filename = "Keychain+Global"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = KeychainKey

class Keychain_Global {
	
	static let shared = Keychain_Global()
	
	private let id: String
	
	/// Serial queue ensures that only one operation is
	/// reading/modifying the keychain at any given time.
	///
	private let queue = DispatchQueue(label: "Keychain_Wallet")
	
	private init() {
		self.id = KEYCHAIN_GLOBAL_ID
	}
	
	// --------------------------------------------------
	// MARK: Lock PIN
	// --------------------------------------------------
	
	func setHiddenWalletInvalidPin(
		_ invalidPin : InvalidPin?,
		completion   : @escaping (_ error: Error?) -> Void
	) -> Void {
		if let invalidPin {
			log.trace("setHiddenWalletInvalidPin(<count = \(invalidPin.count))>")
		} else {
			log.trace("setHiddenWalletInvalidPin(<nil>)")
		}
		
		let succeed = {
			DispatchQueue.main.async {
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		var invalidPinData: Data? = nil
		if let invalidPin {
			do {
				invalidPinData = try JSONEncoder().encode(invalidPin)
			} catch {
				return fail(error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let key = Key.invalidLockPin
			let mixins = Keychain.commonMixins()
			
			if let invalidPinData {
				do {
					try SystemKeychain.storeItem(
						value       : invalidPinData,
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value,
						mixins      : mixins
					)
					
				} catch {
					log.error("keychain.store(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try SystemKeychain.deleteItem(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.delete(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	func getHiddenWalletInvalidPin() -> InvalidPin? {
		
		let key = Key.invalidLockPin
		let mixins = Keychain.commonMixins()
		
		var invalidPin: InvalidPin? = nil
		do {
			let value: Data? = try SystemKeychain.readItem(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value,
				mixins      : mixins
			)
			
			if let value {
				do {
					invalidPin = try JSONDecoder().decode(InvalidPin.self, from: value)
				} catch {
					log.error("JSON.decode(acct: \(key.debugName)): error: \(error)")
				}
			}
			
		} catch {
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
		}
		
		return invalidPin
	}
}
