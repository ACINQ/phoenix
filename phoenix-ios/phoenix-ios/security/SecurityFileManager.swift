import Foundation
import PhoenixShared

fileprivate let filename = "SecurityFileManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class SecurityFileManager {
	
	/// Singleton instance
	public static let shared = SecurityFileManager()
	
	/// Serial queue ensures that only one operation is reading/modifying the
	/// security file at any given time.
	///
	private let queue = DispatchQueue(label: "SecurityFileManager")
	
	/// Cached version decreases trips to disk.
	///
	private var cachedSecurityFile: SecurityFile.VAny? = nil
	
	private init() { /* must use shared instance */ }
	
	func readFromDisk() -> Result<SecurityFile.VAny, ReadSecurityFileError> {
		
		return queue.sync {
			
			if let cached = cachedSecurityFile {
				return .success(cached)
			}
			
			switch SharedSecurity.shared.readSecurityJsonFromDisk_V1() {
			case .success(let v1):
				cachedSecurityFile = v1
				return .success(v1)
				
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
				cachedSecurityFile = v0
				return .success(v0)
				
			case .failure(let reason):
				return .failure(reason)
			}
		}
	}
	
	func writeToDisk(_ securityFile: SecurityFile.V1) -> Result<Void, WriteSecurityFileError> {
		
		return queue.sync {
			
			var shouldDeleteV0File: Bool = false
			if let cached = cachedSecurityFile {
				if cached is SecurityFile.V0 {
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
			cachedSecurityFile = securityFile
			
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
}
