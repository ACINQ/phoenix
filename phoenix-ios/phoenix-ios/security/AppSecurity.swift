import Foundation
import Combine
import CommonCrypto
import CryptoKit
import LocalAuthentication
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppSecurity"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

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
	
	init(_ readSecurityFileError: ReadSecurityFileError?, _ readKeychainError: ReadKeychainError?) {
		self.readSecurityFileError = readSecurityFileError
		self.readKeychainError = readKeychainError
	}
}

class AppSecurity {
	
	/// Singleton instance
	///
	public static let shared = AppSecurity()
	
	/// Changes always posted to the main thread.
	///
	public let enabledSecurity = CurrentValueSubject<EnabledSecurity, Never>(EnabledSecurity())
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// keychain and/or security file at any given time.
	///
	private let queue = DispatchQueue(label: "AppSecurity")
	
	private init() {/* must use shared instance */}
	
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
			log.error("Error excluding \(url.lastPathComponent) from backup \(String(describing: error))")
		}
	}
	
	private func validateParameter(mnemonics: [String]) -> Data {
		
		precondition(mnemonics.count == 12, "Invalid parameter: mnemonics.count")
		
		let space = " "
		precondition(mnemonics.allSatisfy { !$0.contains(space) },
		  "Invalid parameter: mnemonics.word")
		
		let mnemonicsData = mnemonics.joined(separator: space).data(using: .utf8)
		
		precondition(mnemonicsData != nil,
		  "Invalid parameter: mnemonics.work contains non-utf8 characters")
		
		return mnemonicsData!
	}
	
	private func calculateEnabledSecurity(_ securityFile: SecurityFile) -> EnabledSecurity {
		
		var enabledSecurity = EnabledSecurity.none
		
		if securityFile.biometrics != nil {
			enabledSecurity.insert(.biometrics)
			enabledSecurity.insert(.advancedSecurity)
		} else if (securityFile.keychain != nil) && self.getSoftBiometricsEnabled() {
			enabledSecurity.insert(.biometrics)
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
			_ mnemonics: [String]?,
			_ configuration: EnabledSecurity,
			_ error: UnlockError?
		) -> Void
	) {
		
		let finish = {(mnemonics: [String]?, configuration: EnabledSecurity) -> Void in
			
			DispatchQueue.main.async {
				self.enabledSecurity.send(configuration)
				completion(mnemonics, configuration, nil)
			}
		}
		
		let dangerZone1 = {(error: ReadSecurityFileError, configuration: EnabledSecurity) -> Void in
			
			let danger = UnlockError(error, nil)
			
			DispatchQueue.main.async {
				self.enabledSecurity.send(configuration)
				completion(nil, configuration, danger)
			}
		}
		
		let dangerZone2 = {(error: ReadKeychainError, configuration: EnabledSecurity) -> Void in
			
			let danger = UnlockError(nil, error)
			
			DispatchQueue.main.async {
				self.enabledSecurity.send(configuration)
				completion(nil, configuration, danger)
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
							return dangerZone1(reason, configuration)
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
								case .errorOpeningBox: fallthrough
								case .invalidMnemonics:
									return dangerZone2(reason, configuration)
							}
							
						case .success(let mnemonics):
							return finish(mnemonics, configuration)
					}
			}
		}
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
		mnemonics  : [String],
		completion : @escaping (_ error: Error?) -> Void
	) {
		let mnemonicsData = validateParameter(mnemonics: mnemonics)
		
		let succeed = {(securityFile: SecurityFile) -> Void in
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.enabledSecurity.send(newEnabledSecurity)
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
			
			let lockingKey = SymmetricKey(size: .bits256)
			
			let sealedBox: ChaChaPoly.SealedBox
			do {
				sealedBox = try ChaChaPoly.seal(mnemonicsData, using: lockingKey)
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
			//    The given databaseKey corresponds to the existing database file.
			//    There are existing entries in the keychain.
			//    The security.json file exists, and contains entries.
			//
			// 3. Something bad happened during app launch.
			//    We discovered a corrupt database, a corrupt security.json,
			//    or necessary keychain entries have gone missing.
			//    When this occurs, the system invokes the various `backup` functions.
			//    This creates a copy of the database, security.json file & keychain entries.
			//    Afterwards this function is called.
			//    And we can treat this scenario as the equivalent of a first app launch.
			//
			// So situation #2 is the dangerous one.
			// Consider what happens if:
			//
			// - we delete the touchID entry from the database
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
				try keychain.deleteKey(
					account     : keychain_accountName_keychain,
					accessGroup : self.sharedAccessGroup()
				)
			} catch {/* ignored */}
			do {
				// Access control considerations:
				//
				// This is only for fetching the databaseKey,
				// which we only need to do once when launching the app.
				// So we shouldn't need access to the keychain item when the device is locked.
				
				var query = [String: Any]()
				query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
				
				try keychain.storeKey( lockingKey,
				              account: keychain_accountName_keychain,
				          accessGroup: self.sharedAccessGroup(),
				               mixins: query)
			} catch {
				log.error("keychain.storeKey(account: keychain): error: \(String(describing: error))")
				return fail(error)
			}
			
			do {
				try self.writeToDisk(securityFile: securityFile)
			} catch {
				log.error("writeToDisk(securityFile): error: \(String(describing: error))")
				return fail(error)
			}
			
			// Now we can safely delete the biometric entry in the database (if it exists)
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
		
		let succeed = {
			let securityFile = self.readFromDisk()
			DispatchQueue.main.async {
				let newEnabledSecurity = self.calculateEnabledSecurity(securityFile)
				self.enabledSecurity.send(newEnabledSecurity)
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
			
			if enabled {
				do {
					var query = [String: Any]()
					query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
					
					try keychain.storeKey( "true",
					              account: account,
					          accessGroup: self.privateAccessGroup(),
					               mixins: query)
					
				} catch {
					log.error("keychain.storeKey(account: softBiometrics): error: \(String(describing: error))")
					return fail(error)
				}
				
			} else {
				do {
					try keychain.deleteKey(
						account     : account,
						accessGroup : self.privateAccessGroup()
					)
				
				} catch {
					log.error("keychain.deleteKey(account: softBiometrics): error: \(String(describing: error))")
					return fail(error)
				}
			}
			
			succeed()
		
		} // </queue.async>
	}
	
	public func getSoftBiometricsEnabled() -> Bool {
		
		let keychain = GenericPasswordStore()
		let account = keychain_accountName_softBiometrics
		
		var enabled = false
		do {
			let value: String? = try keychain.readKey(
				account     : account,
				accessGroup : privateAccessGroup()
			)
			enabled = value != nil
			
		} catch {
			log.error("keychain.readKey(account: softBiometrics): error: \(String(describing: error))")
		}
		
		return enabled
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
		completion: @escaping (_ result: Result<[String], Error>) -> Void
	) {
		let succeed = {(_ mnemonics: [String]) in
			DispatchQueue.main.async {
				completion(Result.success(mnemonics))
			}
		}
		
		let fail = {(_ error: Error) -> Void in
			DispatchQueue.main.async {
				completion(Result.failure(error))
			}
		}
		
		// "Advanced security" technique removed in v1.4.
		// Replaced with notification-service-extension,
		// with ability to receive payments when app is running in the background.
		// 
		let disableAdvancedSecurityAndSucceed = {(_ mnemonics: [String]) in
			
			self.addKeychainEntry(mnemonics: mnemonics) { _ in
				succeed(mnemonics)
			}
		}
		
		let trySoftBiometrics = {(_ securityFile: SecurityFile) -> Void in
			
			let result = SharedSecurity.shared.readKeychainEntry(securityFile)
			switch result {
			case .failure(let error):
				fail(error)
			
			case .success(let mnemonics):
				self.tryGenericBiometrics { (success, error) in
					if success {
						succeed(mnemonics)
					} else {
						fail(error ?? genericError(401, "Biometrics prompt failed / cancelled"))
					}
				}
			}
		}
		
		// Disk IO ahead - get off the main thread.
		// Also - go thru the serial queue for proper thread safety.
		queue.async {
			
			// Fetch the "security.json" file.
			// If the file doesn't exist, an empty SecurityFile is returned.
			let securityFile = self.readFromDisk()
			
			// The file tells us which security options have been enabled.
			// If there isn't a keychain entry, then we cannot unlock the seed.
			guard
				let keyInfo_biometrics = securityFile.biometrics as? KeyInfo_ChaChaPoly,
				let sealedBox_biometrics = try? keyInfo_biometrics.toSealedBox()
			else {
				
				if self.getSoftBiometricsEnabled() {
					return trySoftBiometrics(securityFile)
				} else {
					return fail(genericError(400, "SecurityFile doesn't have biometrics entry"))
				}
			}
			
			let context = LAContext()
			context.localizedReason = prompt ?? self.biometricsPrompt()
			
			var query = [String: Any]()
			query[kSecUseAuthenticationContext as String] = context
			
			let keychain = GenericPasswordStore()
			let account = keychain_accountName_biometrics
		
			let fetchedKey: SymmetricKey?
			do {
				fetchedKey = try keychain.readKey(
					account     : account,
					accessGroup : self.privateAccessGroup(),
					mixins      : query
				)
			} catch {
				return fail(error)
			}
			
			guard let lockingKey = fetchedKey else {
				return fail(genericError(401, "Biometrics keychain entry missing"))
			}
		
			// Decrypt the databaseKey using the lockingKey
			let mnemonicsData: Data
			do {
				mnemonicsData = try ChaChaPoly.open(sealedBox_biometrics, using: lockingKey)
			} catch {
				return fail(error)
			}
			
			guard let mnemonicsString = String(data: mnemonicsData, encoding: .utf8) else {
				return fail(genericError(500, "Keychain data is invalid"))
			}
			let mnemonics = mnemonicsString.split(separator: " ").map { String($0) }
			
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
					disableAdvancedSecurityAndSucceed(mnemonics)
				}
			}
		#else
		
			// iOS device
			disableAdvancedSecurityAndSucceed(mnemonics)
		
		#endif
		}
	}
	
	private func tryGenericBiometrics(
		prompt     : String? = nil,
		completion : @escaping (Bool, Error?) -> Void
	) -> Void {
		
		let context = LAContext()
		context.evaluatePolicy( .deviceOwnerAuthenticationWithBiometrics,
		       localizedReason: prompt ?? self.biometricsPrompt(),
		                 reply: completion)
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------------------------------------
	
	public func performMigration(previousBuild: String) -> Void {
		log.trace("performMigration(previousBuild: \(previousBuild))")
		
		if previousBuild.isVersion(lessThan: "5") {
			performMigration_toBuild5()
		}
		
		if previousBuild.isVersion(lessThan: "40") {
			performMigration_toBuild40()
		}
	}
	
	private func performMigration_toBuild5() {
		log.trace("performMigration_toBuild5()")
		
		let keychain = GenericPasswordStore()
		var hardBiometricsEnabled = false
		
		do {
			hardBiometricsEnabled = try keychain.keyExists(
				account     : keychain_accountName_biometrics,
				accessGroup : privateAccessGroup()
			)
		} catch {
			log.error("keychain.keyExists(account: hardBiometrics): error: \(String(describing: error))")
		}
		
		if hardBiometricsEnabled {
			// Then soft biometrics are implicitly enabled.
			// So we need to set that flag.
			
			let account = keychain_accountName_softBiometrics
			do {
				try keychain.deleteKey(
					account     : account,
					accessGroup : privateAccessGroup()
				)
			} catch {
				log.error("keychain.deleteKey(account: softBiometrics): error: \(String(describing: error))")
			}
			
			do {
				var query = [String: Any]()
				query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
				
				try keychain.storeKey( "true",
				              account: account,
				          accessGroup: privateAccessGroup(),
				               mixins: query)
				
			} catch {
				log.error("keychain.storeKey(account: softBiometrics): error: \(String(describing: error))")
			}
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
		
		let keychain = GenericPasswordStore()
		
		var savedKey: SymmetricKey? = nil
		do {
			savedKey = try keychain.readKey(
				account     : keychain_accountName_keychain,
				accessGroup : privateAccessGroup() // <- old location
			)
		} catch {
			log.error("keychain.readKey(account: keychain, group: nil): error: \(String(describing: error))")
		}
		
		if let lockingKey = savedKey {
			var migrated = false
			do {
				try keychain.deleteKey(
					account     : keychain_accountName_keychain,
					accessGroup : sharedAccessGroup())
			} catch {
				log.error("keychain.deleteKey(account: keychain, group: shared): error: \(String(describing: error))")
			}
			do {
				var query = [String: Any]()
				query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
				
				try keychain.storeKey( lockingKey,
				              account: keychain_accountName_keychain,
				          accessGroup: sharedAccessGroup(), // <- new location
				               mixins: query)
				migrated = true
				try keychain.deleteKey(
					account     : keychain_accountName_keychain,
					accessGroup : privateAccessGroup() // <- old location
				)
				
			} catch {
				if !migrated {
					log.error("keychain.storeKey(account: keychain, group: shared): error: \(String(describing: error))")
				} else {
					log.error("keychain.deleteKey(account: keychain, group: private): error: \(String(describing: error))")
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
