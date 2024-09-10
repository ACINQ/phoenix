import Foundation
import Combine

/// The backup state, according to current values in Prefs.
/// Used by `backupSeedStatePublisher`.
///
enum BackupSeedState {
	case notBackedUp
	case backupInProgress
	case safelyBackedUp
}

fileprivate enum Key: String {
	case backupTransactions_enabled
	case backupTransactions_useCellularData
	case backupTransactions_useUploadDelay
	case hasCKRecordZone_v2
	case hasDownloadedPayments = "hasDownloadedCKRecords"
	case hasDownloadedContacts
}

/// Preferences pertaining to backing up payment history in the user's own iCloud account.
///
class Prefs_BackupTransactions {
	
	private var defaults: UserDefaults {
		return Prefs.shared.defaults
	}
	
	lazy private(set) var isEnabledPublisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.isEnabled)
	}()
	
	var isEnabled: Bool {
		get {
			let key = Key.backupTransactions_enabled.rawValue
			if defaults.object(forKey: key) != nil {
				return defaults.bool(forKey: key)
			} else {
				return true // default value
			}
		}
		set {
			let key = Key.backupTransactions_enabled.rawValue
			defaults.set(newValue, forKey: key)
			isEnabledPublisher.send(newValue)
		}
	}
	
	var useCellular: Bool {
		get {
			let key = Key.backupTransactions_useCellularData.rawValue
			if defaults.object(forKey: key) != nil {
				return defaults.bool(forKey: key)
			} else {
				return true // default value
			}
		}
		set {
			let key = Key.backupTransactions_useCellularData.rawValue
			defaults.set(newValue, forKey: key)
		}
	}
	
	var useUploadDelay: Bool {
		get {
			let key = Key.backupTransactions_useUploadDelay.rawValue
			if defaults.object(forKey: key) != nil {
				return defaults.bool(forKey: key)
			} else {
				return false // default value
			}
		}
		set {
			let key = Key.backupTransactions_useUploadDelay.rawValue
			defaults.set(newValue, forKey: key)
		}
	}
	
	private func recordZoneCreatedKey(_ encryptedNodeId: String) -> String {
		return "\(Key.hasCKRecordZone_v2.rawValue)-\(encryptedNodeId)"
	}
	
	func recordZoneCreated(_ encryptedNodeId: String) -> Bool {
		return defaults.bool(forKey: recordZoneCreatedKey(encryptedNodeId))
	}
	
	func setRecordZoneCreated(_ value: Bool, _ encryptedNodeId: String) {
		let key = recordZoneCreatedKey(encryptedNodeId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
	}
	
	private func hasDownloadedPaymentsKey(_ encryptedNodeId: String) -> String {
		return "\(Key.hasDownloadedPayments.rawValue)-\(encryptedNodeId)"
	}
	
	func hasDownloadedPayments(_ encryptedNodeId: String) -> Bool {
		return defaults.bool(forKey: hasDownloadedPaymentsKey(encryptedNodeId))
	}
	
	func markHasDownloadedPayments(_ encryptedNodeId: String) {
		defaults.setValue(true, forKey: hasDownloadedPaymentsKey(encryptedNodeId))
	}
	
	private func hasDownloadedContactsKey(_ encryptedNodeId: String) -> String {
		return "\(Key.hasDownloadedContacts.rawValue)-\(encryptedNodeId)"
	}
	
	func hasDownloadedContacts(_ encryptedNodeId: String) -> Bool {
		return defaults.bool(forKey: hasDownloadedContactsKey(encryptedNodeId))
	}
	
	func markHasDownloadedContacts(_ encryptedNodeId: String) {
		defaults.setValue(true, forKey: hasDownloadedContactsKey(encryptedNodeId))
	}
	
	func resetWallet(encryptedNodeId: String) {
		
		defaults.removeObject(forKey: recordZoneCreatedKey(encryptedNodeId))
		defaults.removeObject(forKey: hasDownloadedPaymentsKey(encryptedNodeId))
		defaults.removeObject(forKey: hasDownloadedContactsKey(encryptedNodeId))
		defaults.removeObject(forKey: Key.backupTransactions_enabled.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useCellularData.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useUploadDelay.rawValue)
		
		// Reset any publishers with stored state
		isEnabledPublisher.send(self.isEnabled)
	}
}
