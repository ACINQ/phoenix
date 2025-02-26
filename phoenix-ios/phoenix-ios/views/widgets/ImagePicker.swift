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
				if parent.copyFile {
					
					if let type = provider.registeredContentTypes(conformingTo: UTType.heic).first {
						log.debug("heic: available")
						if let url = try? await provider.asyncLoadFileRepresentation(for: type) {
							log.debug("heic: loaded")
							fileUrl = url
						}
					} else {
						log.debug("heic: NOT available")
					}
				
					if fileUrl == nil {
						if let type = provider.registeredContentTypes(conformingTo: UTType.jpeg).first {
							log.debug("jpeg: available")
							if let url = try? await provider.asyncLoadFileRepresentation(for: type) {
								log.debug("jpeg: loaded")
								fileUrl = url
							}
						} else {
							log.debug("jpeg: NOT available")
						}
					}
				}
				
				var fileCopyResult: FileCopyResult? = nil
				if let fileUrl {
					let keys = Set<URLResourceKey>([.fileSizeKey])
					if let resourceValues = try? fileUrl.resourceValues(forKeys: keys) {
						if let fileSize = resourceValues.fileSize {
							fileCopyResult = FileCopyResult(url: fileUrl, size: fileSize)
						}
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
extension NSItemProvider: @unchecked @retroactive Sendable { }
