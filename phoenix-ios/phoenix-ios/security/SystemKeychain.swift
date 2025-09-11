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
 *
 * Notes concerning the mixins:
 *
 * Say that you add an item to the keychain with `kSecAttrAccessible` set to nil.
 * Then later you attempt to update this item, however you use a different `kSecAttrAccessible` value.
 * Turns out, the system keychain will return an error.
 */

import Foundation
import CryptoKit
import Security
import LocalAuthentication

class SystemKeychain {

	// --------------------------------------------------
	// MARK: Add
	// --------------------------------------------------
	
	/// Adds a NEW item in the keychain (with Data as the value).
	/// If a matching entry already exists, an error is thrown.
	///
	static func addItem(
		value       : Data,
		account     : String,
		accessGroup : String,       // always required (see notes atop)
		mixins      : [String: Any] // always required (see notes atop)
	) throws {
		
		var query = mixins
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecValueData as String] = value
		
		// Add the key data.
		let status = SecItemAdd(query as CFDictionary, nil)
		guard status == errSecSuccess else {
			throw KeyStoreError("Unable to store item: \(status.message)")
		}
	}
	
	/// Adds a NEW entry in the keychcain (with a String as the value).
	/// If a matching entry already exists, an error is thrown.
	///
	static func addItem(
		value       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {
		
		guard let valueData = value.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try addItem(value: valueData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Adds a NEW item in the keychain (with a CryptoKit key as the value).
	/// If a matching item already exists, an error is thrown.
	///
	static func addItem<T: GenericPasswordConvertible>(
		value       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {

		let keyData = value.rawRepresentation
		
		try addItem(value: keyData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Update
	// --------------------------------------------------
	
	/// Updates an item in the keychain (with Data as the new value).
	/// If a matching item doesn't exist, an error is thrown.
	///
	static func updateItem(
		value       : Data,
		account     : String,
		accessGroup : String,       // always required (see notes atop)
		mixins      : [String: Any] // always required (see notes atop)
	) throws {
		
		var query = mixins
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		
		var attributes = [String: Any]()
		attributes[kSecValueData as String] = value
		
		// Add the key data.
		let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
		guard status == errSecSuccess else {
			throw KeyStoreError("Unable to update item: \(status.message)")
		}
	}
	
	/// Updates an item in the keychain (with a String as the new value).
	/// If a matching item doesn't exist, an error is thrown.
	///
	static func updateItem(
		value       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {
		
		guard let valueData = value.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try updateItem(value: valueData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Updates an item in the keychain (with a CryptoKit key as the value).
	/// If a matching item doesn't exist, an error is thrown.
	///
	static func updateItem<T: GenericPasswordConvertible>(
		value       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {

		let valueData = value.rawRepresentation
		
		try updateItem(value: valueData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Store
	// --------------------------------------------------
	
	/// Stores an item in the keychain (with Data as the value).
	/// If the item doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	static func storeItem(
		value       : Data,
		account     : String,
		accessGroup : String,       // always required (see notes atop)
		mixins      : [String: Any] // always required (see notes atop)
	) throws {
		
		let exists = try itemExists(account: account, accessGroup: accessGroup)
		if exists {
			try updateItem(value: value, account: account, accessGroup: accessGroup, mixins: mixins)
		} else {
			try addItem(value: value, account: account, accessGroup: accessGroup, mixins: mixins)
		}
	}
	
	/// Stores an item in the keychcain (with a String as the value).
	/// If the item doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	static func storeItem(
		value       : String,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {
		
		guard let valueData = value.data(using: .utf8) else {
			throw KeyStoreError("Unable to convert string to data using utf8")
		}
		
		try storeItem(value: valueData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	/// Stores an item in the keychain (with a CryptoKit key as the value).
	/// If the item doesn't exit, it's added to the keychain.
	/// If it already exits, it's value is updated.
	///
	static func storeItem<T: GenericPasswordConvertible>(
		value       : T,
		account     : String,
		accessGroup : String,
		mixins      : [String: Any]
	) throws {

		let valueData = value.rawRepresentation
		
		try storeItem(value: valueData, account: account, accessGroup: accessGroup, mixins: mixins)
	}
	
	// --------------------------------------------------
	// MARK: Exists
	// --------------------------------------------------
	
	static func itemExists(
		account     : String,
		accessGroup : String  // always required (see notes atop)
	) throws -> Bool {
		
		// Concerning `mixins`:
		// What seems to be the case is that:
		// * if this method takes a mixins parameter (like the add/update functions above)
		// * and the normal `commonMixins` parameter is passed
		//   i.e.: [kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
		// * then `SecItemCopyMatching` will return `errSecItemNotFound`
		//
		// But:
		// * if you do NOT include `kSecAttrAccessible` in the query
		// * then `SecItemCopyMatching` will return `errSecSuccess`
		//
		// For this reason, this function purposefully does NOT have the `mixins` parameter.
		
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
		let result = SecItemCopyMatching(query as CFDictionary, &item)
		switch result {
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
	static func readItem(
		account     : String,
		accessGroup : String,  // always required (see notes atop)
		context     : LAContext? = nil
	) throws -> Data? {
		
		// Concerning `mixins`:
		// What seems to be the case is that:
		// * if this method takes a mixins parameter (like the add/update functions above)
		// * and the normal `commonMixins` parameter is passed
		//   i.e.: [kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
		// * then `SecItemCopyMatching` will return `errSecItemNotFound`
		//
		// But:
		// * if you do NOT include `kSecAttrAccessible` in the query
		// * then `SecItemCopyMatching` will return `errSecSuccess`
		//
		// For this reason, this function purposefully does NOT have the `mixins` parameter.
		
		// Seek a generic password with the given account.
		var query = [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		query[kSecReturnData as String] = true
		query[kSecMatchLimit as String] = kSecMatchLimitOne
		
		if let context {
			query[kSecUseAuthenticationContext as String] = context
		}
		
		// Find item and cast as data.
		var item: CFTypeRef?
		let result = SecItemCopyMatching(query as CFDictionary, &item)
		switch result {
			case errSecSuccess      : return item as? Data
			case errSecItemNotFound : return nil
			case let status         : throw KeyStoreError("Keychain read failed: \(status.message)")
		}
	}
	
	/// Reads data from the keychain, and interprets it as a String.
	///
	static func readItem(
		account     : String,
		accessGroup : String,
		context     : LAContext? = nil
	) throws -> String? {
		
		if let data: Data = try readItem(account: account, accessGroup: accessGroup, context: context) {
			return String(data: data, encoding: .utf8)
		}
		return nil
	}
	
	/// Reads a CryptoKit key from the keychain.
	///
	static func readItem<T: GenericPasswordConvertible>(
		account     : String,
		accessGroup : String,
		context     : LAContext? = nil
	) throws -> T? {

		if let data: Data = try readItem(account: account, accessGroup: accessGroup, context: context) {
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
	static func deleteItem(
		account     : String,
		accessGroup : String // always required (see notes atop)
	) throws -> DeleteKeyResult {
		
		var query = [String: Any]()
		query[kSecClass as String] = kSecClassGenericPassword
		query[kSecAttrAccount as String] = account
		query[kSecAttrAccessGroup as String] = accessGroup
		query[kSecUseDataProtectionKeychain as String] = true
		
		let result = SecItemDelete(query as CFDictionary)
		switch result {
			case errSecSuccess      : return .itemDeleted
			case errSecItemNotFound : return .itemNotFound // OK to ignore ItemNotFound
			case let status:
				throw KeyStoreError("Unexpected deletion error: \(status.message)")
		}
	}
}
