import Foundation
import Combine

fileprivate let filename = "Prefs+BackupTransactions"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

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
			return defaults.bool(forKey: PrefsKey.backupTxs_enabled.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.backupTxs_enabled.value(id))
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
		}
	}
	
	var useCellular: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.backupTxs_useCellularData.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.backupTxs_useCellularData.value(id))
		}
	}
	
	var useUploadDelay: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.backupTxs_useUploadDelay.value(id), defaultValue: false)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.backupTxs_useUploadDelay.value(id))
		}
	}
	
	var recordZoneCreated: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.recordZoneCreated.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.recordZoneCreated.value(id))
		}
	}
	
	var hasDownloadedPayments: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hasDownloadedPayments.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hasDownloadedPayments.value(id))
		}
	}
	
	var hasDownloadedContacts: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hasDownloadedContacts.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hasDownloadedContacts.value(id))
		}
	}
	
	var hasReUploadedPayments: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hasReUploadedPayments.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hasReUploadedPayments.value(id))
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
	static func valueDescription(_ key: String, _ value: Any) -> String? {
		
		switch key {
		case PrefsKey.backupTxs_enabled.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.backupTxs_useCellularData.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.backupTxs_useUploadDelay.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.recordZoneCreated.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.hasDownloadedPayments.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.hasDownloadedContacts.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.hasReUploadedPayments.prefix:
			return Prefs.printBool(value)
			
		default:
			return nil
		}
	}
	#endif
}
