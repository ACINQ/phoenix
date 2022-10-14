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



class Prefs_BackupTransactions {
	
	private enum Key: String {
		case hasCKRecordZone
		case hasDownloadedCKRecords
		case backupTransactions_enabled
		case backupTransactions_useCellularData
		case backupTransactions_useUploadDelay
	}
	
	private var defaults: UserDefaults {
		return Prefs.shared.defaults
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
	
	func closeWallet(encryptedNodeId: String) {
		
		defaults.removeObject(forKey: recordZoneCreatedKey(encryptedNodeId))
		defaults.removeObject(forKey: hasDownloadedRecordsKey(encryptedNodeId))
		defaults.removeObject(forKey: Key.backupTransactions_enabled.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useCellularData.rawValue)
		defaults.removeObject(forKey: Key.backupTransactions_useUploadDelay.rawValue)
		
		// Reset any publishers with stored state
		isEnabledPublisher.send(self.isEnabled)
	}
}
	
// --------------------------------------------------
// MARK: -
// --------------------------------------------------

class Prefs_BackupSeed {
	
	private enum Key: String {
		case backupSeed_enabled
		case backupSeed_hasUploadedSeed
		case backupSeed_name
		case manualBackup_taskDone
	}
	
	private var defaults: UserDefaults {
		return Prefs.shared.defaults
	}
	
	lazy private(set) var isEnabled_publisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.isEnabled)
	}()
	
	var isEnabled: Bool {
		get {
			let key = Key.backupSeed_enabled.rawValue
			if defaults.object(forKey: key) != nil {
				return defaults.bool(forKey: key)
			} else {
				return false // default value
			}
		}
		set {
			let key = Key.backupSeed_enabled.rawValue
			defaults.set(newValue, forKey: key)
			isEnabled_publisher.send(newValue)
		}
	}
	
	lazy private(set) var hasUploadedSeed_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func hasUploadedSeed_key(_ encryptedNodeId: String) -> String {
		return "\(Key.backupSeed_hasUploadedSeed.rawValue)-\(encryptedNodeId)"
	}
	
	func hasUploadedSeed(encryptedNodeId: String) -> Bool {
		
		return defaults.bool(forKey: hasUploadedSeed_key(encryptedNodeId))
	}
	
	func setHasUploadedSeed(_ value: Bool, encryptedNodeId: String) {
		
		let key = hasUploadedSeed_key(encryptedNodeId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
		hasUploadedSeed_publisher.send()
	}
	
	lazy private(set) var name_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func name_key(_ encryptedNodeId: String) -> String {
		return "\(Key.backupSeed_name)-\(encryptedNodeId)"
	}
	
	func name(encryptedNodeId: String) -> String? {
		
		return defaults.string(forKey: name_key(encryptedNodeId))
	}
	
	func setName(_ value: String?, encryptedNodeId: String) {
		
		let key = name_key(encryptedNodeId)
		let oldValue = name(encryptedNodeId: encryptedNodeId) ?? ""
		let newValue = value ?? ""
		
		if oldValue != newValue {
			if newValue.isEmpty {
				defaults.removeObject(forKey: key)
			} else {
				defaults.setValue(newValue, forKey: key)
			}
			setHasUploadedSeed(false, encryptedNodeId: encryptedNodeId)
			name_publisher.send()
		}
	}
	
	lazy private(set) var manualBackup_taskDone_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func manualBackup_taskDone_key(_ encryptedNodeId: String) -> String {
		return "\(Key.manualBackup_taskDone)-\(encryptedNodeId)"
	}
	
	func manualBackup_taskDone(encryptedNodeId: String) -> Bool {
		
		return defaults.bool(forKey: manualBackup_taskDone_key(encryptedNodeId))
	}
	
	func manualBackup_setTaskDone(_ newValue: Bool, encryptedNodeId: String) {
		
		let key = manualBackup_taskDone_key(encryptedNodeId)
		if newValue {
			defaults.setValue(newValue, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
		manualBackup_taskDone_publisher.send()
	}
	
	func closeWallet(encryptedNodeId: String) {
		
		defaults.removeObject(forKey: Key.backupSeed_enabled.rawValue)
		defaults.removeObject(forKey: hasUploadedSeed_key(encryptedNodeId))
		defaults.removeObject(forKey: name_key(encryptedNodeId))
		defaults.removeObject(forKey: manualBackup_taskDone_key(encryptedNodeId))
		
		// Reset any publishers with stored state
		isEnabled_publisher.send(self.isEnabled)
	}
}

extension Prefs {
	
	func backupSeedStatePublisher(_ encryptedNodeId: String) -> AnyPublisher<BackupSeedState, Never> {
		
		let publisher = Publishers.CombineLatest3(
			backupSeed.isEnabled_publisher,            // CurrentValueSubject<Bool, Never>
			backupSeed.hasUploadedSeed_publisher,      // PassthroughSubject<Void, Never>
			backupSeed.manualBackup_taskDone_publisher // PassthroughSubject<Void, Never>
		).map { (backupSeed_isEnabled: Bool, _, _) -> BackupSeedState in
			
			let prefs = Prefs.shared
			
			let backupSeed_hasUploadedSeed = prefs.backupSeed.hasUploadedSeed(encryptedNodeId: encryptedNodeId)
			let manualBackup_taskDone = prefs.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
			
			if backupSeed_isEnabled {
				if backupSeed_hasUploadedSeed {
					return .safelyBackedUp
				} else {
					return .backupInProgress
				}
			} else {
				if manualBackup_taskDone {
					return .safelyBackedUp
				} else {
					return .notBackedUp
				}
			}
		}
		.handleEvents(receiveRequest: { _ in
			
			// Publishers.CombineLatest doesn't fire until all publishers have emitted a value.
			// We don't have have to worry about that with the CurrentValueSubject, because it always has a value.
			// But for the PassthroughSubject publishers, this poses a problem.
			//
			// The other related publishers (Merge & Zip) don't do exactly what we want either.
			// So we're taking the simplest approach, and force-firing the associated PassthroughSubject publishers.
			
			let prefs = Prefs.shared
			
			prefs.backupSeed.hasUploadedSeed_publisher.send()
			prefs.backupSeed.manualBackup_taskDone_publisher.send()
		})
		.eraseToAnyPublisher()
		
		return publisher
	}
}
