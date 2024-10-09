import Foundation
import Combine

fileprivate enum Key: String {
	case backupSeed_enabled
	case backupSeed_hasUploadedSeed
	case backupSeed_name
	case manualBackup_taskDone
}

/// Preferences pertaining to backing up the recovery phrase (seed) in the user's own iCloud account.
///
class Prefs_BackupSeed {
	
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
			runOnMainThread {
				self.isEnabled_publisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var hasUploadedSeed_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func hasUploadedSeed_key(_ walletId: WalletIdentifier) -> String {
		return "\(Key.backupSeed_hasUploadedSeed.rawValue)-\(walletId.prefsKeySuffix)"
	}
	
	func hasUploadedSeed(_ walletId: WalletIdentifier) -> Bool {
		
		return defaults.bool(forKey: hasUploadedSeed_key(walletId))
	}
	
	func setHasUploadedSeed(_ value: Bool, _ walletId: WalletIdentifier) {
		
		let key = hasUploadedSeed_key(walletId)
		if value == true {
			defaults.setValue(value, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
		runOnMainThread {
			self.hasUploadedSeed_publisher.send()
		}
	}
	
	lazy private(set) var name_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func name_key(_ walletId: WalletIdentifier) -> String {
		return "\(Key.backupSeed_name)-\(walletId.prefsKeySuffix)"
	}
	
	func name(_ walletId: WalletIdentifier) -> String? {
		
		return defaults.string(forKey: name_key(walletId))
	}
	
	func setName(_ value: String?, _ walletId: WalletIdentifier) {
		
		let key = name_key(walletId)
		let oldValue = name(walletId) ?? ""
		let newValue = value ?? ""
		
		if oldValue != newValue {
			if newValue.isEmpty {
				defaults.removeObject(forKey: key)
			} else {
				defaults.setValue(newValue, forKey: key)
			}
			setHasUploadedSeed(false, walletId)
			runOnMainThread {
				self.name_publisher.send()
			}
			
		}
	}
	
	lazy private(set) var manualBackup_taskDone_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func manualBackup_taskDone_key(_ walletId: WalletIdentifier) -> String {
		return "\(Key.manualBackup_taskDone)-\(walletId.prefsKeySuffix)"
	}
	
	func manualBackup_taskDone(_ walletId: WalletIdentifier) -> Bool {
		
		return defaults.bool(forKey: manualBackup_taskDone_key(walletId))
	}
	
	func manualBackup_setTaskDone(_ newValue: Bool, _ walletId: WalletIdentifier) {
		
		let key = manualBackup_taskDone_key(walletId)
		if newValue {
			defaults.setValue(newValue, forKey: key)
		} else {
			defaults.removeObject(forKey: key)
		}
		runOnMainThread {
			self.manualBackup_taskDone_publisher.send()
		}
	}
	
	func resetWallet(_ walletId: WalletIdentifier) {
		
		defaults.removeObject(forKey: Key.backupSeed_enabled.rawValue)
		defaults.removeObject(forKey: hasUploadedSeed_key(walletId))
		defaults.removeObject(forKey: name_key(walletId))
		defaults.removeObject(forKey: manualBackup_taskDone_key(walletId))
		
		// Reset any publishers with stored state
		runOnMainThread {
			self.isEnabled_publisher.send(self.isEnabled)
		}
	}
}

extension Prefs {
	
	func backupSeedStatePublisher(_ walletId: WalletIdentifier) -> AnyPublisher<BackupSeedState, Never> {
		
		let publisher = Publishers.CombineLatest3(
			backupSeed.isEnabled_publisher,            // CurrentValueSubject<Bool, Never>
			backupSeed.hasUploadedSeed_publisher,      // PassthroughSubject<Void, Never>
			backupSeed.manualBackup_taskDone_publisher // PassthroughSubject<Void, Never>
		).map { (backupSeed_isEnabled: Bool, _, _) -> BackupSeedState in
			
			let prefs = Prefs.shared
			
			let backupSeed_hasUploadedSeed = prefs.backupSeed.hasUploadedSeed(walletId)
			let manualBackup_taskDone = prefs.backupSeed.manualBackup_taskDone(walletId)
			
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
			
			// On iOS 15:
			//   * Causes a crash
			//   * Solution is to delay these calls until next runloop cycle
			//
			// On iOS 16
			//   * Causes a runtime warning: Publishing changes from within view updates is not allowed
			//   * Solution is to delay these calls until next runloop cycle
			// 
			DispatchQueue.main.async {
				prefs.backupSeed.hasUploadedSeed_publisher.send()
				prefs.backupSeed.manualBackup_taskDone_publisher.send()
			}
		})
		.eraseToAnyPublisher()
		
		return publisher
	}
}
