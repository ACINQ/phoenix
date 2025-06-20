import Foundation
import Combine

fileprivate let filename = "Prefs+BackupTransactions"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
	case enabled
	case useCellularData
	case useUploadDelay
	case recordZoneCreated
	case hasDownloadedPayments
	case hasDownloadedContacts
	case hasReUploadedPayments
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .enabled               : return "backupTransactions_enabled"
			case .useCellularData       : return "backupTransactions_useCellularData"
			case .useUploadDelay        : return "backupTransactions_useUploadDelay"
			case .recordZoneCreated     : return "hasCKRecordZone_v2"
			case .hasDownloadedPayments : return "hasDownloadedCKRecords"
			case .hasDownloadedContacts : return "hasDownloadedContacts_v2"
			case .hasReUploadedPayments : return "hasReUploadedPayments"
		}
	}
	
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

fileprivate enum KeyDeprecated: String {
	case hasDownloadedContacts_v1 = "hasDownloadedContacts"
}

/// Preferences pertaining to backing up payment history in the user's own iCloud account.
///
class Prefs_BackupTransactions {
	
	private static var defaults: UserDefaults {
		return Prefs.defaults
	}
	
	private let id: String
	private let defaults: UserDefaults
#if DEBUG
	private let isDefault: Bool
#endif
	
	init(id: String) {
		self.id = id
		self.defaults = Self.defaults
	#if DEBUG
		self.isDefault = (id == PREFS_DEFAULT_ID)
	#endif
	}
	
	// --------------------------------------------------
	// MARK: User Options
	// --------------------------------------------------
	
	lazy private(set) var isEnabledPublisher = {
		CurrentValueSubject<Bool, Never>(self.isEnabled)
	}()
	
	var isEnabled: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.enabled.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.enabled.value(id))
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
		}
	}
	
	var useCellular: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.useCellularData.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.useCellularData.value(id))
		}
	}
	
	var useUploadDelay: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.useUploadDelay.value(id), defaultValue: false)
		}
		set {
			defaults.set(newValue, forKey: Key.useUploadDelay.value(id))
		}
	}
	
	var recordZoneCreated: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.recordZoneCreated.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.recordZoneCreated.value(id))
		}
	}
	
	var hasDownloadedPayments: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasDownloadedPayments.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hasDownloadedPayments.value(id))
		}
	}
	
	var hasDownloadedContacts: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasDownloadedContacts.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hasDownloadedContacts.value(id))
		}
	}
	
	var hasReUploadedPayments: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasReUploadedPayments.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hasReUploadedPayments.value(id))
		}
	}
	
	// --------------------------------------------------
	// MARK: Load Wallet
	// --------------------------------------------------
	
	static func loadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		let d = self.defaults
		let oldId = PREFS_DEFAULT_ID
		let newId = walletId.prefsKeySuffix
		
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
	
	func resetWallet() {
		log.trace(#function)
		
		for key in Key.allCases {
			defaults.removeObject(forKey: key.value(id))
		}
		
		defaults.removeObject(forKey: "\(KeyDeprecated.hasDownloadedContacts_v1.rawValue)-\(id)")
	}
	
	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration_toBuild92() {
		log.trace(#function)
		
		let d = self.defaults
		let newId = PREFS_DEFAULT_ID
		
		for key in Key.allCases {
			let oldKey = key.deprecatedValue
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
	// MARK: Debugging
	// --------------------------------------------------
	
	@inline(__always)
	func maybeLogDefaultAccess(_ functionName: String) {
	#if DEBUG
		if isDefault {
			log.info("Default access: \(functionName)")
		}
	#endif
	}
	
	#if DEBUG
	static func isKnownKey(_ key: String) -> Bool {
		
		for knownKey in Key.allCases {
			if key.hasPrefix(knownKey.prefix) {
				return true
			}
		}
		
		return false
	}
	
	static func valueDescription(_ key: String, _ value: Any) -> String? {
		
		let printBool = {() -> String in
			let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
			return "<Bool: \(desc)>"
		}
		
		switch key {
		case Key.enabled.prefix:
			return printBool()
			
		case Key.useCellularData.prefix:
			return printBool()
			
		case Key.useUploadDelay.prefix:
			return printBool()
			
		case Key.recordZoneCreated.prefix:
			return printBool()
			
		case Key.hasDownloadedPayments.prefix:
			return printBool()
			
		case Key.hasDownloadedContacts.prefix:
			return printBool()
			
		case Key.hasReUploadedPayments.prefix:
			return printBool()
			
		default:
			return nil
		}
	}
	#endif
}
