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
	
	private let queue = DispatchQueue.init(label: "PhotosManager")
	private let cache = Cache<String, UIImage>(countLimit: 30)
	
	private let memoryPressure: DispatchSourceMemoryPressure
	
	private init() { // must use shared instance
		
		memoryPressure = DispatchSource.makeMemoryPressureSource(
			eventMask: [.warning, .critical],
			queue: queue
		)
		memoryPressure.setEventHandler { [weak self] in
			guard let self else {
				return
			}
			let event = self.memoryPressure.data
			switch event {
				case .warning : self.respondToMemoryPressure(event)
				case.critical : self.respondToMemoryPressure(event)
				default       : break
			}
		}
		memoryPressure.activate()
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func respondToMemoryPressure(_ event: DispatchSource.MemoryPressureEvent) {
		log.trace("respondToMemoryPressure()")
		
		// Note: This function is invoked on our `queue`, so we can safely modify the `cache` variable.
		
		cache.removeAll()
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
	
	enum PhotosManagerError: Error {
		case conversionToJPEG
	}
	
	func writeToDisk(image: UIImage) async throws -> String {
		
		return try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: .userInitiated).async {
				
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

	func deleteFromDisk(fileName: String) async throws {
		
		return try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: .userInitiated).async {
				
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
	
	// --------------------------------------------------
	// MARK: Reading
	// --------------------------------------------------
	
	func readFromDisk(fileName: String, size: CGFloat, useCache: Bool = true) async -> UIImage? {
		
		let readTask = {() -> UIImage? in
			do {
				let fileUrl = self.urlForPhoto(fileName: fileName)
				let data = try Data(contentsOf: fileUrl, options: [.mappedIfSafe, .uncached])
				guard let fullSizePhoto = UIImage(data: data) else {
					return nil
				}
				
				let cgsize = CGSize(width: size, height: size)
				guard let scaledPhoto = fullSizePhoto.preparingThumbnail(of: cgsize) else {
					return nil
				}
				
				return scaledPhoto
			} catch {
				log.warning("readFromDisk: error: \(error)")
				return nil
			}
		}
		
		return await withCheckedContinuation { continuation in
			if useCache {
				queue.async {
					let key = "\(fileName)|\(size)"
					if let cachedImg = self.cache[key] {
						continuation.resume(returning: cachedImg)
					} else if let img = readTask() {
						self.cache[key] = img
						continuation.resume(returning: img)
					} else {
						continuation.resume(returning: nil)
					}
				}
			} else {
				DispatchQueue.global(qos: .userInitiated).async {
					let img = readTask()
					continuation.resume(returning: img)
				}
			}
		}
	}
}
