import Foundation
import Combine

fileprivate enum Key {
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
	
	private let id: String
	
	init(id: String) {
		self.id = id
	}
	
	private var defaults: UserDefaults {
		return Prefs.wallet(id).defaults
	}
	
	lazy private(set) var isEnabledPublisher = {
		CurrentValueSubject<Bool, Never>(self.isEnabled)
	}()
	
	var isEnabled: Bool {
		get { defaults.bool(forKey: Key.enabled.value(id), defaultValue: true) }
		set {
			defaults.set(newValue, forKey: Key.enabled.value(id))
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
		}
	}
	
	var useCellular: Bool {
		get { defaults.bool(forKey: Key.useCellularData.value(id), defaultValue: true) }
		set { defaults.set(newValue, forKey: Key.useCellularData.value(id)) }
	}
	
	var useUploadDelay: Bool {
		get { defaults.bool(forKey: Key.useUploadDelay.value(id), defaultValue: false) }
		set { defaults.set(newValue, forKey: Key.useUploadDelay.value(id)) }
	}
	
	var recordZoneCreated: Bool {
		get { defaults.bool(forKey: Key.recordZoneCreated.value(id)) }
		set { defaults.set(newValue, forKey: Key.recordZoneCreated.value(id))}
	}
	
	var hasDownloadedPayments: Bool {
		get { defaults.bool(forKey: Key.hasDownloadedPayments.value(id)) }
		set { defaults.set(newValue, forKey: Key.hasDownloadedPayments.value(id)) }
	}
	
	var hasDownloadedContacts: Bool {
		get { defaults.bool(forKey: Key.hasDownloadedContacts.value(id)) }
		set { defaults.set(newValue, forKey: Key.hasDownloadedContacts.value(id)) }
	}
	
	var hasReUploadedPayments: Bool {
		get { defaults.bool(forKey: Key.hasReUploadedPayments.value(id)) }
		set { defaults.set(newValue, forKey: Key.hasReUploadedPayments.value(id)) }
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	func resetWallet() {
		
		defaults.removeObject(forKey: Key.enabled.value(id))
		defaults.removeObject(forKey: Key.useCellularData.value(id))
		defaults.removeObject(forKey: Key.useUploadDelay.value(id))
		defaults.removeObject(forKey: Key.recordZoneCreated.value(id))
		defaults.removeObject(forKey: Key.hasDownloadedPayments.value(id))
		defaults.removeObject(forKey: Key.hasDownloadedContacts.value(id))
		defaults.removeObject(forKey: Key.hasReUploadedPayments.value(id))
		
		defaults.removeObject(forKey: "\(KeyDeprecated.hasDownloadedContacts_v1.rawValue)-\(id)")
		
		// Reset any publishers with stored state
		runOnMainThread {
			self.isEnabledPublisher.send(self.isEnabled)
		}
	}
}
