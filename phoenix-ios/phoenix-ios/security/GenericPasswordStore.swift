/**
 * Code inspired from Apple Sample Code:
 * https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain
*/

import Foundation
import CryptoKit
import Security
import LocalAuthentication

struct GenericPasswordStore {

	/// Stores raw Data in the keychain.
	///
	func storeKey(
		_ key   : Data,
		account : String,
		mixins  : [String: Any]
	) throws {
		
		var query = mixins
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecValueData as String] = key
		
		// Add the key data.
		let status = SecItemAdd(query as CFDictionary, nil)
		guard status == errSecSuccess else {
			throw KeyStoreError("Unable to store item: \(status.message)")
		}
	}
	
	/// Stores a simple String in the keychain.
	///
	func storeKey(
		_ key   : String,
		account : String,
		mixins  : [String: Any]
	) throws {
		
		guard let keyData = key.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert strong to data using utf8")
		}
		
		try storeKey(keyData, account: account, mixins: mixins)
	}
	
	/// Stores a CryptoKit key in the keychain.
	///
	func storeKey<T: GenericPasswordConvertible>(
		_ key   : T,
		account : String,
		mixins  : [String: Any]
	) throws {

		let keyData = key.rawRepresentation
		
		try storeKey(keyData, account: account, mixins: mixins)
	}
	
	func keyExists(
		account: String
	) throws -> Bool {
		
		let context = LAContext()
		context.interactionNotAllowed = true // <- don't prompt user
		
		var query = [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecReturnData as String] = false // <- don't want it
		query[kSecMatchLimit as String] = kSecMatchLimitOne
		query[kSecUseAuthenticationContext as String] = context
		
		var item: CFTypeRef?
		switch SecItemCopyMatching(query as CFDictionary, &item) {
			case noErr                       : return true
			case errSecInteractionNotAllowed : return true
			case errSecItemNotFound          : return false
			case let status                  : throw KeyStoreError("Keychain read failed: \(status.message)")
		}
	}

	func readKey(
		account : String,
		mixins  : [String: Any]? = nil
	) throws -> Data? {
		
		// Seek a generic password with the given account.
		var query = mixins ?? [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecReturnData as String] = true
		query[kSecMatchLimit as String] = kSecMatchLimitOne
		
		// Find item and cast as data.
		var item: CFTypeRef?
		switch SecItemCopyMatching(query as CFDictionary, &item) {
			case errSecSuccess      : return item as? Data
			case errSecItemNotFound : return nil
			case let status         : throw KeyStoreError("Keychain read failed: \(status.message)")
		}
	}
	
	/// Reads a simple String from the keychain.
	///
	func readKey(
		account : String,
		mixins  : [String: Any]? = nil
	) throws -> String? {
		
		if let data: Data = try readKey(account: account, mixins: mixins) {
			return String(data: data, encoding: .utf8)
		}
		return nil
	}
	
	/// Reads a CryptoKit key from the keychain.
	///
	func readKey<T: GenericPasswordConvertible>(
		account : String,
		mixins  : [String: Any]? = nil
	) throws -> T? {

		if let data: Data = try readKey(account: account, mixins: mixins) {
			return try T(rawRepresentation: data)
		}
		return nil
	}
	
	/// Removes any existing key with the given account.
	func deleteKey(account: String) throws {
		let query = [
			kSecClass                     : kSecClassGenericPassword,
			kSecAttrAccount               : account,
			kSecUseDataProtectionKeychain : true
		] as [String: Any]
		
        switch SecItemDelete(query as CFDictionary) {
        	case errSecItemNotFound, errSecSuccess: break // Okay to ignore
        	case let status:
            	throw KeyStoreError("Unexpected deletion error: \(status.message)")
		}
	}
}
