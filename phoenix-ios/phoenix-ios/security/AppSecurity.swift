import Foundation
import Combine
import CommonCrypto
import CryptoKit
import LocalAuthentication
import SwiftUI

fileprivate let filename = "AppSecurity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

let PIN_LENGTH = 6

/// Represents the availability of Biometrics on the current device.
/// Devices either support TouchID or FaceID,
/// but the user needs to have enabled and enrolled in the service.
///
enum BiometricSupport {
	
	case touchID_available
	case touchID_notAvailable
	case touchID_notEnrolled
	
	case faceID_available
	case faceID_notAvailable
	case faceID_notEnrolled
	
	case notAvailable
	
	func isAvailable() -> Bool {
		return (self == .touchID_available) || (self == .faceID_available)
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

class AppSecurity {
	
	/// Singleton instance
	///
	public static let shared = AppSecurity()
	
	/// Changes always posted to the main thread.
	///
	public let enabledSecurityPublisher = CurrentValueSubject<EnabledSecurity, Never>(EnabledSecurity())
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// keychain and/or security file at any given time.
	///
	private let queue = DispatchQueue(label: "AppSecurity")
	
	private init() {/* must use shared instance */}
	
	// --------------------------------------------------------------------------------
	// MARK: Publisher
	// --------------------------------------------------------------------------------
	
	private func publishEnabledSecurity(_ value: EnabledSecurity) {
		
		let block = {
			self.enabledSecurityPublisher.send(value)
		}
		if Thread.isMainThread {
			block()
		} else {
			DispatchQueue.main.async { block() }
		}
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Private Utilities
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
			if hasCustomPin() {
				enabledSecurity.insert(.customPin)
			}
		}
		
		return enabledSecurity
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Public Utilities
	// --------------------------------------------------------------------------------
	
	public func generateEntropy() -> Data {
		
		let key = SymmetricKey(size: .bits128)
		
		return key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
	}
	
	/// Returns the device's current status concerning biometric support.
	///
	public func deviceBiometricSupport() -> BiometricSupport {
		
		let context = LAContext()
		
		var error : NSError?
		let result = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
		
		if context.biometryType == .touchID {
			if result && (error == nil) {
				return .touchID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .touchID_notEnrolled
				} else {
					return .touchID_notAvailable
				}
			}
		}
		if context.biometryType == .faceID {
			if result && (error == nil) {
				return .faceID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .faceID_notEnrolled
				} else {
					return .faceID_notAvailable
				}
			}
		}
		
		return .notAvailable
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Access Groups
	// --------------------------------------------------------------------------------
	
	/// Represents the keychain domain for this app.
	/// I.E. can NOT be accessed by our app extensions.
	///
	fileprivate func privateAccessGroup() -> String {
		
		return "XD77LN4376.co.acinq.phoenix"
	}
	
	/// Represents the keychain domain for our app group.
	/// I.E. can be accessed by our app extensions (e.g. notification-service-extension).
	///
	fileprivate func sharedAccessGroup() -> String {
		
		return SharedSecurity.shared.sharedAccessGroup()
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
				
				var mixins = [String: Any]()
				mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
				
				try keychain.storeKey( lockingKey,
				              account: keychain_accountName_keychain,
				          accessGroup: self.sharedAccessGroup(),
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
					account     : keychain_accountName_biometrics,
					accessGroup : self.privateAccessGroup()
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
			let account = keychain_accountName_softBiometrics
			let accessGroup = self.privateAccessGroup()
			
			if enabled {
				do {
					var mixins = [String: Any]()
					mixins[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
					
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
		let account = keychain_accountName_softBiometrics
		let accessGroup = self.privateAccessGroup()
		
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
			let account = keychain_accountName_passcodeFallback
			let accessGroup = self.privateAccessGroup()
			
			if enabled {
				do {
					var mixins = [String: Any]()
					mixins[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
					
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
		let account = keychain_accountName_passcodeFallback
		let accessGroup = privateAccessGroup()
		
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
	
	public func setCustomPin(
		pin        : String?,
		completion : @escaping (_ error: Error?) -> Void
	) -> Void {
		log.trace("setCustomPin(\(pin == nil ? "<nil>" : "<non-nil>"))")
		
		if let pin {
			precondition(pin.isValidPIN, "Attempting to set invalid PIN")
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
			let account = keychain_accountName_customPin
			let accessGroup = self.privateAccessGroup()
			
			if let pin {
				do {
					var mixins = [String: Any]()
					mixins[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
					
					try keychain.storeKey( pin,
									  account: account,
								 accessGroup: accessGroup,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: customPin): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				
				} catch {
					log.error("keychain.deleteKey(account: customPin): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	public func getCustomPin() -> String? {
		
		let keychain = GenericPasswordStore()
		let account = keychain_accountName_customPin
		let accessGroup = privateAccessGroup()
		
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
			log.error("keychain.readKey(account: customPin): error: \(error)")
		}
		
		return pin
	}
	
	public func hasCustomPin() -> Bool {
		
		return getCustomPin() != nil
	}
	
	public func setInvalidPin(
		invalidPin : InvalidPin?,
		completion : @escaping (_ error: Error?) -> Void
	) -> Void {
		if let invalidPin {
			log.trace("setInvalidPin(<count = \(invalidPin.count)>)")
		} else {
			log.trace("setInvalidPin(<nil>)")
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
			let account = keychain_accountName_invalidPin
			let accessGroup = self.privateAccessGroup()
			
			if let invalidPinData {
				do {
					var mixins = [String: Any]()
					mixins[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
					
					try keychain.storeKey( invalidPinData,
									  account: account,
								 accessGroup: accessGroup,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(account: invalidPin): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : accessGroup
					)
				
				} catch {
					log.error("keychain.deleteKey(account: invalidPin): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	public func getInvalidPin() -> InvalidPin? {
		
		let keychain = GenericPasswordStore()
		let account = keychain_accountName_invalidPin
		let accessGroup = privateAccessGroup()
		
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
					log.error("JSON.decode(InvalidPin): error: \(error)")
				}
			}
			
		} catch {
			log.error("keychain.readKey(account: invalidPin): error: \(error)")
		}
		
		return invalidPin
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
		let account = keychain_accountName_biometrics
	
		let fetchedKey: SymmetricKey?
		do {
			fetchedKey = try keychain.readKey(
				account     : account,
				accessGroup : self.privateAccessGroup(),
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
		
		do {
			try fm.removeItem(at: securityJsonUrl)
			log.error("Deleted file security.json")
		} catch {
			log.error("Unable to delete security.json: \(error)")
		}
			
		let keychain = GenericPasswordStore()
		
		do {
			try keychain.deleteKey(
				account: keychain_accountName_keychain,
				accessGroup: privateAccessGroup() // <- old location
			)
			log.info("Deleted keychain item: act(keychain) grp(private)")
		} catch {
			log.error("Unable to delete keychain item: act(keychain) grp(private): \(error)")
		}
		
		do {
			try keychain.deleteKey(
				account: keychain_accountName_keychain,
				accessGroup: sharedAccessGroup() // <- new location
			)
			log.info("Deleted keychain item: act(keychain) grp(shared)")
		} catch {
			log.error("Unable to delete keychain item: act(keychain) grp(shared): \(error)")
		}
		
		do {
			try keychain.deleteKey(
				account: keychain_accountName_biometrics,
				accessGroup: privateAccessGroup()
			)
			log.info("Deleted keychain item: act(biometrics) grp(private)")
		} catch {
			log.error("Unable to delete keychain item: act(biometrics) grp(private): \(error)")
		}
		
		do {
			try keychain.deleteKey(
				account: keychain_accountName_softBiometrics,
				accessGroup: privateAccessGroup()
			)
			log.error("Deleted keychain item: act(softBiometrics) grp(private)")
		} catch {
			log.error("Unable to delete keychain item: act(softBiometrics) grp(private): \(error)")
		}
		
		publishEnabledSecurity(EnabledSecurity())
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------------------------------------
	
	public func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) -> Void {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "40") {
			performMigration_toBuild40()
		}
		
		if targetBuild.isVersion(equalTo: "41") {
			performMigration_toBuild41(completionPublisher)
		}
	}
	
	private func performMigration_toBuild40() {
		log.trace("performMigration_toBuild40()")
		
		// Step 1 of 2:
		// Migrate "security.json" file to group container directory.
		
		let fm = FileManager.default
		
		if let appSupportDir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first,
		   let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix")
		{
			let oldFile = appSupportDir.appendingPathComponent("security.json", isDirectory: false)
			let newFile = groupDir.appendingPathComponent("security.json", isDirectory: false)
			
			if fm.fileExists(atPath: oldFile.path) {
				
				try? fm.moveItem(at: oldFile, to: newFile)
			}
		}
		
		// Step 2 of 2:
		// Migrate keychain entry to group container.
		
		migrateKeychainItemToSharedGroup()
	}
	
	private func performMigration_toBuild41(_ completionPublisher: CurrentValueSubject<Int, Never>) {
		log.trace("performMigration_toBuild41()")
		
		// There was a bug in versions prior to build 41,
		// where we didn't check the UIApplication's `isProtectedDataAvailable` flag.
		//
		// If that value happened to be false, and we attempted to read from the keychain,
		// we would have received an item-not-found error.
		//
		// If this occurred during performMigration_toBuild40(),
		// this would have resulted in the app failing to read the keychain item forever (in build 40).
		// So in build 41, we have to perform this migration again,
		// but this time not until `isProtectedDataAvailable` is true.
		
		if UIApplication.shared.isProtectedDataAvailable {
			migrateKeychainItemToSharedGroup()
			
		} else {
			
			completionPublisher.value += 1
			var cancellables = Set<AnyCancellable>()
			
			let nc = NotificationCenter.default
			nc.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification).sink { _ in
				
				// Apple doesn't specify which thread this notification is posted on.
				// Should be the main thread, but just in case, let's be safe.
				if Thread.isMainThread {
					self.migrateKeychainItemToSharedGroup()
					completionPublisher.value -= 1
				} else {
					DispatchQueue.main.async {
						self.migrateKeychainItemToSharedGroup()
						completionPublisher.value -= 1
					}
				}
				
				cancellables.removeAll()
			}.store(in: &cancellables)
		}
	}
	
	private func migrateKeychainItemToSharedGroup() {
		log.trace("migrateKeychainItemToSharedGroup()")
		
		let keychain = GenericPasswordStore()
		
		// Step 1 of 4:
		// - Read the OLD keychain item.
		// - If it exists, then we need to migrate it to the new location.
		var savedKey: SymmetricKey? = nil
		do {
			savedKey = try keychain.readKey(
				account     : keychain_accountName_keychain,
				accessGroup : privateAccessGroup() // <- old location
			)
		} catch {
			log.error("keychain.readKey(account: keychain, group: nil): error: \(error)")
		}
		
		if let lockingKey = savedKey {
			// The OLD keychain item exists, so we're going to migrate it.
			
			var migrated = false
			do {
				// Step 2 of 4:
				// - Delete the NEW keychain item.
				// - It shouldn't exist, but if it does it will cause an error on the next step.
				try keychain.deleteKey(
					account     : keychain_accountName_keychain,
					accessGroup : sharedAccessGroup() // <- new location
				)
			} catch {
				log.error("keychain.deleteKey(account: keychain, group: shared): error: \(error)")
			}
			do {
				var mixins = [String: Any]()
				mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
				
				// Step 3 of 4:
				// - Copy the OLD keychain item to the NEW location.
				// - If this step fails, an exception is thrown, and we do NOT advance to step 4.
				try keychain.storeKey( lockingKey,
				              account: keychain_accountName_keychain,
				          accessGroup: sharedAccessGroup(), // <- new location
				               mixins: mixins
				)
				migrated = true
				
				// Step 4 of 4:
				// - Finally, delete the OLD keychain item.
				// - This prevents any duplicate migration attempts in the future.
				try keychain.deleteKey(
					account     : keychain_accountName_keychain,
					accessGroup : privateAccessGroup() // <- old location
				)
				
			} catch {
				if !migrated {
					log.error("keychain.storeKey(account: keychain, group: shared): error: \(error)")
				} else {
					log.error("keychain.deleteKey(account: keychain, group: private): error: \(error)")
				}
			}
		}
	}
}

// MARK: - Utilities

fileprivate func genericError(_ code: Int, _ description: String? = nil) -> NSError {
	
	var userInfo = [String: String]()
	if let description = description {
		userInfo[NSLocalizedDescriptionKey] = description
	}
		
	return NSError(domain: "AppSecurity", code: code, userInfo: userInfo)
}
