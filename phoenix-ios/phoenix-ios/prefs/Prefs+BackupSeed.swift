import Foundation
import Combine

fileprivate enum Key {
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
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

/// Preferences pertaining to backing up the recovery phrase (seed) in the user's own iCloud account.
///
class Prefs_BackupSeed {
	
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
		get {
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
		get { defaults.bool(forKey: Key.hasUploadedSeed.value(id)) }
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
		get { defaults.string(forKey: Key.name.value(id)) }
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
		get { defaults.bool(forKey: Key.manualBackupDone.value(id)) }
		set {
			defaults.setValue(newValue, forKey: Key.manualBackupDone.value(id))
			runOnMainThread {
				self.manualBackupDonePublisher.send(newValue)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	func resetWallet() {
		
		defaults.removeObject(forKey: Key.enabled.value(id))
		defaults.removeObject(forKey: Key.hasUploadedSeed.value(id))
		defaults.removeObject(forKey: Key.name.value(id))
		defaults.removeObject(forKey: Key.manualBackupDone.value(id))
		
		// Reset any publishers with stored state
		runOnMainThread {
			self.isEnabledPublisher.send(self.isEnabled)
		}
	}
}

enum BackupSeedState {
	case notBackedUp
	case backupInProgress
	case safelyBackedUp
}

extension Prefs {
	
	func backupSeedStatePublisher() -> AnyPublisher<BackupSeedState, Never> {
		
		let publisher = Publishers.CombineLatest3(
			backupSeed.isEnabledPublisher,             // CurrentValueSubject<Bool, Never>
			backupSeed.hasUploadedSeedPublisher,       // CurrentValueSubject<Bool, Never>
			backupSeed.manualBackupDonePublisher       // CurrentValueSubject<Bool, Never>
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
		}
//		.handleEvents(receiveRequest: { _ in
//			
//			// Publishers.CombineLatest doesn't fire until all publishers have emitted a value.
//			// We don't have have to worry about that with the CurrentValueSubject, because it always has a value.
//			// But for the PassthroughSubject publishers, this poses a problem.
//			//
//			// The other related publishers (Merge & Zip) don't do exactly what we want either.
//			// So we're taking the simplest approach, and force-firing the associated PassthroughSubject publishers.
//			
//			let prefs = Prefs.shared
//			
//			// On iOS 15:
//			//   * Causes a crash
//			//   * Solution is to delay these calls until next runloop cycle
//			//
//			// On iOS 16
//			//   * Causes a runtime warning: Publishing changes from within view updates is not allowed
//			//   * Solution is to delay these calls until next runloop cycle
//			// 
//			DispatchQueue.main.async {
//				prefs.backupSeed.hasUploadedSeed_publisher.send()
//				prefs.backupSeed.manualBackup_taskDone_publisher.send()
//			}
//		})
		.eraseToAnyPublisher()
		
		return publisher
	}
}
