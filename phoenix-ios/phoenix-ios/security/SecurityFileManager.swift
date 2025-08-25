import Foundation
import PhoenixShared
import Combine

fileprivate let filename = "SecurityFileManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class SecurityFileManager {
	
	/// Singleton instance
	public static let shared = SecurityFileManager()
	
	/// To be notified when `currentSecurityFile` changes.
	/// Changes to publisher always posted to main thread.
	///
	public let currentSecurityFilePublisher = CurrentValueSubject<SecurityFile.Version?, Never>(nil)
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// security file at any given time.
	///
	private let queue = DispatchQueue(label: "SecurityFileManager")
	
	/// Cached version decreases trips to disk.
	///
	private var cachedSecurityFile: SecurityFile.Version? = nil {
		didSet {
			let updatedValue = cachedSecurityFile
			runOnMainThread {
				self.currentSecurityFilePublisher.send(updatedValue)
			}
		}
	}
	
	private init() { /* must use shared instance */ }
	
	// --------------------------------------------------
	// MARK: State
	// --------------------------------------------------
	
	func currentSecurityFile() -> SecurityFile.Version? {
		
		return queue.sync { self.cachedSecurityFile }
	}
	
	func currentWallet() -> WalletMetadata? {
		
		guard let currentId = Biz.walletId else {
			return nil
		}
		guard case .v1(let v1) = self.currentSecurityFile() else {
			return nil
		}
		guard let wallet = v1.getWallet(currentId) else {
			return nil
		}
		
		let isDefault = v1.isDefaultWalletId(currentId)
		return WalletMetadata(wallet: wallet, id: currentId, isDefault: isDefault)
	}
	
	func defaultWallet() -> WalletMetadata? {
		
		guard case .v1(let v1) = self.currentSecurityFile() else {
			return nil
		}
		guard let wallet = v1.defaultWallet(), let keyComps = v1.defaultKeyComponents() else {
			return nil
		}
		return WalletMetadata(wallet: wallet, keyComps: keyComps, isDefault: true)
	}
	
	func hasZeroWallets() -> Bool {
		
		guard case .v1(let v1) = self.currentSecurityFile() else {
			return true
		}
		return v1.wallets.isEmpty
	}
	
	func allWallets() -> [WalletMetadata] {
		
		guard case .v1(let v1) = self.currentSecurityFile() else {
			return []
		}
	
		let defaultKey = v1.defaultKey
		return v1.wallets.compactMap { key, wallet in
			if let keyComps = SecurityFile.V1.KeyComponents.fromId(key) {
				WalletMetadata(wallet: wallet, keyComps: keyComps, isDefault: (key == defaultKey))
			} else {
				nil
			}
		}
	}
	
	func sortedWallets() -> [WalletMetadata] {
	
		return allWallets().sorted()
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
			
			switch SharedSecurity.shared.readSecurityJsonFromDisk() {
			case .success(let result):
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
		
		DispatchQueue.global(qos: qos).async {
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
