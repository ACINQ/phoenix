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
	case hasCKRecordZone
	case hasDownloadedCKRecords
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
		return "\(Key.hasCKRecordZone.rawValue)-\(encryptedNodeId)"
	}
	
	func recordZoneCreated(encryptedNodeId: String) -> Bool {
		
		return defaults.bool(forKey: recordZoneCreatedKey(encryptedNodeId))
	}
	
	func setRecordZoneCreated(_ value: Bool, encryptedNodeId: String) {
		
		let key = recordZoneCreatedKey(encryptedNodeId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
	}
	
	private func hasDownloadedRecordsKey(_ encryptedNodeId: String) -> String {
		return "\(Key.hasDownloadedCKRecords.rawValue)-\(encryptedNodeId)"
	}
	
	func hasDownloadedRecords(encryptedNodeId: String) -> Bool {
		
		return defaults.bool(forKey: hasDownloadedRecordsKey(encryptedNodeId))
	}
	
	func setHasDownloadedRecords(_ value: Bool, encryptedNodeId: String) {
		
		let key = hasDownloadedRecordsKey(encryptedNodeId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			// Remove trace of account on disk
			defaults.removeObject(forKey: key)
		}
	}
	
	func resetWallet(encryptedNodeId: String) {
		
		defaults.removeObject(forKey: recordZoneCreatedKey(encryptedNodeId))
		defaults.removeObject(forKey: hasDownloadedRecordsKey(encryptedNodeId))
		defaults.removeObject(forKey: Key.backupTransactions_enabled.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useCellularData.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useUploadDelay.rawValue)
		
		// Reset any publishers with stored state
		isEnabledPublisher.send(self.isEnabled)
	}
}
