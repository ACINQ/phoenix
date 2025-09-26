import Foundation
import Combine

fileprivate let filename = "Prefs+BackupSeed"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey

enum BackupSeedState {
	case notBackedUp
	case backupInProgress
	case safelyBackedUp
}

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// This set pertains to backing up the recovery phrase (seed) in the user's own iCloud account.
///
/// - Note:
/// The values here are NOT shared with other extensions bundled in the app, such as the
/// notification-service-extension. For preferences shared with extensions, see `GroupPrefs`.
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
			return defaults.bool(forKey: Key.backupSeed_enabled.value(id), defaultValue: false)
		}
		set {
			defaults.set(newValue, forKey: Key.backupSeed_enabled.value(id))
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
			return defaults.bool(forKey: Key.backupSeed_hasUploadedSeed.value(id))
		}
		set {
			defaults.setValue(newValue, forKey: Key.backupSeed_hasUploadedSeed.value(id))
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
			return defaults.string(forKey: Key.backupSeed_name.value(id))
		}
		set {
			if self.name == newValue {
				return
			}
			let trimmedName = (newValue ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
			let newName = trimmedName.isEmpty ? nil : trimmedName
			defaults.setValue(newName, forKey: Key.backupSeed_name.value(id))
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
	static func valueDescription(_ prefix: String, _ value: Any) -> String? {
		
		switch prefix {
		case Key.backupSeed_enabled.prefix:
			return printBool(value)
			
		case Key.backupSeed_hasUploadedSeed.prefix:
			return printBool(value)
			
		case Key.backupSeed_name.prefix:
			return printString(value)
			
		case Key.manualBackupDone.prefix:
			return printBool(value)
			
		default:
			return nil
		}
	}
	#endif
}
