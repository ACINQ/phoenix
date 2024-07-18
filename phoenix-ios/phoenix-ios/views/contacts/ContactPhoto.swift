import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactPhoto"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactPhoto: View {
	
	let fileName: String?
	let size: CGFloat
	let useCache: Bool
	
	init(fileName: String?, size: CGFloat, useCache: Bool = true) {
		self.fileName = fileName
		self.size = size
		self.useCache = useCache
		
		log.trace("[public] init(): \(fileName ?? "<nil>")")
	}
	
	@ViewBuilder
	var body: some View {
		
		_ContactPhoto(fileName: fileName, size: size, useCache: useCache)
			.id(uniqueId) // <- required
		
		// Due to "structural identity" in SwiftUI:
		// - even when `fileName` or `size` changes, it's still considered to be the "same" view
		// - which means we don't receive a notification via `onAppear`
		// - nor do our `.task` items re-fire
		//
		// In other words:
		// - zero notifications
		// - only a silent re-run of our ViewBuilder `body`
		//
		// To get around this, we use `.id` to force "explicit identity".
		// So when `fileName` or `size` changes, it will be a new instance of `_ContactPhoto`.
	}
	
	var uniqueId: String {
		return "\(fileName ?? "<nil>")@\(size)|\(useCache)"
	}
}

fileprivate struct _ContactPhoto: View {
	
	let fileName: String?
	let size: CGFloat
	let useCache: Bool
	
	@State private var bgLoadedImage: UIImage? = nil
	
	@Environment(\.displayScale) var displayScale: CGFloat
	
	init(fileName: String?, size: CGFloat, useCache: Bool) {
		self.fileName = fileName
		self.size = size
		self.useCache = useCache
		
		log.trace("[private] init(): \(fileName ?? "<nil>")")
	}
	
	@ViewBuilder
	var body: some View {
		
		Group {
			if let uiImage = bgLoadedImage {
				Image(uiImage: uiImage)
					.resizable()
					.aspectRatio(contentMode: .fill) // FILL !
			} else {
				Image(systemName: "person.circle")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(.gray)
			}
		}
		.frame(width: size, height: size)
		.clipShape(Circle())
		.task {
			await loadImage()
		}
	}
	
	func loadImage() async {
		log.trace("[private] loadImage(): \(fileName ?? "<nil>")")

		guard let fileName else {
			return
		}
		
		let targetSize = size * displayScale
		let img = await PhotosManager.shared.readFromDisk(
			fileName: fileName,
			size: targetSize,
			useCache: useCache
		)
		
		log.trace("[private] loadImage(): \(fileName) => done")
		bgLoadedImage = img
	}
}
