import Foundation
import Combine
import CryptoKit
import LocalAuthentication
import SwiftUI

fileprivate let filename = "AppSecurity+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = AppSecurityKey

let PIN_LENGTH = 6

enum PinType: CustomStringConvertible {
	case lockPin
	case spendingPin
	
	var description: String {
		switch self {
			case .lockPin     : return "lockPin"
			case .spendingPin : return "spendingPin"
		}
	}
	
	fileprivate var keyPin: Key {
		switch self {
			case .lockPin     : return Key.lockPin
			case .spendingPin : return Key.spendingPin
		}
	}
	
	fileprivate var keyInvalid: Key {
		switch self {
			case .lockPin     : return Key.invalidLockPin
			case .spendingPin : return Key.invalidSpendingPin
		}
	}
}

struct UnlockError {
	let readSecurityFileError: ReadSecurityFileError?
	let readKeychainError: ReadKeychainError?
	let readRecoveryPhraseError: ReadRecoveryPhraseError?
	
	init(_ readSecurityFileError: ReadSecurityFileError?) {
		self.readSecurityFileError = readSecurityFileError
		self.readKeychainError = nil
		self.readRecoveryPhraseError = nil
	}
	
	init(_ readKeychainError: ReadKeychainError?) {
		self.readSecurityFileError = nil
		self.readKeychainError = readKeychainError
		self.readRecoveryPhraseError = nil
	}
	
	init(_ readRecoveryPhraseError: ReadRecoveryPhraseError?) {
		self.readSecurityFileError = nil
		self.readKeychainError = nil
		self.readRecoveryPhraseError = readRecoveryPhraseError
	}
}

class AppSecurity_Wallet {
	
	private let id: String
#if DEBUG
	private let isDefault: Bool
#endif
	
	/// Changes always posted to the main thread.
	///
	public let enabledSecurityPublisher = CurrentValueSubject<EnabledSecurity, Never>(EnabledSecurity())
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// keychain and/or security file at any given time.
	///
	private let queue = DispatchQueue(label: "AppSecurity")
	
	// --------------------------------------------------------------------------------
	// MARK: Init
	// --------------------------------------------------------------------------------
	
	init(id: String) {
		self.id = id
	#if DEBUG
		self.isDefault = (id == PREFS_DEFAULT_ID)
	#endif
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Publisher
	// --------------------------------------------------------------------------------
	
	private func publishEnabledSecurity(_ value: EnabledSecurity) {
		
		runOnMainThread {
			self.enabledSecurityPublisher.send(value)
		}
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------------------------------------
	
	/// Performs disk IO - use in background thread.
	///
	private func readFromDisk() -> SecurityFile {
		
		let result = SharedSecurity.shared.readSecurityJsonFromDisk()
		let securityFile = try? result.get()
		
		return securityFile ?? SecurityFile()
	}
	
	/// Performs disk IO - use in background thread.
	///
	private func writeToDisk(securityFile: SecurityFile) throws {
		
		var url = SharedSecurity.shared.securityJsonUrl
		
		let jsonData = try JSONEncoder().encode(securityFile)
		try jsonData.write(to: url, options: [.atomic])
		
		do {
			var resourceValues = URLResourceValues()
			resourceValues.isExcludedFromBackup = true
			try url.setResourceValues(resourceValues)
			
		} catch {
			// Don't throw from this error as it's an optimization
			log.error("Error excluding \(url.lastPathComponent) from backup \(error)")
		}
	}
	
	private func calculateEnabledSecurity(_ securityFile: SecurityFile) -> EnabledSecurity {
		
		var enabledSecurity = EnabledSecurity.none
		
		if securityFile.biometrics != nil {
			enabledSecurity.insert(.biometrics)
			enabledSecurity.insert(.advancedSecurity)
			
		} else if securityFile.keychain != nil {
			if getSoftBiometricsEnabled() {
				enabledSecurity.insert(.biometrics)
				
				if getPasscodeFallbackEnabled() {
					enabledSecurity.insert(.passcodeFallback)
				}
			}
			if hasLockPin() {
				enabledSecurity.insert(.lockPin)
			}
			if hasSpendingPin() {
				enabledSecurity.insert(.spendingPin)
			}
		}
		
		return enabledSecurity
	}
	
	private func commonMixins() -> [String: Any] {
		
		var mixins = [String: Any]()
		
		// kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly:
		//
		// > After the first unlock, the data remains accessible until the next restart.
		// > This is recommended for items that need to be accessed by background applications.
		// > Items with this attribute do not migrate to a new device. Thus, after restoring
		// > from a backup of a different device, these items will not be present.
		//
		mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		
		return mixins
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Keychain
	// --------------------------------------------------------------------------------
	
	/// Attempts to extract the mnemonics using the keychain.
	/// If the user hasn't enabled any additional security options, this will succeed.
	/// Otherwise it will fail, and the completion closure will specify the additional security in place.
	///
	public func tryUnlockWithKeychain(
		completion: @escaping (
			_ recoveryPhrase: RecoveryPhrase?,
			_ configuration: EnabledSecurity,
			_ error: UnlockError?
		) -> Void
	) {
		
		let finish = {(recoveryPhrase: RecoveryPhrase?, configuration: EnabledSecurity) -> Void in
			
			DispatchQueue.main.async {
				self.publishEnabledSecurity(configuration)
				completion(recoveryPhrase, configuration, nil)
			}
		}
		
		let dangerZone = {(error: UnlockError, configuration: EnabledSecurity) -> Void in
			
			DispatchQueue.main.async {
				self.publishEnabledSecurity(configuration)
				completion(nil, configuration, error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			// Fetch the "security.json" file.
			// If the file doesn't exist, an empty SecurityFile is returned.
			let diskResult = SharedSecurity.shared.readSecurityJsonFromDisk()
			
			switch diskResult {
			case .failure(let reason):
				
				let securityFile = SecurityFile()
				let configuration = self.calculateEnabledSecurity(securityFile)
				
				switch reason {
				case .fileNotFound:
					return finish(nil, configuration)
					
				case .errorReadingFile: fallthrough
				case .errorDecodingFile:
					return dangerZone(UnlockError(reason), configuration)
				}
				
			case .success(let securityFile):
					
				let configuration = self.calculateEnabledSecurity(securityFile)
					
				let keychainResult = SharedSecurity.shared.readKeychainEntry(securityFile)
				switch keychainResult {
				case .failure(let reason):
							
					switch reason {
					case .keychainOptionNotEnabled:
						return finish(nil, configuration)
						
					case .keychainBoxCorrupted: fallthrough
					case .errorReadingKey: fallthrough
					case .keyNotFound: fallthrough
					case .errorOpeningBox:
						return dangerZone(UnlockError(reason), configuration)
					}
					
				case .success(let cleartextData):
					
					let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
					switch decodeResult {
					case .failure(let reason):
						return dangerZone(UnlockError(reason), configuration)
						
					case .success(let recoveryPhrase):
						return finish(recoveryPhrase, configuration)
						
					} // </switch decodeResult>
				} // </switch keychainResult>
			} // </switch diskResult>
		} // </queue.async>
	}
	
	/// Updates the keychain & security file to include a keychain entry.
	/// This is a destructive action - existing entries will be removed from
	/// both the keychain & security file.
	///
	/// It is designed to be called either:
	/// - we need to bootstrap the system on first launch
	/// - the user is explicitly disabling existing security options
	///
	public func addKeychainEntry(
		recoveryPhrase : RecoveryPhrase,
		completion     : @escaping (_ error: Error?) -> Void
	) {
		
		let succeed = {(securityFile: SecurityFile) -> Void in
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.publishEnabledSecurity(newEnabledSecurity)
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		let recoveryPhraseData: Data
		do {
			recoveryPhraseData = try JSONEncoder().encode(recoveryPhrase)
		} catch {
			log.error("recoveryPhrase.jsonEncoding error")
			return fail(error)
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let lockingKey = SymmetricKey(size: .bits256)
			
			let sealedBox: ChaChaPoly.SealedBox
			do {
				sealedBox = try ChaChaPoly.seal(recoveryPhraseData, using: lockingKey)
			} catch {
				return fail(error)
			}
			
			let keyInfo = KeyInfo_ChaChaPoly(sealedBox: sealedBox)
			let securityFile = SecurityFile(keychain: keyInfo)
			
			// Order matters !
			// Don't lock out the user from their wallet !
			//
			// There are 3 scenarios in which this method may be called:
			//
			// 1. App was launched for the first time.
			//    There are no entries in the keychain.
			//    The security.json file doesn't exist.
			//
			// 2. User has existing security options, but is choosing to disable them.
			//    There are existing entries in the keychain.
			//    The security.json file exists, and contains entries.
			//
			// 3. Something bad happened during app launch.
			//    We discovered a corrupt security.json file,
			//    or necessary keychain entries have gone missing.
			//    When this occurs, the system invokes the various `backup` functions.
			//    This creates a copy of the security.json file & keychain entries.
			//    Afterwards this function is called.
			//    And we can treat this scenario as the equivalent of a first app launch.
			//
			// So situation #2 is the dangerous one.
			// Consider what happens if:
			//
			// - we delete the existing entry from the OS keychain
			// - then the app crashes
			//
			// Answer => we just lost the user's data ! :(
			//
			// So we're careful to to perform operations in a particular order here:
			//
			// - add new entry to OS keychain (account=keychain)
			// - write security.json file to disk
			// - then we can safely remove the old entries from the OS keychain (account=biometrics)
			
			let keychain = GenericPasswordStore()
			do {
				// Access control considerations:
				//
				// This is only for fetching the databaseKey,
				// which we only need to do once when launching the app.
				// So we shouldn't need access to the keychain item when the device is locked.
				
				let mixins = self.commonMixins()
				try keychain.storeKey( lockingKey,
				              account: Key.lockingKey_keychain.value(self.id),
				          accessGroup: Key.lockingKey_keychain.accessGroup.value,
				               mixins: mixins)
				
			} catch {
				log.error("keychain.storeKey(account: keychain): error: \(error)")
				return fail(error)
			}
			
			do {
				try self.writeToDisk(securityFile: securityFile)
			} catch {
				log.error("writeToDisk(securityFile): error: \(error)")
				return fail(error)
			}
			
			// Now we can safely delete the previous entry in the OS keychain (if it exists)
			do {
				try keychain.deleteKey(
					account     : Key.lockingKey_biometrics.value(self.id),
					accessGroup : Key.lockingKey_biometrics.accessGroup.value
				)
				
			} catch {/* ignored */}
			
			succeed(securityFile)
			
		} // </queue.async>
	}
	
	public func setSoftBiometrics(
		enabled    : Bool,
		completion : @escaping (_ error: Error?) -> Void
	) -> Void {
		log.trace("setSoftBiometrics(\(enabled))")
		
		let succeed = {
			let securityFile = self.readFromDisk()
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.publishEnabledSecurity(newEnabledSecurity)
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let keychain = GenericPasswordStore()
			let account     = Key.softBiometrics.value(self.id)
			let accessGroup = Key.softBiometrics.accessGroup.value
			
			if enabled {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( "true",
					              account: account,
					          accessGroup: accessGroup,
					               mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: softBiometrics): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				} catch {
					log.error("keychain.deleteKey(account: softBiometrics): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	public func getSoftBiometricsEnabled() -> Bool {
		
		let keychain = GenericPasswordStore()
		let account     = Key.softBiometrics.value(self.id)
		let accessGroup = Key.softBiometrics.accessGroup.value
		
		var enabled = false
		do {
			let value: String? = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.readKey(account: softBiometrics): error: \(error)")
		}
		
		return enabled
	}
	
	public func setPasscodeFallback(
		enabled    : Bool,
		completion : @escaping (_ error: Error?) -> Void
	) -> Void {
		log.trace("setPasscodeFallback(\(enabled))")
		
		let succeed = {
			let securityFile = self.readFromDisk()
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.publishEnabledSecurity(newEnabledSecurity)
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let keychain = GenericPasswordStore()
			let account     = Key.passcodeFallback.value(self.id)
			let accessGroup = Key.passcodeFallback.accessGroup.value
			
			if enabled {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( "true",
									  account: account,
								 accessGroup: accessGroup,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: passcodeFallback): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				
				} catch {
					log.error("keychain.deleteKey(account: passcodeFallback): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	public func getPasscodeFallbackEnabled() -> Bool {
		
		let keychain = GenericPasswordStore()
		let account     = Key.passcodeFallback.value(self.id)
		let accessGroup = Key.passcodeFallback.accessGroup.value
		
		var enabled = false
		do {
			let value: String? = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.readKey(account: passcodeFallback): error: \(error)")
		}
		
		return enabled
	}
	
	// --------------------------------------------------------------------------------
	// MARK: PIN
	// --------------------------------------------------------------------------------
	
	func setPin(
		_ pin      : String?,
		_ type     : PinType,
		completion : @escaping (_ error: Error?) -> Void
	) -> Void {
		log.trace("setPin(\(pin == nil ? "<nil>" : "<non-nil>"), \(type)")
		
		if let pin {
			precondition(pin.isValidPIN, "Attempting to set invalid PIN for type \(type)")
		}
		
		let succeed = {
			let securityFile = self.readFromDisk()
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.publishEnabledSecurity(newEnabledSecurity)
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let keychain = GenericPasswordStore()
			let account     = type.keyPin.value(self.id)
			let accessGroup = type.keyPin.accessGroup.value
			
			if let pin {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( pin,
									  account: account,
								 accessGroup: accessGroup,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: \(account)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				
				} catch {
					log.error("keychain.deleteKey(account: \(account)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	func getPin(_ type: PinType) -> String? {
		
		let keychain = GenericPasswordStore()
		let account     = type.keyPin.value(id)
		let accessGroup = type.keyPin.accessGroup.value
		
		var pin: String? = nil
		do {
			let value: String? = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup
			)
			
			if let value, value.isValidPIN {
				pin = value
			}
			
		} catch {
			log.error("keychain.readKey(account: \(account)): error: \(error)")
		}
		
		return pin
	}
	
	func setInvalidPin(
		_ invalidPin : InvalidPin?,
		_ type       : PinType,
		completion   : @escaping (_ error: Error?) -> Void
	) -> Void {
		if let invalidPin {
			log.trace("setInvalidPin(<count = \(invalidPin.count)>, \(type))")
		} else {
			log.trace("setInvalidPin(<nil>, \(type))")
		}
		
		let succeed = {
			DispatchQueue.main.async {
				completion(nil)
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(error)
			}
		}
		
		var invalidPinData: Data? = nil
		if let invalidPin {
			do {
				invalidPinData = try JSONEncoder().encode(invalidPin)
			} catch {
				return fail(error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let keychain = GenericPasswordStore()
			let account     = type.keyInvalid.value(self.id)
			let accessGroup = type.keyInvalid.accessGroup.value
			
			if let invalidPinData {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( invalidPinData,
									  account: account,
								 accessGroup: accessGroup,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: \(account)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				
				} catch {
					log.error("keychain.deleteKey(account: \(account)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	func getInvalidPin(_ type: PinType) -> InvalidPin? {
		
		let keychain = GenericPasswordStore()
		let account     = type.keyInvalid.value(id)
		let accessGroup = type.keyInvalid.accessGroup.value
		
		var invalidPin: InvalidPin? = nil
		do {
			let value: Data? = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup
			)
			
			if let value {
				do {
					invalidPin = try JSONDecoder().decode(InvalidPin.self, from: value)
				} catch {
					log.error("JSON.decode(account: \(account)): error: \(error)")
				}
			}
			
		} catch {
			log.error("keychain.readKey(account: \(account)): error: \(error)")
		}
		
		return invalidPin
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Lock PIN
	// --------------------------------------------------------------------------------
	
	public func setLockPin(
		_ pin      : String?,
		completion : @escaping (_ error: Error?) -> Void
	) {
		setPin(pin, .lockPin, completion: completion)
	}
	
	public func getLockPin() -> String? {
		return getPin(.lockPin)
	}
	
	public func hasLockPin() -> Bool {
		return getLockPin() != nil
	}
	
	public func setInvalidLockPin(
		_ invalidPin : InvalidPin?,
		completion   : @escaping (_ error: Error?) -> Void
	) {
		setInvalidPin(invalidPin, .lockPin, completion: completion)
	}
	
	public func getInvalidLockPin() -> InvalidPin? {
		return getInvalidPin(.lockPin)
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Spending PIN
	// --------------------------------------------------------------------------------
	
	public func setSpendingPin(
		_ pin      : String?,
		completion : @escaping (_ error: Error?) -> Void
	) {
		setPin(pin, .spendingPin, completion: completion)
	}
	
	public func getSpendingPin() -> String? {
		return getPin(.spendingPin)
	}
	
	public func hasSpendingPin() -> Bool {
		return getSpendingPin() != nil
	}
	
	public func setInvalidSpendingPin(
		_ invalidPin : InvalidPin?,
		completion   : @escaping (_ error: Error?) -> Void
	) {
		setInvalidPin(invalidPin, .spendingPin, completion: completion)
	}
	
	public func getInvalidSpendingPin() -> InvalidPin? {
		return getInvalidPin(.spendingPin)
	}
	
	// --------------------------------------------------------------------------------
	// MARK: BIP353 Address
	// --------------------------------------------------------------------------------
	
	public func setBip353Address(
		_ address: String
	) -> Result<Void, Error> {
		
		let keychain = GenericPasswordStore()
		let account     = Key.bip353Address.value(id)
		let accessGroup = Key.bip353Address.accessGroup.value
		
		do {
			let mixins = self.commonMixins()
			try keychain.storeKey( address,
							  account: account,
						 accessGroup: accessGroup,
								mixins: mixins)
			
			return .success
		} catch {
			log.error("keychain.storeKey(account: bip353Address): error: \(error)")
			return .failure(error)
		}
	}
		
	public func getBip353Address() -> String? {
		
		let keychain = GenericPasswordStore()
		let account     = Key.bip353Address.value(id)
		let accessGroup = Key.bip353Address.accessGroup.value
		
		var addr: String? = nil
		do {
			let value: String? = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup
			)
			
			if let value {
				addr = value
			}
			
		} catch {
			log.error("keychain.readKey(account: bip353Address): error: \(error)")
		}
		
		return addr
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Biometrics
	// --------------------------------------------------------------------------------
	
	private func biometricsPrompt() -> String {
		
		return NSLocalizedString( "App is locked",
		                 comment: "Biometrics prompt to unlock the Phoenix app"
		)
	}
	
	/// Attempts to extract the seed using biometrics (e.g. touchID, faceID)
	///
	public func tryUnlockWithBiometrics(
		prompt: String? = nil,
		completion: @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			// Fetch the "security.json" file.
			// If the file doesn't exist, an empty SecurityFile is returned.
			let securityFile = self.readFromDisk()
			
			if securityFile.biometrics != nil {
				self.tryUnlockWithHardBiometrics(securityFile, prompt, completion)
			} else {
				self.tryUnlockWithSoftBiometrics(securityFile, prompt, completion)
			}
		}
	}
	
	/// This function is called when "advanced security" is detected.
	///
	/// This type of security was removed in v1.4.
	/// It was replaced with the notification-service-extension,
	/// with ability to receive payments when app is running in the background.
	///
	private func tryUnlockWithHardBiometrics(
		_ securityFile : SecurityFile,
		_ prompt       : String? = nil,
		_ completion   : @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		
		let succeed = {(_ recoveryPhrase: RecoveryPhrase) in
			DispatchQueue.main.async {
				completion(Result.success(recoveryPhrase))
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(Result.failure(error))
			}
		}
		
		// The "advanced security" technique removed in v1.4.
		// Replaced with notification-service-extension,
		// with ability to receive payments when app is running in the background.
		//
		let disableAdvancedSecurityAndSucceed = {(_ recoveryPhrase: RecoveryPhrase) in
			
			self.addKeychainEntry(recoveryPhrase: recoveryPhrase) { _ in
				succeed(recoveryPhrase)
			}
		}
		
		// The security.json file tells us which security options have been enabled.
		// This function should only be called if the `biometrics` entry is present.
		guard
			let keyInfo_biometrics = securityFile.biometrics as? KeyInfo_ChaChaPoly,
			let sealedBox_biometrics = try? keyInfo_biometrics.toSealedBox()
		else {
			
			return fail(genericError(400, "SecurityFile.biometrics entry is corrupt"))
		}
		
		
		let context = LAContext()
		context.localizedReason = prompt ?? self.biometricsPrompt()
		context.localizedFallbackTitle = "" // passcode fallback disbaled
		
		var mixins = [String: Any]()
		mixins[kSecUseAuthenticationContext as String] = context
		
		let keychain = GenericPasswordStore()
		let account     = Key.lockingKey_biometrics.value(id)
		let accessGroup = Key.lockingKey_biometrics.accessGroup.value
	
		let fetchedKey: SymmetricKey?
		do {
			fetchedKey = try keychain.readKey(
				account     : account,
				accessGroup : accessGroup,
				mixins      : mixins
			)
		} catch {
			return fail(error)
		}
		
		guard let lockingKey = fetchedKey else {
			return fail(genericError(401, "Biometrics keychain entry missing"))
		}
	
		// Decrypt the databaseKey using the lockingKey
		let cleartextData: Data
		do {
			cleartextData = try ChaChaPoly.open(sealedBox_biometrics, using: lockingKey)
		} catch {
			return fail(error)
		}
		
		let recoveryPhrase: RecoveryPhrase
		do {
			recoveryPhrase = try SharedSecurity.shared.decodeRecoveryPhrase(cleartextData).get()
		} catch {
			return fail(error)
		}
		
	#if targetEnvironment(simulator)
		
		// On the iOS simulator you can fake Touch ID.
		//
		// Features -> Touch ID -> Enroll
		//                      -> Matching touch
		//                      -> Non-matching touch
		//
		// However, it has some shortcomings.
		//
		// On the device:
		//     Attempting to read the entry from the keychain will prompt
		//     the user to authenticate with Touch ID. And the keychain
		//     entry is only returned if Touch ID succeeds.
		//
		// On the simulator:
		//     Attempting to read the entry from the keychain always succceeds.
		//     It does NOT prompt the user for Touch ID,
		//     giving the appearance that we didn't code something properly.
		//     But in reality, this is just a bug in the iOS simulator.
		//
		// So we're going to fake it here.
		
		self.tryGenericBiometrics {(success, error) in
		
			if let error = error {
				fail(error)
			} else {
				disableAdvancedSecurityAndSucceed(recoveryPhrase)
			}
		}
	#else
	
		// iOS device
		disableAdvancedSecurityAndSucceed(recoveryPhrase)
	
	#endif
	}
	
	private func tryUnlockWithSoftBiometrics(
		_ securityFile : SecurityFile,
		_ prompt       : String? = nil,
		_ completion   : @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		
		let succeed = {(_ recoveryPhrase: RecoveryPhrase) in
			DispatchQueue.main.async {
				completion(Result.success(recoveryPhrase))
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(Result.failure(error))
			}
		}
		
		let keychainResult = SharedSecurity.shared.readKeychainEntry(securityFile)
		switch keychainResult {
		case .failure(let error):
			fail(error)
		
		case .success(let cleartextData):
			
			let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
			switch decodeResult {
			case .failure(let error):
				fail(error)
				
			case .success(let recoveryPhrase):
				self.tryGenericBiometrics { (success, error) in
					if success {
						succeed(recoveryPhrase)
					} else {
						fail(error ?? genericError(401, "Biometrics prompt failed / cancelled"))
					}
				}
			} // </switch decodeResult>
		} // </switch keychainResult>
	}
	
	private func tryGenericBiometrics(
		prompt     : String? = nil,
		completion : @escaping (Bool, Error?) -> Void
	) -> Void {
		
		let context = LAContext()
		
		let policy: LAPolicy
		if getPasscodeFallbackEnabled() {
			// Biometrics + Passcode Fallback
			policy = .deviceOwnerAuthentication
		} else if hasLockPin() {
			// Biometrics + Lock PIN Fallback
			policy = .deviceOwnerAuthenticationWithBiometrics
			context.localizedFallbackTitle = "" // do not show (cancel button ==> custom pin)
			context.localizedCancelTitle = String(localized: "Enter Phoenix PIN")
		} else {
			// Biometrics only
			policy = .deviceOwnerAuthenticationWithBiometrics
			context.localizedFallbackTitle = "" // passcode fallback disabled
		}
		
		context.evaluatePolicy( policy,
		       localizedReason: prompt ?? self.biometricsPrompt(),
		                 reply: completion)
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	public func resetWallet() {
		
		let fm = FileManager.default
		let securityJsonUrl = SharedSecurity.shared.securityJsonUrl
		
		if fm.fileExists(atPath: securityJsonUrl.path) {
			do {
				try fm.removeItem(at: securityJsonUrl)
				log.info("Deleted file security.json")
			} catch {
				log.error("Unable to delete security.json: \(error)")
			}
		}
			
		let keychain = GenericPasswordStore()
		
		for key in Key.allCases {
			do {
				let result = try keychain.deleteKey(
					account     : key.value(id),
					accessGroup : key.accessGroup.value
				)
				if result == .itemDeleted {
					log.info(
						"""
						Deleted keychain item: \
						acct(\(key.debugName)) grp(\(key.accessGroup.debugName))
						"""
					)
				}
			} catch {
				log.error(
					"""
					Unable to delete keychain item: \
					acct(\(key.debugName)) grp(\(key.accessGroup.debugName)): \(error)
					"""
				)
			}
		}
		
		AppSecurity.didResetWallet(id)
	}
}

// --------------------------------------------------
// MARK: - Utilities
// --------------------------------------------------

fileprivate func genericError(_ code: Int, _ description: String? = nil) -> NSError {
	
	var userInfo = [String: String]()
	if let description = description {
		userInfo[NSLocalizedDescriptionKey] = description
	}
		
	return NSError(domain: "AppSecurity", code: code, userInfo: userInfo)
}
