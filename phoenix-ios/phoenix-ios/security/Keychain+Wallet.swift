import Foundation
import Combine
import CryptoKit
import LocalAuthentication
import SwiftUI

fileprivate let filename = "Keychain+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = KeychainKey
fileprivate typealias KeyDeprecated = KeychainKeyDeprecated

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

class Keychain_Wallet {
	
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
	private let queue = DispatchQueue(label: "Keychain")
	
	private var _cached_softBiometrics: Optional<Bool> = nil
	private var _cached_passcodeFallback: Optional<Bool> = nil
	private var _cached_lockPin: Optional<String?> = nil
	private var _cached_spendingPin: Optional<String?> = nil
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(id: String) {
		self.id = id
	#if DEBUG
		self.isDefault = (id == KEYCHAIN_DEFAULT_ID)
	#endif
	}
	
	// --------------------------------------------------
	// MARK: Publisher
	// --------------------------------------------------
	
	private func publishEnabledSecurity(_ value: EnabledSecurity) {
		
		runOnMainThread {
			self.enabledSecurityPublisher.send(value)
		}
	}
	
	private func updateEnabledSecurity() {
		
		queue.async {
			var enabledSecurity = EnabledSecurity.none
			var keyInfo: KeyInfo_ChaChaPoly? = nil
			
			let securityFile = try? SecurityFileManager.shared.readFromDisk().get()
			if let v0 = securityFile as? SecurityFile.V0 {
				
				if v0.biometrics != nil {
					enabledSecurity.insert(.biometrics)
					enabledSecurity.insert(.advancedSecurity)
					
				} else {
					keyInfo = v0.keychain
				}
				
			} else if let v1 = securityFile as? SecurityFile.V1 {
				keyInfo = v1.wallets[self.id]?.keychain
			}
			
			if keyInfo != nil {
				if self._getSoftBiometricsEnabled() {
					enabledSecurity.insert(.biometrics)
					
					if self._getPasscodeFallbackEnabled() {
						enabledSecurity.insert(.passcodeFallback)
					}
				}
				if self._getPin(.lockPin) != nil {
					enabledSecurity.insert(.lockPin)
				}
				if self.getPin(.spendingPin) != nil {
					enabledSecurity.insert(.spendingPin)
				}
			}
			
			self.publishEnabledSecurity(enabledSecurity)
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
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
	
	// --------------------------------------------------
	// MARK: Keychain
	// --------------------------------------------------
	
	/// Attempts to extract the mnemonics using the keychain.
	/// If the user hasn't enabled any additional security options, this will succeed.
	/// Otherwise it will fail, and the completion closure will specify the additional security in place.
	///
	public func tryUnlockWithKeychain(
		completion: @escaping (
			_ recoveryPhrase: RecoveryPhrase?,
			_ error: UnlockError?
		) -> Void
	) {
		log.trace("tryUnlockWithKeychain()")
		
		let succeed = {(recoveryPhrase: RecoveryPhrase?) -> Void in
			self.updateEnabledSecurity()
			DispatchQueue.main.async {
				completion(recoveryPhrase, nil)
			}
		}
		
		let fail = {(error: UnlockError) -> Void in
			self.updateEnabledSecurity()
			DispatchQueue.main.async {
				completion(nil, error)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			// Fetch the "security.json" file.
			// If the file doesn't exist, an empty SecurityFile is returned.
			let diskResult = SecurityFileManager.shared.readFromDisk()
			
			switch diskResult {
			case .failure(let reason):
				
				switch reason {
				case .fileNotFound:
					return succeed(nil)
					
				case .errorReadingFile: fallthrough
				case .errorDecodingFile:
					return fail(.readSecurityFileError(underlying: reason))
				}
				
			case .success(let securityFile):
				
				var keyInfo: KeyInfo_ChaChaPoly? = nil
				if let v0 = securityFile as? SecurityFile.V0 {
					keyInfo = v0.keychain
					
				} else if let v1 = securityFile as? SecurityFile.V1 {
					keyInfo = v1.wallets[self.id]?.keychain
				}
				
				guard let keyInfo else {
					return succeed(nil)
				}
				
				let keychainResult = SharedSecurity.shared.readKeychainEntry(KEYCHAIN_DEFAULT_ID, keyInfo)
				switch keychainResult {
				case .failure(let reason):
					return fail(.readKeychainError(underlying: reason))
					
				case .success(let cleartextData):
					
					let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
					switch decodeResult {
					case .failure(let reason):
						return fail(.readRecoveryPhraseError(underlying: reason))
						
					case .success(let recoveryPhrase):
						return succeed(recoveryPhrase)
						
					} // </switch decodeResult>
				} // </switch keychainResult>
			} // </switch diskResult>
		} // </queue.async>
	}
	
	public func setLockingKey(
		_ lockingKey : SymmetricKey
	) -> Result<Void, Error> {
		
		let keychain = GenericPasswordStore()
		let key = Key.lockingKey
		
		do {
			// Access control considerations:
			//
			// This is only for fetching the databaseKey,
			// which we only need to do once when launching the app.
			// So we shouldn't need access to the keychain item when the device is locked.
			
			let mixins = self.commonMixins()
			try keychain.storeKey( lockingKey,
			              account: key.account(self.id),
			          accessGroup: key.accessGroup.value,
			               mixins: mixins)
				
			} catch {
				log.error("keychain.storeKey(acct: \(key.debugName)): error: \(error)")
				return .failure(error)
			}
			
			updateEnabledSecurity()
			return .success
	}
	
	// --------------------------------------------------
	// MARK: Soft Biometrics
	// --------------------------------------------------
	
	public func setSoftBiometrics(
		enabled    : Bool,
		completion : @escaping (_ error: Error?) -> Void
	) {
		log.trace("setSoftBiometrics(\(enabled))")
		
		let succeed = {
			self._cached_softBiometrics = enabled
			self.updateEnabledSecurity()
			DispatchQueue.main.async {
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
			let key = Key.softBiometrics
			
			if enabled {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( "true",
					              account: key.account(self.id),
					          accessGroup: key.accessGroup.value,
					               mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value)
					
				} catch {
					log.error("keychain.deleteKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	public func getSoftBiometricsEnabled() -> Bool {
		
		return queue.sync {
			_getSoftBiometricsEnabled()
		}
	}
	
	private func _getSoftBiometricsEnabled() -> Bool {
		
		if let cachedValue = _cached_softBiometrics {
			return cachedValue
		}
		
		let keychain = GenericPasswordStore()
		let key = Key.softBiometrics
		
		var enabled = false
		do {
			let value: String? = try keychain.readKey(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.readKey(acct: \(key.debugName)): error: \(error)")
		}
		
		_cached_softBiometrics = enabled
		return enabled
	}
	
	// --------------------------------------------------
	// MARK: Passcode Fallback
	// --------------------------------------------------
	
	public func setPasscodeFallback(
		enabled    : Bool,
		completion : @escaping (_ error: Error?) -> Void
	) {
		log.trace("setPasscodeFallback(\(enabled))")
		
		let succeed = {
			self._cached_passcodeFallback = enabled
			self.updateEnabledSecurity()
			DispatchQueue.main.async {
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
			let key = Key.passcodeFallback
			
			if enabled {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( "true",
					              account: key.account(self.id),
					          accessGroup: key.accessGroup.value,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.deleteKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	public func getPasscodeFallbackEnabled() -> Bool {
		
		return queue.sync {
			_getPasscodeFallbackEnabled()
		}
	}
	
	private func _getPasscodeFallbackEnabled() -> Bool {
		
		if let cachedValue = _cached_passcodeFallback {
			return cachedValue
		}
		
		let keychain = GenericPasswordStore()
		let key = Key.passcodeFallback
		
		var enabled = false
		do {
			let value: String? = try keychain.readKey(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.readKey(acct: \(key.debugName)): error: \(error)")
		}
		
		_cached_passcodeFallback = enabled
		return enabled
	}
	
	// --------------------------------------------------
	// MARK: PIN
	// --------------------------------------------------
	
	func setPin(
		_ pin      : String?,
		_ type     : PinType,
		completion : @escaping (_ error: Error?) -> Void
	) {
		log.trace("setPin(\(pin == nil ? "<nil>" : "<non-nil>"), \(type)")
		
		if let pin {
			precondition(pin.isValidPIN, "Attempting to set invalid PIN for type \(type)")
		}
		
		let succeed = {
			switch type {
				case .lockPin     : self._cached_lockPin = pin
				case .spendingPin : self._cached_spendingPin = pin
			}
			self.updateEnabledSecurity()
			DispatchQueue.main.async {
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
			let key = type.keyPin
			
			if let pin {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( pin,
					              account: key.account(self.id),
					          accessGroup: key.accessGroup.value,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.deleteKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	func getPin(_ type: PinType) -> String? {
		
		return queue.sync {
			_getPin(type)
		}
	}
	
	private func _getPin(_ type: PinType) -> String? {
		
		switch type {
		case .lockPin:
			if let cachedValue = _cached_lockPin {
				return cachedValue
			} else {
				log.warning("_getPin(.lockPin): no cached value")
			}
		case .spendingPin:
			if let cachedValue = _cached_spendingPin {
				return cachedValue
			} else {
				log.warning("_getPin(.spendingPin): no cached value")
			}
		}
		
		let keychain = GenericPasswordStore()
		let key = type.keyPin
		
		var pin: String? = nil
		do {
			let value: String? = try keychain.readKey(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			
			if let value, value.isValidPIN {
				pin = value
			}
			
		} catch {
			log.error("keychain.readKey(acct: \(key.debugName)): error: \(error)")
		}
		
		switch type {
			case .lockPin     : _cached_lockPin = pin
			case .spendingPin : _cached_spendingPin = pin
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
			let key = type.keyInvalid
			
			if let invalidPinData {
				do {
					let mixins = self.commonMixins()
					try keychain.storeKey( invalidPinData,
					              account: key.account(self.id),
					          accessGroup: key.accessGroup.value,
										mixins: mixins)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.deleteKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	func getInvalidPin(_ type: PinType) -> InvalidPin? {
		
		let keychain = GenericPasswordStore()
		let key = type.keyInvalid
		
		var invalidPin: InvalidPin? = nil
		do {
			let value: Data? = try keychain.readKey(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			
			if let value {
				do {
					invalidPin = try JSONDecoder().decode(InvalidPin.self, from: value)
				} catch {
					log.error("JSON.decode(acct: \(key.debugName)): error: \(error)")
				}
			}
			
		} catch {
			log.error("keychain.readKey(acct: \(key.debugName)): error: \(error)")
		}
		
		return invalidPin
	}
	
	// --------------------------------------------------
	// MARK: Lock PIN
	// --------------------------------------------------
	
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
	
	// --------------------------------------------------
	// MARK: Spending PIN
	// --------------------------------------------------
	
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
	
	// --------------------------------------------------
	// MARK: BIP353 Address
	// --------------------------------------------------
	
	public func setBip353Address(
		_ address: String
	) -> Result<Void, Error> {
		
		let keychain = GenericPasswordStore()
		let key = Key.bip353Address
		
		do {
			let mixins = self.commonMixins()
			try keychain.storeKey( address,
			              account: key.account(self.id),
			          accessGroup: key.accessGroup.value,
								mixins: mixins)
			
			return .success
		} catch {
			log.error("keychain.storeKey(account: bip353Address): error: \(error)")
			return .failure(error)
		}
	}
		
	public func getBip353Address() -> String? {
		
		let keychain = GenericPasswordStore()
		let key = Key.bip353Address
		
		var addr: String? = nil
		do {
			let value: String? = try keychain.readKey(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			
			if let value {
				addr = value
			}
			
		} catch {
			log.error("keychain.readKey(acct: \(key.debugName)): error: \(error)")
		}
		
		return addr
	}
	
	// --------------------------------------------------
	// MARK: Biometrics
	// --------------------------------------------------
	
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
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(Result.failure(error))
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			let securityFile = try? SecurityFileManager.shared.readFromDisk().get()
			
			if let v0 = securityFile as? SecurityFile.V0 {
				if v0.biometrics != nil {
					self.tryUnlockWithHardBiometrics(v0, prompt, completion)
				} else {
					if let keyInfo = v0.keychain {
						self.tryUnlockWithSoftBiometrics(keyInfo, prompt, completion)
					} else {
						fail(genericError(404, "keyInfo not found"))
					}
				}
				
			} else if let v1 = securityFile as? SecurityFile.V1 {
				if let keyInfo = v1.wallets[self.id]?.keychain {
					self.tryUnlockWithSoftBiometrics(keyInfo, prompt, completion)
				} else {
					fail(genericError(404, "keyInfo not found"))
				}
				
			} else {
				fail(genericError(404, "securityFile not found"))
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
		_ securityFile : SecurityFile.V0,
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
		
		// The "advanced security" technique was removed in v1.4.
		// We introduced the notification-service-extension,
		// with the ability to receive payments when app is running in the background.
		//
		let disableAdvancedSecurityAndSucceed = {(_ recoveryPhrase: RecoveryPhrase) in
			
			// TODO
			fail(genericError(501, "Not implemented"))
		//	self.addKeychainEntry(recoveryPhrase: recoveryPhrase) { _ in
		//		succeed(recoveryPhrase)
		//	}
		}
		
		// The security.json file tells us which security options have been enabled.
		// This function should only be called if the `biometrics` entry is present.
		guard
			let keyInfo_biometrics = securityFile.biometrics,
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
		let account     = KeyDeprecated.lockingKey_biometrics.rawValue
		let accessGroup = AccessGroup.appOnly.value
	
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
		_ keyInfo    : KeyInfo_ChaChaPoly,
		_ prompt     : String? = nil,
		_ completion : @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
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
		
		let keychainResult = SharedSecurity.shared.readKeychainEntry(KEYCHAIN_DEFAULT_ID, keyInfo)
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
		let securityJsonUrl = SharedSecurity.shared.securityJsonUrl_V0
		
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
					account     : key.account(self.id),
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
		
		Keychain.didResetWallet(id)
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
		
	return NSError(domain: "Keychain", code: code, userInfo: userInfo)
}
