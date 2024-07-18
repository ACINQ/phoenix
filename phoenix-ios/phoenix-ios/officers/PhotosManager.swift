import SwiftUI

fileprivate let filename = "PhotosManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class PhotosManager {
	
	/// Singleton instance
	public static let shared = PhotosManager()
	
	private init() { // must use shared instance
		// Nothing to do here yet
	}
	
	enum PhotosManagerError: Error {
		case conversionToJPEG
	}
	
	// --------------------------------------------------
	// MARK: Locations
	// --------------------------------------------------
	
	lazy var photosDirectory: URL = {
		
		// lazy == thread-safe (uses dispatch_once primitives internally)
		
		let fm = FileManager.default
		
		guard
			let appSupportDir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
		else {
			fatalError("FileManager returned nil appSupportDir !")
		}
		
		let photosDir = appSupportDir.appendingPathComponent("photos", isDirectory: true)
		do {
			try fm.createDirectory(at: photosDir, withIntermediateDirectories: true)
		} catch {
			fatalError("Error creating photos directory: \(error)")
		}
		
		return photosDir
	}()
	
	func urlForPhoto(fileName: String) -> URL {
		
		return photosDirectory.appendingPathComponent(fileName, isDirectory: false)
	}
	
	func filePathForPhoto(fileName: String) -> String {
		
		return urlForPhoto(fileName: fileName).path
	}
	
	// --------------------------------------------------
	// MARK: Writing
	// --------------------------------------------------
	
	func deleteFromDisk(
		fileName: String,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		return try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: qos).async {
				
				let fileUrl = self.urlForPhoto(fileName: fileName)
				do {
					try FileManager.default.removeItem(at: fileUrl)
					continuation.resume(with: .success)
				} catch {
					continuation.resume(throwing: error)
				}
			}
		}
	}
	
	func writeToDisk(
		image: UIImage,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws -> String {
		
		return try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: qos).async {
				
				guard let imageData = image.jpegData(compressionQuality: 1.0) else {
					continuation.resume(throwing: PhotosManagerError.conversionToJPEG)
					return
				}
				
				let fileName = UUID().uuidString.replacingOccurrences(of: "-", with: "")
				let fileUrl = self.urlForPhoto(fileName: fileName)
				
				do {
					try imageData.write(to: fileUrl)
					continuation.resume(returning: fileName)
				} catch {
					continuation.resume(throwing: error)
				}
			}
		}
	}
}
