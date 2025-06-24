import Foundation
import Combine

fileprivate let filename = "Prefs+BackupTransactions"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey

/// Standard app preferences, stored in the iOS UserDefaults system.
/// 
/// This set pertains to backing up payment history in the user's own iCloud account.
///
/// - Note:
/// The values here are NOT shared with other extensions bundled in the app, such as the
/// notification-service-extension. For preferences shared with extensions, see `GroupPrefs`.
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
			return defaults.bool(forKey: Key.backupTxs_enabled.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.backupTxs_enabled.value(id))
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
		}
	}
	
	var useCellular: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.backupTxs_useCellularData.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.backupTxs_useCellularData.value(id))
		}
	}
	
	var useUploadDelay: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.backupTxs_useUploadDelay.value(id), defaultValue: false)
		}
		set {
			defaults.set(newValue, forKey: Key.backupTxs_useUploadDelay.value(id))
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
		case Key.backupTxs_enabled.prefix:
			return printBool(value)
			
		case Key.backupTxs_useCellularData.prefix:
			return printBool(value)
			
		case Key.backupTxs_useUploadDelay.prefix:
			return printBool(value)
			
		case Key.recordZoneCreated.prefix:
			return printBool(value)
			
		case Key.hasDownloadedPayments.prefix:
			return printBool(value)
			
		case Key.hasDownloadedContacts.prefix:
			return printBool(value)
			
		case Key.hasReUploadedPayments.prefix:
			return printBool(value)
			
		default:
			return nil
		}
	}
	#endif
}
