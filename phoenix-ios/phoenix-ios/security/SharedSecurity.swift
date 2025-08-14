import Foundation
import PhoenixShared
import CryptoKit

fileprivate let filename = "SharedSecurity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = KeychainKey

/// Encompasses security operations shared between phoenix & phoenix-notifySrvExt
///
class SharedSecurity {
	
	/// Singleton instance
	///
	public static let shared = SharedSecurity()
	
	private init() {/* must use shared instance */}
	
	// --------------------------------------------------
	// MARK: Security JSON File
	// --------------------------------------------------
	
	public lazy var groupDirectoryUrl: URL = {
		
		let fm = FileManager.default
		guard let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix") else {
			fatalError("FileManager returned nil containerUrl !")
		}
		
		return groupDir
	}()
	
	public lazy var securityJsonUrl_V0: URL = {
		
		return groupDirectoryUrl.appendingPathComponent(SecurityFile.V0.filename, isDirectory: false)
	}()
	
	public lazy var securityJsonUrl_V1: URL = {
		
		return groupDirectoryUrl.appendingPathComponent(SecurityFile.V1.filename, isDirectory: false)
	}()
	
	/// Performs disk IO - use in background thread.
	///
	public func readSecurityJsonFromDisk_V0() -> Result<SecurityFile.V0, ReadSecurityFileError> {
		
		let fileUrl = self.securityJsonUrl_V0
		
		if !FileManager.default.fileExists(atPath: fileUrl.path) {
			return .failure(.fileNotFound)
		}
		
		let data: Data
		do {
			data = try Data(contentsOf: fileUrl)
		} catch {
			log.error("readSecurityJsonFromDisk_V0(): error reading file: \(String(describing: error))")
			return .failure(.errorReadingFile(underlying: error))
		}
		
		let result: SecurityFile.V0
		do {
			result = try JSONDecoder().decode(SecurityFile.V0.self, from: data)
		} catch {
			log.error("readSecurityJsonFromDisk_V0(): error decoding file: \(String(describing: error))")
			return .failure(.errorDecodingFile(underlying: error))
		}
		
		return .success(result)
	}
	
	public func readSecurityJsonFromDisk_V1() -> Result<SecurityFile.V1, ReadSecurityFileError> {
		
		let fileUrl = self.securityJsonUrl_V1
		
		if !FileManager.default.fileExists(atPath: fileUrl.path) {
			return .failure(.fileNotFound)
		}
		
		let data: Data
		do {
			data = try Data(contentsOf: fileUrl)
		} catch {
			log.error("readSecurityJsonFromDisk_V1(): error reading file: \(String(describing: error))")
			return .failure(.errorReadingFile(underlying: error))
		}
		
		let result: SecurityFile.V1
		do {
			result = try JSONDecoder().decode(SecurityFile.V1.self, from: data)
		} catch {
			log.error("readSecurityJsonFromDisk_V1(): error decoding file: \(String(describing: error))")
			return .failure(.errorDecodingFile(underlying: error))
		}
		
		return .success(result)
	}
	
	// --------------------------------------------------
	// MARK: Keychain
	// --------------------------------------------------
	
	public func readKeychainEntry(
		_ id: String,
		_ keyInfo: KeyInfo_ChaChaPoly
	) -> Result<Data, ReadKeychainError> {
		
		let sealedBox: ChaChaPoly.SealedBox
		do {
			sealedBox = try keyInfo.toSealedBox()
		} catch {
			log.error("readKeychainEntry(): error: keychainBoxCorrupted: \(String(describing: error))")
			return .failure(.keychainBoxCorrupted(underlying: error))
		}
		
		var mixins = [String: Any]()
		mixins[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		
		// Read the lockingKey from the OS keychain
		let fetchedKey: SymmetricKey?
		do {
			fetchedKey = try SystemKeychain.readItem(
				account     : Key.lockingKey.account(id),
				accessGroup : Key.lockingKey.accessGroup.value,
				mixins      : mixins
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
		let cleartextData: Data
		do {
			cleartextData = try ChaChaPoly.open(sealedBox, using: lockingKey)
		} catch {
			log.error("readKeychainEntry(): error: openingBox: \(String(describing: error))")
			return .failure(.errorOpeningBox(underlying: error))
		}
		
		return .success(cleartextData)
	}
	
	// --------------------------------------------------
	// MARK: Recovery Phrase
	// --------------------------------------------------
	
	public func decodeRecoveryPhrase(_ cleartextData: Data) -> Result<RecoveryPhrase, ReadRecoveryPhraseError> {
		
		guard let cleartextString = String(data: cleartextData, encoding: .utf8) else {
			log.error("decodeRecoveryPhrase(): error: invalid ciphertext")
			return .failure(.invalidCiphertext)
		}
		
		// Version 1:
		// - cleartextString is mnemonicString
		// - language is english
		//
		// Version 2:
		// - cleartextString is JSON-encoded RecoveryPhrase
		
		let result: RecoveryPhrase
		if cleartextString.starts(with: "{") {
			
			do {
				result = try JSONDecoder().decode(RecoveryPhrase.self, from: cleartextData)
			} catch {
				log.error("decodeRecoveryPhrase(): error: invalid json")
				return .failure(.invalidJSON)
			}
			
		} else {
			
			result = RecoveryPhrase(
				mnemonics: cleartextString,
				language: MnemonicLanguage.english
			)
		}
		
		return .success(result)
	}
}
