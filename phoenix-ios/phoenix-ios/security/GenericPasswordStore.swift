/**
 * Code inspired from Apple Sample Code:
 * https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain
 *
 * Notes concerning the AccessGroup:
 *
 * Apple has a good article that discusses AccessGroup's:
 *
 * - Title: "Sharing Access to Keychain Items Among a Collection of Apps"
 * - Link: https://developer.apple.com/documentation/security/keychain_services/
 *         keychain_items/sharing_access_to_keychain_items_among_a_collection_of_apps
 *
 * Cliff notes:
 * - An app can belong to multiple access groups
 * - Each access group represents a distinct keychain domain
 * - A keychain item can only belong to a single keychain domain
 * - The default domain is "${TeamID}.${AppID}"
 * - The list of domains is a sorted array, which represents the search order
 * - The default domain comes before app groups
 *
 * IMPORTANT:
 * - If you specify a nil group, you will search ALL domains
 *
 * Thus, if you say, for example:
 * `keychain.deleteKey(account: "foobar", accessGroup: nil)`
 *
 * Then this would delete the associated ITEM from ALL keychain domains !
 * Which may be an unexpected result, and could lead to lost funds.
 * Thus the `accessGroup` is a REQUIRED parameter.
*/

import Foundation
import CryptoKit
import Security
import LocalAuthentication

struct GenericPasswordStore {

	// --------------------------------------------------
	// MARK: Add
	// --------------------------------------------------
	
	/// Adds a NEW entry in the keychain (with Data as the value).
	/// If a matching entry already exists, an error is thrown.
	///
	func addKey(
		_ key       : Data,
		account     : String,
		accessGroup : String, // always required (see notes atop)
		mixins      : [String: Any]? = nil
	) throws {
		
		var query = mixins ?? [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecValueData as String] = key
		
		// Add the key data.
		let status = SecItemAdd(query as CFDictionary, nil)
		guard status == errSecSuccess else {
			throw KeyStoreError("Unable to store item: \(status.message)")
		}
	}
	
	/// Adds a NEW entry in the keychcain (with a String as the value).
	/// If a matching entry already exists, an error is thrown.
	///
	func addKey(
		_ key       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {
		
		guard let keyData = key.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try addKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Adds a NEW entry in the keychain (with a CryptoKit key as the value).
	/// If a matching entry already exists, an error is thrown.
	///
	func addKey<T: GenericPasswordConvertible>(
		_ key       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {

		let keyData = key.rawRepresentation
		
		try addKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Update
	// --------------------------------------------------
	
	/// Updates an entry in the keychain (with Data as the new value).
	/// If a matching entry doesn't exist, an error is thrown.
	///
	func updateKey(
		_ key       : Data,
		account     : String,
		accessGroup : String, // always required (see notes atop)
		mixins      : [String: Any]? = nil
	) throws {
		
		var query = mixins ?? [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		
		var attributes = [String: Any]()
		attributes[kSecValueData as String] = key
		
		// Add the key data.
		let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
		guard status == errSecSuccess else {
			throw KeyStoreError("Unable to update item: \(status.message)")
		}
	}
	
	/// Updates an entry in the keychain (with a String as the new value).
	/// If a matching entry doesn't exist, an error is thrown.
	///
	func updateKey(
		_ key       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {
		
		guard let keyData = key.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try updateKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Updates an entry in the keychain (with a CryptoKit key as the value).
	/// If a matching entry doesn't exist, an error is thrown.
	///
	func updateKey<T: GenericPasswordConvertible>(
		_ key       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {

		let keyData = key.rawRepresentation
		
		try updateKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Store
	// --------------------------------------------------
	
	/// Stores an entry in the keychain (with Data as the value).
	/// If the entry doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	func storeKey(
		_ key       : Data,
		account     : String,
		accessGroup : String, // always required (see notes atop)
		mixins      : [String: Any]? = nil
	) throws {
		
		let exists = try keyExists(account: account, accessGroup: accessGroup)
		if exists {
			try updateKey(key, account: account, accessGroup: accessGroup, mixins: mixins)
		} else {
			try addKey(key, account: account, accessGroup: accessGroup, mixins: mixins)
		}
	}
	
	/// Stores an entry in the keychcain (with a String as the value).
	/// If the entry doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	func storeKey(
		_ key       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {
		
		guard let keyData = key.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try storeKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Stores an entry in the keychain (with a CryptoKit key as the value).
	/// If the entry doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	func storeKey<T: GenericPasswordConvertible>(
		_ key       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws {

		let keyData = key.rawRepresentation
		
		try storeKey(keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Exists
	// --------------------------------------------------
	
	func keyExists(
		account     : String,
		accessGroup : String // always required (see notes atop)
	) throws -> Bool {
		
		let context = LAContext()
		context.interactionNotAllowed = true // <- don't prompt user
		
		var query = [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecReturnData as String] = false // <- don't need it, don't want it
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

	// --------------------------------------------------
	// MARK: Read
	// --------------------------------------------------
	
	/// Reads raw Data from the keychain.
	///
	func readKey(
		account     : String,
		accessGroup : String, // always required (see notes atop)
		mixins      : [String: Any]? = nil
	) throws -> Data? {
		
		// Seek a generic password with the given account.
		var query = mixins ?? [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
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
	
	/// Reads data from the keychain, and interprets it as a String.
	///
	func readKey(
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws -> String? {
		
		if let data: Data = try readKey(account: account, accessGroup: accessGroup, mixins: mixins) {
			return String(data: data, encoding: .utf8)
		}
		return nil
	}
	
	/// Reads a CryptoKit key from the keychain.
	///
	func readKey<T: GenericPasswordConvertible>(
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]? = nil
	) throws -> T? {

		if let data: Data = try readKey(account: account, accessGroup: accessGroup, mixins: mixins) {
			return try T(rawRepresentation: data)
		}
		return nil
	}
	
	// --------------------------------------------------
	// MARK: Delete
	// --------------------------------------------------
	
	enum DeleteKeyResult {
		case itemDeleted
		case itemNotFound
	}
	
	/// Removes any existing key with the given account.
	@discardableResult
	func deleteKey(
		account     : String,
		accessGroup : String // always required (see notes atop)
	) throws -> DeleteKeyResult {
		
		var query = [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		
		switch SecItemDelete(query as CFDictionary) {
			case errSecSuccess      : return .itemDeleted
			case errSecItemNotFound : return .itemNotFound // OK to ignore ItemNotFound
			case let status:
				throw KeyStoreError("Unexpected deletion error: \(status.message)")
		}
	}
}
