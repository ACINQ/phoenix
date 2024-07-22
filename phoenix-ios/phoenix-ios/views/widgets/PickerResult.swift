import Foundation
import UIKit
import AVFoundation

fileprivate let filename = "PickerResult"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class PickerResult: Equatable {
	
	let image: UIImage
	let file: FileCopyResult?
	
	init(image: UIImage, file: FileCopyResult?) {
		self.image = image
		self.file = file
	}
	
	func downscale(maxWidth: CGFloat = 1_000, maxHeight: CGFloat = 1_000) async -> PickerResult {
		
		guard image.size.width > maxWidth, image.size.height > maxHeight else {
			log.debug("PickerResult.downscale: image too small - downsize not required")
			return self
		}
		
		let targetSize = CGSize(width: maxWidth, height: maxHeight)
		guard let scaledImage = image.preparingThumbnail(of: targetSize) else {
			log.error("PickerResult.downscale: image.preparingThumbnail returned nil")
			return self
		}
		
		return PickerResult(image: scaledImage, file: self.file)
	}
	
	func compress() async -> Data? {
		
		let isSmallerThanExistingFile = { (data: Data) -> Bool in
			if let existingFile = self.file {
				return data.count < existingFile.size
			} else {
				return true
			}
		}
		
		if let result = compressWithHeic_optionA(), isSmallerThanExistingFile(result) {
			return result
		}
		if let result = compressWithHeic_optionB(), isSmallerThanExistingFile(result) {
			return result
		}
		if let result = compressWithJpeg(), isSmallerThanExistingFile(result) {
			return result
		}
		
		return nil
	}
	
	private func compressWithHeic_optionA() -> Data? {
		
		// UIImage has a method `heicData` that just works.
		// Unfortunately it's only available on iOS 17.
		// And it doesn't take a compressionQuality parameter as you would expect.
		//
		// But it does actually work.
		// Unlike Apple's buggy and/or undocumented low-level stuff.
		
		var compressedImageData: Data? = nil
		if #available(iOS 17, *) {
			compressedImageData = image.heicData()
		}
		
		return compressedImageData
	}
	
	private func compressWithHeic_optionB() -> Data? {
		
		// We can use CGImageDestination to create the HEIC file.
		// But it only seems to work if the image isn't rotated.
		//
		// Details:
		// When you take a photo on the iPhone, the raw data (rows & columns of color information)
		// is always stored in the same orientation, according to the hardware of the camera.
		// So how does rotation work ? Via metadata.
		// A `rotation` flag is stored in the image's metadata. This is later read by software,
		// which automatically rotates the image for display on the screen.
		//
		// The problem we have is that we're unable to properly set this orientation flag.
		// You're supposed to be able to use the `kCGImageDestinationOrientation` option.
		// But I've tried setting this flag a hundred different ways, and no matter what I do,
		// the CGImageDestination code always writes a file with the orientation flag set to 1.
		//
		// So we're only going to use this option if the bug won't affect us.
		
		guard image.imageOrientation == .up else {
			return nil
		}
		
		let data = NSMutableData()
		guard let imageDestination =
			CGImageDestinationCreateWithData(
				data, AVFileType.heic as CFString, 1, nil
			) else {
				log.error("PickerResult.compressWithHeic: heic not supported")
				return nil
		}
		
		guard let cgImage = image.cgImage else {
			log.error("PickerResult.compressWithHeic: cgImage missing")
			return nil
		}

		let orientation: CGImagePropertyOrientation = image.imageOrientation.cgImageOrientation
		let options: NSDictionary = [
			kCGImageDestinationLossyCompressionQuality as String: NSNumber(value: 0.98),
			kCGImageDestinationOrientation as String: NSNumber(value: orientation.rawValue), // does NOT work
		]
		
		CGImageDestinationAddImageAndMetadata(imageDestination, cgImage, nil, options)
		guard CGImageDestinationFinalize(imageDestination) else {
			log.error("PickerResult.compressWithHeic: could not finalize")
			return nil
		}
		
		return data as Data
	}
	
	private func compressWithJpeg() -> Data? {
		
		return image.jpegData(compressionQuality: 0.98)
	}
	
	static func == (lhs: PickerResult, rhs: PickerResult) -> Bool {
		if lhs.image != rhs.image {
			return false
		}
		if lhs.file != rhs.file {
			return false
		}
		return true
	}
}

class FileCopyResult: Equatable {
	
	let url: URL
	let size: Int // in bytes
	
	init(url: URL, size: Int) {
		self.url = url
		self.size = size
	}
	
	deinit {
		let fileUrl = url
		DispatchQueue.global(qos: .background).async {
			do {
				if FileManager.default.isDeletableFile(atPath: fileUrl.path) {
					try FileManager.default.removeItem(at: fileUrl)
					log.info("FileCopyResult.cleanup: deleted temp file")
				} else {
					log.debug("FileCopyResult.cleanup: unowned temp file")
				}
			} catch {
				log.error("FileCopyResult.cleanup: cannot delete file: \(error)")
			}
		}
	}
	
	static func == (lhs: FileCopyResult, rhs: FileCopyResult) -> Bool {
		if lhs.url != rhs.url {
			return false
		}
		if lhs.size != rhs.size {
			return false
		}
		return true
	}
}

extension UIImage.Orientation {
	
	// The rawValues for UIImageOrientation do NOT match CGImagePropertyOrientation :(
	// https://developer.apple.com/documentation/imageio/cgimagepropertyorientation
	//
	var cgImageOrientation: CGImagePropertyOrientation {
		switch self {
			case .up            : return CGImagePropertyOrientation.up
			case .upMirrored    : return CGImagePropertyOrientation.upMirrored
			case .down          : return CGImagePropertyOrientation.down
			case .downMirrored  : return CGImagePropertyOrientation.downMirrored
			case .left          : return CGImagePropertyOrientation.left
			case .leftMirrored  : return CGImagePropertyOrientation.leftMirrored
			case .right         : return CGImagePropertyOrientation.right
			case .rightMirrored : return CGImagePropertyOrientation.rightMirrored
			@unknown default    : fatalError("Unknown UIImage.Orientation")
		}
	}
}
