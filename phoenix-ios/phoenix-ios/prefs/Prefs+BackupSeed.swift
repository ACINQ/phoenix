import Foundation
import Combine

fileprivate let filename = "Prefs+BackupSeed"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
	case enabled
	case hasUploadedSeed
	case name
	case manualBackupDone
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .enabled          : return "backupSeed_enabled"
			case .hasUploadedSeed  : return "backupSeed_hasUploadedSeed"
			case .name             : return "backupSeed_name"
			case .manualBackupDone : return "manualBackup_taskDone"
		}
	}
	
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

enum BackupSeedState {
	case notBackedUp
	case backupInProgress
	case safelyBackedUp
}

/// Preferences pertaining to backing up the recovery phrase (seed) in the user's own iCloud account.
///
class Prefs_BackupSeed {
	
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
			return defaults.bool(forKey: Key.enabled.value(id), defaultValue: false)
		}
		set {
			defaults.set(newValue, forKey: Key.enabled.value(id))
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var hasUploadedSeedPublisher = {
		return CurrentValueSubject<Bool, Never>(self.hasUploadedSeed)
	}()
	
	var hasUploadedSeed: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasUploadedSeed.value(id))
		}
		set {
			defaults.setValue(newValue, forKey: Key.hasUploadedSeed.value(id))
			runOnMainThread {
				self.hasUploadedSeedPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var namePublisher = {
		CurrentValueSubject<String?, Never>(self.name)
	}()
	
	var name: String? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.string(forKey: Key.name.value(id))
		}
		set {
			if self.name == newValue {
				return
			}
			let trimmedName = (newValue ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
			let newName = trimmedName.isEmpty ? nil : trimmedName
			defaults.setValue(newName, forKey: Key.name.value(id))
			runOnMainThread {
				self.namePublisher.send(newName)
				self.hasUploadedSeed = false
			}
		}
	}
	
	lazy private(set) var manualBackupDonePublisher = {
		return CurrentValueSubject<Bool, Never>(self.manualBackupDone)
	}()
	
	var manualBackupDone: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.manualBackupDone.value(id))
		}
		set {
			defaults.setValue(newValue, forKey: Key.manualBackupDone.value(id))
			runOnMainThread {
				self.manualBackupDonePublisher.send(newValue)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Seed State
	// --------------------------------------------------
	
	func statePublisher() -> AnyPublisher<BackupSeedState, Never> {
		
		return Publishers.CombineLatest3(
			self.isEnabledPublisher,             // CurrentValueSubject<Bool, Never>
			self.hasUploadedSeedPublisher,       // CurrentValueSubject<Bool, Never>
			self.manualBackupDonePublisher       // CurrentValueSubject<Bool, Never>
		).map { (isEnabled: Bool, hasUploadedSeed: Bool, manualBackupDone: Bool) -> BackupSeedState in
			
			if isEnabled {
				if hasUploadedSeed {
					return .safelyBackedUp
				} else {
					return .backupInProgress
				}
			} else {
				if manualBackupDone {
					return .safelyBackedUp
				} else {
					return .notBackedUp
				}
			}
			
		}.eraseToAnyPublisher()
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
		
		let printString = {() -> String in
			let desc = (value as? String) ?? "unknown"
			return "<String: \(desc)>"
		}
		
		let printBool = {() -> String in
			let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
			return "<Bool: \(desc)>"
		}
		
		switch key {
		case Key.enabled.prefix:
			return printBool()
			
		case Key.hasUploadedSeed.prefix:
			return printBool()
			
		case Key.name.prefix:
			return printString()
			
		case Key.manualBackupDone.prefix:
			return printBool()
			
		default:
			return nil
		}
	}
	#endif
}
