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

class Keychain_Wallet {
	
	private let id: String
#if DEBUG
	private let isDefault: Bool
#endif
	
	/// Serial queue ensures that only one operation is
	/// reading/modifying the keychain at any given time.
	///
	private let queue = DispatchQueue(label: "Keychain_Wallet")
	
	private var _cached_softBiometrics: Optional<Bool> = nil
	private var _cached_passcodeFallback: Optional<Bool> = nil
	private var _cached_lockPin: Optional<String?> = nil
	private var _cached_spendingPin: Optional<String?> = nil
	
	private var _cached_enabledSecurity: Optional<EnabledSecurity> = nil
	
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
	
	var enabledSecurity: EnabledSecurity {
		
		return queue.sync {
			
			if let cached = _cached_enabledSecurity {
				return cached
			}
			
			let calculated = calculateEnabledSecurity()
			_cached_enabledSecurity = calculated
			
			return calculated
		}
	}
	
	private func calculateEnabledSecurity() -> EnabledSecurity {
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		var enabledSecurity = EnabledSecurity.none
		var sealedBox: SealedBox_ChaChaPoly? = nil
		
		if let version = SecurityFileManager.shared.currentSecurityFile() {
			switch version {
			case .v0(let v0):
				if v0.biometrics != nil {
					enabledSecurity.insert(.biometrics)
					enabledSecurity.insert(.advancedSecurity)
					
				} else {
					sealedBox = v0.keychain
				}
				
			case .v1(let v1):
				sealedBox = v1.wallets[self.id]?.keychain
			}
		}
		
		if sealedBox != nil {
			if self._getSoftBiometricsEnabled() {
				enabledSecurity.insert(.biometrics)
				
				if self._getPasscodeFallbackEnabled() {
					enabledSecurity.insert(.passcodeFallback)
				}
			}
			if self._getPin(.lockPin) != nil {
				enabledSecurity.insert(.lockPin)
			}
			if self._getPin(.spendingPin) != nil {
				enabledSecurity.insert(.spendingPin)
			}
		}
		
		return enabledSecurity
	}
	
	private func updateEnabledSecurity() {
		log.trace(#function)
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		let calculated = calculateEnabledSecurity()
		_cached_enabledSecurity = calculated
	}
	
	// --------------------------------------------------
	// MARK: Unlock
	// --------------------------------------------------
	
	public func firstUnlockWithKeychain(
		completion: @escaping (
			_ recoveryPhrase: RecoveryPhrase?,
			_ enabledSecurity: EnabledSecurity,
			_ error: UnlockError?
		) -> Void
	) {
		log.trace(#function)
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			let result = self._unlockWithKeychain()
			
			let recoveryPhrase: RecoveryPhrase?
			let error: UnlockError?
			switch result {
			case .success(let success):
				recoveryPhrase = success
				error = nil
				
			case .failure(let failure):
				recoveryPhrase = nil
				error = failure
			}
			
			let enabledSecurity = self.calculateEnabledSecurity()
			self._cached_enabledSecurity = enabledSecurity
			DispatchQueue.main.async {
				completion(recoveryPhrase, enabledSecurity, error)
			}
		}
	}
	
	/// Attempts to extract the mnemonics using the keychain.
	/// If the user hasn't enabled any additional security options, this will succeed.
	/// Otherwise it will fail, and the completion closure will specify the additional security in place.
	///
	public func unlockWithKeychain(
		completion: @escaping (Result<RecoveryPhrase?, UnlockError>) -> Void
	) {
		log.trace(#function)
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			let result = self._unlockWithKeychain()
			DispatchQueue.main.async {
				completion(result)
			}
		}
	}
	
	/// Attempts to extract the mnemonics using the keychain.
	///
	/// - If the user has advanced security enabled (deprecated feature), will return a nil RecoveryPhrase
	/// - Otherwise it should return the wallet's RecoveryPhrase
	/// - Unless an unexpected UnlockError occurs
	///
	private func _unlockWithKeychain() -> Result<RecoveryPhrase?, UnlockError> {
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
			return .failure(.readSecurityFileError(underlying: .fileNotFound))
		}
		
		var sealedBox: SealedBox_ChaChaPoly?
		switch securityFile {
		case .v0(let v0):
			sealedBox = v0.keychain
		case .v1(let v1):
			sealedBox = v1.wallets[self.id]?.keychain
		}
		
		guard let sealedBox else {
			return .success(nil)
		}
			
		let keychainResult = SharedSecurity.shared.readKeychainEntry(self.id, sealedBox)
		switch keychainResult {
		case .failure(let reason):
			return .failure(.readKeychainError(underlying: reason))
			
		case .success(let cleartextData):
				
			let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
			switch decodeResult {
			case .failure(let reason):
				return .failure(.readRecoveryPhraseError(underlying: reason))
				
			case .success(let recoveryPhrase):
				return .success(recoveryPhrase)
				
			} // </switch decodeResult>
		} // </switch keychainResult>
	}
	
	// --------------------------------------------------
	// MARK: Locking Key
	// --------------------------------------------------
	
	public func setLockingKey(
		_ lockingKey : SymmetricKey
	) -> Result<Void, Error> {
		log.trace(#function)
		
		let key = Key.lockingKey
		do {
			// Access control considerations:
			//
			// This is only for fetching the databaseKey,
			// which we only need to do once when launching the app.
			// So we shouldn't need access to the keychain item when the device is locked.
			let mixins = Keychain.commonMixins()
			
			try SystemKeychain.storeItem(
				value       : lockingKey,
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value,
				mixins      : mixins
			)
				
		} catch {
			log.error("keychain.store(acct: \(key.debugName)): error: \(error)")
			return .failure(error)
		}
		
		queue.async {
			self.updateEnabledSecurity()
		}
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
			
			let key = Key.softBiometrics
			let mixins = Keychain.commonMixins()
			
			if enabled {
				do {
					try SystemKeychain.storeItem(
						value       : "true",
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value,
						mixins      : mixins
					)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try SystemKeychain.deleteItem(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value)
					
				} catch {
					log.error("keychain.delete(acct: \(key.debugName)): error: \(error)")
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
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		if let cachedValue = _cached_softBiometrics {
			return cachedValue
		}
		
		let key = Key.softBiometrics
		
		var enabled = false
		do {
			let value: String? = try SystemKeychain.readItem(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
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
			
			let key = Key.passcodeFallback
			let mixins = Keychain.commonMixins()
			
			if enabled {
				do {
					try SystemKeychain.storeItem(
						value       : "true",
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value,
						mixins      : mixins
					)
					
				} catch {
					log.error("keychain.storeKey(acct: \(key.debugName): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try SystemKeychain.deleteItem(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.delete(acct: \(key.debugName)): error: \(error)")
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
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		if let cachedValue = _cached_passcodeFallback {
			return cachedValue
		}
		
		let key = Key.passcodeFallback
		
		var enabled = false
		do {
			let value: String? = try SystemKeychain.readItem(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
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
			
			let key = type.keyPin
			let mixins = Keychain.commonMixins()
			
			if let pin {
				do {
					try SystemKeychain.storeItem(
						value       : pin,
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value,
						mixins      : mixins
					)
					
				} catch {
					log.error("keychain.store(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try SystemKeychain.deleteItem(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.delete(acct: \(key.debugName)): error: \(error)")
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
		
		#if DEBUG
		dispatchPrecondition(condition: .onQueue(queue))
		#endif
		
		switch type {
		case .lockPin:
			if let cachedValue = _cached_lockPin {
				return cachedValue
			}
		case .spendingPin:
			if let cachedValue = _cached_spendingPin {
				return cachedValue
			}
		}
		
		let key = type.keyPin
		
		var pin: String? = nil
		do {
			let value: String? = try SystemKeychain.readItem(
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value
			)
			
			if let value, value.isValidPIN {
				pin = value
			}
			
		} catch {
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
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
			
			let key = type.keyInvalidPin
			let mixins = Keychain.commonMixins()
			
			if let invalidPinData {
				do {
					try SystemKeychain.storeItem(
						value       : invalidPinData,
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value,
						mixins      : mixins
					)
					
				} catch {
					log.error("keychain.store(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
				
			} else {
				do {
					try SystemKeychain.deleteItem(
						account     : key.account(self.id),
						accessGroup : key.accessGroup.value
					)
				
				} catch {
					log.error("keychain.delete(acct: \(key.debugName)): error: \(error)")
					return fail(error)
				}
			}
			
			succeed()
		} // </queue.async>
	}
	
	func getInvalidPin(_ type: PinType) -> InvalidPin? {
		
		let key = type.keyInvalidPin
		
		var invalidPin: InvalidPin? = nil
		do {
			let value: Data? = try SystemKeychain.readItem(
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
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
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
		log.trace(#function)
		
		let key = Key.bip353Address
		let mixins = Keychain.commonMixins()
		
		do {
			try SystemKeychain.storeItem(
				value       : address,
				account     : key.account(self.id),
				accessGroup : key.accessGroup.value,
				mixins      : mixins
			)
			
			return .success
		} catch {
			log.error("keychain.store(account: bip353Address): error: \(error)")
			return .failure(error)
		}
	}
		
	public func getBip353Address() -> String? {
		
		let key = Key.bip353Address
		
		var addr: String? = nil
		do {
			let value: String? = try SystemKeychain.readItem(
				account     : key.account(id),
				accessGroup : key.accessGroup.value
			)
			
			if let value {
				addr = value
			}
			
		} catch {
			log.error("keychain.read(acct: \(key.debugName)): error: \(error)")
		}
		
		return addr
	}
	
	// --------------------------------------------------
	// MARK: Biometrics
	// --------------------------------------------------
	
	/// Attempts to extract the seed using biometrics (e.g. touchID, faceID)
	///
	public func unlockWithBiometrics(
		prompt: String? = nil,
		completion: @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		log.trace(#function)
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(Result.failure(error))
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
				return fail(genericError(404, "securityFile not found"))
			}
			
			switch securityFile {
			case .v0(let v0):
				if v0.biometrics != nil {
					self.unlockWithHardBiometrics(v0, prompt, completion)
				} else {
					if let keyInfo = v0.keychain {
						self.unlockWithSoftBiometrics(keyInfo, prompt, completion)
					} else {
						fail(genericError(404, "keyInfo not found"))
					}
				}
				
			case .v1(let v1):
				if let keyInfo = v1.wallets[self.id]?.keychain {
					self.unlockWithSoftBiometrics(keyInfo, prompt, completion)
				} else {
					fail(genericError(404, "keyInfo not found"))
				}
			} // </switch securityFile>
		} // </queue.async>
	}
	
	/// This function is called when "advanced security" is detected.
	///
	/// This type of security was removed in v1.4.
	/// It was replaced with the notification-service-extension,
	/// with ability to receive payments when app is running in the background.
	///
	private func unlockWithHardBiometrics(
		_ securityFile : SecurityFile.V0,
		_ prompt       : String? = nil,
		_ completion   : @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		log.trace(#function)
		
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
			
			let chain = Biz.business.chain
			AppSecurity.shared.addWallet(chain: chain, recoveryPhrase: recoveryPhrase) { result in
				switch result {
				case .failure(let reason):
					fail(reason)
					
				case .success():
					// Remove deprecated key from keychain
					let _ = try? SystemKeychain.deleteItem(
						account     : KeyDeprecated.lockingKey_biometrics.rawValue,
						accessGroup : AccessGroup.appOnly.value
					)
					succeed(recoveryPhrase)
				}
			}
		}
		
		// The security.json file tells us which security options have been enabled.
		// This function should only be called if the `biometrics` entry is present.
		guard
			let keyInfo_biometrics = securityFile.biometrics,
			let rawSealedBox = try? keyInfo_biometrics.toRaw()
		else {
			return fail(genericError(400, "SecurityFile.biometrics entry is corrupt"))
		}
		
		let context = LAContext()
		context.localizedReason = prompt ?? self.biometricsPrompt()
		context.localizedFallbackTitle = "" // passcode fallback disbaled
		
		let account     = KeyDeprecated.lockingKey_biometrics.rawValue
		let accessGroup = AccessGroup.appOnly.value
	
		var fetchedKey: SymmetricKey? = nil
		do {
			fetchedKey = try SystemKeychain.readItem(
				account     : account,
				accessGroup : accessGroup,
				context     : context
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
			cleartextData = try ChaChaPoly.open(rawSealedBox, using: lockingKey)
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
		
		self.biometricsChallenge {(success, error) in
		
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
	
	private func unlockWithSoftBiometrics(
		_ sealedBox  : SealedBox_ChaChaPoly,
		_ prompt     : String? = nil,
		_ completion : @escaping (_ result: Result<RecoveryPhrase, Error>) -> Void
	) {
		log.trace(#function)
		
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
		
		let keychainResult = SharedSecurity.shared.readKeychainEntry(KEYCHAIN_DEFAULT_ID, sealedBox)
		switch keychainResult {
		case .failure(let error):
			fail(error)
		
		case .success(let cleartextData):
			
			let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
			switch decodeResult {
			case .failure(let error):
				fail(error)
				
			case .success(let recoveryPhrase):
				self.biometricsChallenge { (success, error) in
					if success {
						succeed(recoveryPhrase)
					} else {
						fail(error ?? genericError(401, "Biometrics prompt failed / cancelled"))
					}
				}
			} // </switch decodeResult>
		} // </switch keychainResult>
	}
	
	private func biometricsChallenge(
		_ completion: @escaping (Bool, Error?) -> Void
	) -> Void {
		log.trace(#function)
		
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
		       localizedReason: self.biometricsPrompt(),
		                 reply: completion)
	}
	
	private func biometricsPrompt() -> String {
		
		return String(
			localized : "App is locked",
			comment   : "Biometrics prompt to unlock the Phoenix app"
		)
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	public func resetWallet() {
		log.trace(#function)
		
		for key in Key.allCases {
			do {
				let result = try SystemKeychain.deleteItem(
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
