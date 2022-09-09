import Foundation
import CryptoKit
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SharedSecurity"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum ReadSecurityFileError: Error {
	case fileNotFound
	case errorReadingFile(underlying: Error)
	case errorDecodingFile(underlying: Error)
}

enum ReadKeychainError: Error {
	case keychainOptionNotEnabled
	case keychainBoxCorrupted(underlying: Error)
	case errorReadingKey(underlying: Error)
	case keyNotFound
	case errorOpeningBox(underlying: Error)
	case invalidMnemonics
}

/// Encompasses security operations shared between phoenix & phoenix-notifySrvExt
///
class SharedSecurity {
	
	/// Singleton instance
	///
	public static let shared = SharedSecurity()
	
	private init() {/* must use shared instance */}
	
	// --------------------------------------------------------------------------------
	// MARK: Access Groups
	// --------------------------------------------------------------------------------
	
	/// Represents the keychain domain for our app group.
	/// I.E. can be accessed by our app extensions (e.g. notification-service-extension).
	///
	public func sharedAccessGroup() -> String {
		
		return "group.co.acinq.phoenix"
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Security JSON File
	// --------------------------------------------------------------------------------
	
	public lazy var securityJsonUrl: URL = {
		
		// lazy == thread-safe (uses dispatch_once primitives internally)
		
		let fm = FileManager.default
		guard let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix") else {
			fatalError("FileManager returned nil containerUrl !")
		}
		
		return groupDir.appendingPathComponent("security.json", isDirectory: false)
	}()
	
	/// Performs disk IO - use in background thread.
	///
	public func readSecurityJsonFromDisk() -> Result<SecurityFile, ReadSecurityFileError> {
		
		let fileUrl = self.securityJsonUrl
		
		if !FileManager.default.fileExists(atPath: fileUrl.path) {
			return .failure(.fileNotFound)
		}
		
		let data: Data
		do {
			data = try Data(contentsOf: fileUrl)
		} catch {
			log.error("readSecurityJsonFromDisk(): error reading file: \(String(describing: error))")
			return .failure(.errorReadingFile(underlying: error))
		}
		
		let result: SecurityFile
		do {
			result = try JSONDecoder().decode(SecurityFile.self, from: data)
		} catch {
			log.error("readSecurityJsonFromDisk(): error decoding file: \(String(describing: error))")
			return .failure(.errorDecodingFile(underlying: error))
		}
		
		return .success(result)
	}
	
	// --------------------------------------------------------------------------------
	// MARK: Keychain
	// --------------------------------------------------------------------------------
	
	func readKeychainEntry(_ securityFile: SecurityFile) -> Result<[String], ReadKeychainError> {
		
		// The securityFile tells us which security options have been enabled.
		// If there isn't a keychain entry, then we cannot unlock the seed.
		guard let keyInfo = securityFile.keychain as? KeyInfo_ChaChaPoly else {
			return .failure(.keychainOptionNotEnabled)
		}
		
		let sealedBox: ChaChaPoly.SealedBox
		do {
			sealedBox = try keyInfo.toSealedBox()
		} catch {
			log.error("readKeychainEntry(): error: keychainBoxCorrupted: \(String(describing: error))")
			return .failure(.keychainBoxCorrupted(underlying: error))
		}
		
		let keychain = GenericPasswordStore()
		
		// Read the lockingKey from the OS keychain
		let fetchedKey: SymmetricKey?
		do {
			fetchedKey = try keychain.readKey(
				account     : keychain_accountName_keychain,
				accessGroup : sharedAccessGroup()
			)
		} catch {
			log.error("readKeychainEntry(): error: readingKey: \(String(describing: error))")
			return .failure(.errorReadingKey(underlying: error))
		}
		
		guard let lockingKey = fetchedKey else {
			log.error("readKeychainEntry(): error: keyNotFound")
			return .failure(.keyNotFound)
		}
		
		// Decrypt the databaseKey using the lockingKey
		let mnemonicsData: Data
		do {
			mnemonicsData = try ChaChaPoly.open(sealedBox, using: lockingKey)
		} catch {
			log.error("readKeychainEntry(): error: openingBox: \(String(describing: error))")
			return .failure(.errorOpeningBox(underlying: error))
		}
		
		guard let mnemonicsString = String(data: mnemonicsData, encoding: .utf8) else {
			log.error("readKeychainEntry(): error: invalidMnemonics")
			return .failure(.invalidMnemonics)
		}
		
		let mnemonics = mnemonicsString.split(separator: " ").map { String($0) }
		return .success(mnemonics)
	}
}
