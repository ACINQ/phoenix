import Foundation
import PhoenixShared

fileprivate let filename = "SecurityFileManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletMetadata {
	let hidden: Bool
	let name: String
	let photo: String
	
	init(hidden: Bool, name: String, photo: String) {
		self.hidden = hidden
		self.name = name
		self.photo = photo
	}
	
	init(wallet: SecurityFile.V1.Wallet) {
		self.hidden = wallet.hidden
		self.name = wallet.name
		self.photo = wallet.photo
	}
	
	static func `default`() -> WalletMetadata {
		return WalletMetadata(
			hidden : false,
			name   : "?",
			photo  : WalletIcon.default.filename
		)
	}
}

class SecurityFileManager {
	
	/// Singleton instance
	public static let shared = SecurityFileManager()
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// security file at any given time.
	///
	private let queue = DispatchQueue(label: "SecurityFileManager")
	
	/// Cached version decreases trips to disk.
	///
	private var cachedSecurityFile: SecurityFile.Version? = nil
	
	private init() { /* must use shared instance */ }
	
	// --------------------------------------------------
	// MARK: State
	// --------------------------------------------------
	
	func currentWalletMetadata() -> WalletMetadata? {
		
		return queue.sync {
			
			if case .v1(let v1) = self.cachedSecurityFile {
				let currentId = Biz.walletId?.keychainKeyId ?? KEYCHAIN_DEFAULT_ID
				if let wallet = v1.wallets[currentId] {
					return WalletMetadata(
						hidden: wallet.hidden,
						name: wallet.name,
						photo: wallet.photo
					)
				}
			}
			return nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Read
	// --------------------------------------------------
	
	func readFromDisk() -> Result<SecurityFile.Version, ReadSecurityFileError> {
		log.trace(#function)
		
		return queue.sync {
			
			if let cached = cachedSecurityFile {
				return .success(cached)
			}
			
			switch SharedSecurity.shared.readSecurityJsonFromDisk_V1() {
			case .success(let v1):
				let result = SecurityFile.Version.v1(file: v1)
				cachedSecurityFile = result
				return .success(result)
				
			case .failure(let reason):
				switch reason {
				case .errorReadingFile(_):
					return .failure(reason)
					
				case .errorDecodingFile(_):
					return .failure(reason)
					
				case .fileNotFound:
					break
				}
			}
			
			switch SharedSecurity.shared.readSecurityJsonFromDisk_V0() {
			case .success(let v0):
				let result = SecurityFile.Version.v0(file: v0)
				cachedSecurityFile = result
				return .success(result)
				
			case .failure(let reason):
				return .failure(reason)
			}
		}
	}
	
	func asyncReadFromDisk(
		qos: DispatchQoS.QoSClass = .userInitiated,
		completion: @escaping (Result<SecurityFile.Version, ReadSecurityFileError>) -> Void
	) {
		log.trace(#function)
		
		DispatchQueue.global(qos: qos).async {
			let result = self.readFromDisk()
			DispatchQueue.main.async {
				completion(result)
			}
		}
	}
	
	func asyncReadFromDisk(
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws(ReadSecurityFileError) -> SecurityFile.Version {
		
		let result = await withCheckedContinuation { continuation in
			asyncReadFromDisk(qos: qos) { result in
				continuation.resume(returning: result)
			}
		}
		switch result {
		case .failure(let error):
			throw error
		case .success(let securityFile):
			return securityFile
		}
	}
	
	// --------------------------------------------------
	// MARK: Write
	// --------------------------------------------------
	
	func writeToDisk(
		_ securityFile: SecurityFile.V1
	) -> Result<Void, WriteSecurityFileError> {
		log.trace(#function)
		
		return queue.sync {
			
			var shouldDeleteV0File: Bool = false
			if let cached = cachedSecurityFile {
				if case .v0(_) = cached {
					shouldDeleteV0File = true
				}
			}
			
			var url = SharedSecurity.shared.securityJsonUrl_V1
			
			let jsonData: Data
			do {
				jsonData = try JSONEncoder().encode(securityFile)
			} catch {
				return .failure(.errorEncodingFile(underlying: error))
			}
			
			do {
				try jsonData.write(to: url, options: [.atomic])
				log.debug("Wrote SecurityFile.V1")
			} catch {
				return .failure(.errorWritingFile(underlying: error))
			}
			cachedSecurityFile = .v1(file: securityFile)
			
			do {
				var resourceValues = URLResourceValues()
				resourceValues.isExcludedFromBackup = true
				try url.setResourceValues(resourceValues)
				
			} catch {
				// Don't throw from this error as it's an optimization
				log.error("Error excluding \(url.lastPathComponent) from backup \(error)")
			}
			
			if shouldDeleteV0File {
				do {
					let oldUrl = SharedSecurity.shared.securityJsonUrl_V0
					try FileManager.default.removeItem(at: oldUrl)
					log.debug("Deleted SecurityFile.V0")
				} catch {
					log.error("Error deleting SecurityFile.V0: \(error)")
				}
			}
			
			return .success
		}
	}
	
	func asyncWriteToDisk(
		_ securityFile: SecurityFile.V1,
		qos: DispatchQoS.QoSClass = .userInitiated,
		completion: @escaping (Result<Void, WriteSecurityFileError>) -> Void
	) {
		log.trace(#function)
		
		DispatchQueue.global(qos: .userInitiated).async {
			let result = self.writeToDisk(securityFile)
			DispatchQueue.main.async {
				completion(result)
			}
		}
	}
	
	func asyncWriteToDisk(
		_ securityFile: SecurityFile.V1,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws(WriteSecurityFileError) -> Void {
		
		let result = await withCheckedContinuation { continuation in
			asyncWriteToDisk(securityFile, qos: qos) { result in
				continuation.resume(returning: result)
			}
		}
		if case .failure(let error) = result {
			throw error
		}
	}
}
