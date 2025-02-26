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
	case hasDownloadedContacts_v2
	case hasReUploadedPayments
}

fileprivate enum KeyDeprecated: String {
	case hasDownloadedContacts_v1 = "hasDownloadedContacts"
}

/// Preferences pertaining to backing up payment history in the user's own iCloud account.
///
class Prefs_BackupTransactions {
	
	private var defaults: UserDefaults {
		return Prefs.shared.defaults
	}
	
	/// Updating publishers should always be done on the main thread.
	/// Otherwise we risk updating UI components on a background thread, which is dangerous.
	///
	private func runOnMainThread(_ block: @escaping () -> Void) {
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
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
			runOnMainThread {
				self.isEnabledPublisher.send(newValue)
			}
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
	
	private func recordZoneCreatedKey(_ walletId: WalletIdentifier) -> String {
		return "\(Key.hasCKRecordZone_v2.rawValue)-\(walletId.prefsKeySuffix)"
	}
	
	func recordZoneCreated(_ walletId: WalletIdentifier) -> Bool {
		return defaults.bool(forKey: recordZoneCreatedKey(walletId))
	}
	
	func setRecordZoneCreated(_ value: Bool, _ walletId: WalletIdentifier) {
		let key = recordZoneCreatedKey(walletId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
	}
	
	private func hasDownloadedPaymentsKey(_ walletId: WalletIdentifier) -> String {
		return "\(Key.hasDownloadedPayments.rawValue)-\(walletId.prefsKeySuffix)"
	}
	
	func hasDownloadedPayments(_ walletId: WalletIdentifier) -> Bool {
		return defaults.bool(forKey: hasDownloadedPaymentsKey(walletId))
	}
	
	func markHasDownloadedPayments(_ walletId: WalletIdentifier) {
		defaults.setValue(true, forKey: hasDownloadedPaymentsKey(walletId))
	}
	
	private func hasDownloadedContactsKey(_ walletId: WalletIdentifier) -> String {
		return "\(Key.hasDownloadedContacts_v2.rawValue)-\(walletId.prefsKeySuffix)"
	}
	
	func hasDownloadedContacts(_ walletId: WalletIdentifier) -> Bool {
		return defaults.bool(forKey: hasDownloadedContactsKey(walletId))
	}
	
	func markHasDownloadedContacts(_ walletId: WalletIdentifier) {
		defaults.setValue(true, forKey: hasDownloadedContactsKey(walletId))
	}
	
	private func hasReUploadedPaymentsKey(_ walletId: WalletIdentifier) -> String {
		return "\(Key.hasReUploadedPayments.rawValue)-\(walletId.prefsKeySuffix)"
	}
	
	func hasReUploadedPayments(_ walletId: WalletIdentifier) -> Bool {
		return defaults.bool(forKey: hasReUploadedPaymentsKey(walletId))
	}
	
	func markHasReUploadedPayments(_ walletId: WalletIdentifier) {
		defaults.setValue(true, forKey: hasReUploadedPaymentsKey(walletId))
	}
	
	func resetWallet(_ walletId: WalletIdentifier) {
		
		defaults.removeObject(forKey: recordZoneCreatedKey(walletId))
		defaults.removeObject(forKey: hasDownloadedPaymentsKey(walletId))
		defaults.removeObject(forKey: hasDownloadedContactsKey(walletId))
		defaults.removeObject(forKey: hasReUploadedPaymentsKey(walletId))
		defaults.removeObject(forKey: Key.backupTransactions_enabled.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useCellularData.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useUploadDelay.rawValue)
		
		defaults.removeObject(forKey: "\(KeyDeprecated.hasDownloadedContacts_v1.rawValue)-\(walletId.encryptedNodeId)")
		
		// Reset any publishers with stored state
		runOnMainThread {
			self.isEnabledPublisher.send(self.isEnabled)
		}
	}
}
