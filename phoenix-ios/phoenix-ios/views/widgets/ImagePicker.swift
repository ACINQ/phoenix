import PhotosUI
import SwiftUI

fileprivate let filename = "ImagePicker"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ImagePicker: UIViewControllerRepresentable {
	
	let copyFile: Bool
	@Binding var result: PickerResult?

	func makeUIViewController(context: Context) -> PHPickerViewController {
		var config = PHPickerConfiguration()
		config.filter = .images
		let picker = PHPickerViewController(configuration: config)
		picker.delegate = context.coordinator
		return picker
	}

	func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {
	}

	func makeCoordinator() -> Coordinator {
		Coordinator(self)
	}
	
	class Coordinator: NSObject, PHPickerViewControllerDelegate {
		let parent: ImagePicker

		init(_ parent: ImagePicker) {
			self.parent = parent
		}

		func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
			picker.dismiss(animated: true)

			guard let provider = results.first?.itemProvider else { return }

			Task { @MainActor in
				
				var fileUrl: URL? = nil
				if #available(iOS 16, *), parent.copyFile {
					
					if let type = provider.registeredContentTypes(conformingTo: UTType.heic).first {
						log.debug("heic: available")
						if let url = try? await provider.asyncLoadFileRepresentation(for: type) {
							log.debug("heic: loaded")
							fileUrl = url
						}
					} else {
						log.debug("heic: NOT available")
					}
				
					if fileUrl == nil,
						let type = provider.registeredContentTypes(conformingTo: UTType.jpeg).first
					{
						log.debug("jpeg: available")
						if let url = try? await provider.asyncLoadFileRepresentation(for: type) {
							log.debug("jpeg: loaded")
							fileUrl = url
						}
					} else {
						log.debug("jpeg: NOT available")
					}
				}
				
				var fileCopyResult: FileCopyResult? = nil
				if let srcFileUrl = fileUrl {
				
				/*	This doesn't work! I guess we don't have access...
//					let keys = Set<URLResourceKey>([.fileSizeKey])
//					if let resourceValues = try? srcFileUrl.resourceValues(forKeys: keys) {
//						if let fileSize = resourceValues.fileSize {
//							fileCopyResult = FileCopyResult(url: srcFileUrl, size: fileSize)
//						}
//					}
				*/
					
					let tempDir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
					let fileName = UUID().uuidString
					let dstFileUrl = tempDir.appendingPathComponent(fileName, isDirectory: false)
					
					do {
						try FileManager.default.copyItem(at: srcFileUrl, to: dstFileUrl)
						
						let keys = Set<URLResourceKey>([.fileSizeKey])
						if let resourceValues = try? dstFileUrl.resourceValues(forKeys: keys) {
							if let fileSize = resourceValues.fileSize {
								fileCopyResult = FileCopyResult(url: dstFileUrl, size: fileSize)
							}
						}
						
					} catch {
						log.error("File copy error: \(error)")
					}
					
				}
				
				if let image = try? await provider.asyncLoadImage() {
					self.parent.result = PickerResult(image: image, file: fileCopyResult)
				}
				
			} // </Task>
		}
	}
}

// Compiler warning:
// > Passing argument of non-sendable type 'NSItemProvider' outside of main
// > actor-isolated context may introduce data races.
//
// This just silences the compiler until Apple marks NSItemProvider as Sendable
//
extension NSItemProvider: @unchecked Sendable { }
