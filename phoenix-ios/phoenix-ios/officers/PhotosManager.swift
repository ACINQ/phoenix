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
	
	static var photosDirectory: URL = {
		
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
	
	static func genFileName() -> String {
		return UUID().uuidString.replacingOccurrences(of: "-", with: "")
	}
	
	static func urlForPhoto(fileName: String) -> URL {
		return photosDirectory.appendingPathComponent(fileName, isDirectory: false)
	}
	
	static func filePathForPhoto(fileName: String) -> String {
		return urlForPhoto(fileName: fileName).path
	}
	
	// --------------------------------------------------
	// MARK: Writing
	// --------------------------------------------------
	
	enum PhotosManagerError: Error {
		case writingToDisk
		case copyingFile
		case compressionFailed
	}
	
	func writeToDisk(_ original: PickerResult) async throws -> String {
		
		let fileName = Self.genFileName()
		let fileUrl = Self.urlForPhoto(fileName: fileName)
				
		let scaled = await original.downscale()
		if let compressedImageData = await scaled.compress() {
			
			do {
				try compressedImageData.write(to: fileUrl)
				log.debug("compressedImage: \(fileUrl)")
				
				return fileName
			} catch {
				throw PhotosManagerError.writingToDisk
			}
			
		} else if let originalFileUrl = scaled.file?.url {
			
			do {
				try FileManager.default.copyItem(at: originalFileUrl, to: fileUrl)
				log.debug("originalImage: \(originalFileUrl)")
						
				return fileName
			} catch {
				throw PhotosManagerError.copyingFile
			}
					
		} else {
			
			log.debug("compression failed")
			throw PhotosManagerError.compressionFailed
		}
	}

	func deleteFromDisk(fileName: String) async {
		
		let fileUrl = Self.urlForPhoto(fileName: fileName)
		do {
			try FileManager.default.removeItem(at: fileUrl)
		} catch {
			log.warning("FileManager.remoteItem(\(fileUrl.lastPathComponent)): error: \(error)")
		}
	}
	
	// --------------------------------------------------
	// MARK: Reading
	// --------------------------------------------------
	
	func readFromDisk(fileName: String, size: CGFloat, useCache: Bool = true) async -> UIImage? {
		
		let readTask = {() -> UIImage? in
			do {
				let fileUrl = Self.urlForPhoto(fileName: fileName)
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
		
		if useCache {
			var result: UIImage? = nil
			queue.sync {
				let key = "\(fileName)|\(size)"
				if let cachedImg = self.cache[key] {
					result = cachedImg
				} else if let img = readTask() {
					self.cache[key] = img
					result = img
				}
			}
			return result
		} else {
			return readTask()
		}
	}
}
