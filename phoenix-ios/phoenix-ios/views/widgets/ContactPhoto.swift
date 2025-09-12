import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactPhoto"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactPhoto: View {
	
	let filename: String?
	let size: CGFloat
	let useCache: Bool
	
	init(filename: String?, size: CGFloat, useCache: Bool = true) {
		self.filename = filename
		self.size = size
		self.useCache = useCache
		
		log.trace("[public] init(): \(filename ?? "<nil>")")
	}
	
	@ViewBuilder
	var body: some View {
		
		_ContactPhoto(filename: filename, size: size, useCache: useCache)
			.id(uniqueId) // <- required
		
		// Due to "structural identity" in SwiftUI:
		// - even when `filename` or `size` changes, it's still considered to be the "same" view
		// - which means we don't receive a notification via `onAppear`
		// - nor does our `.task` item re-fire
		//
		// In other words:
		// - zero notifications
		// - only a silent re-run of our ViewBuilder `body`
		//
		// To get around this, we use `.id` to force "explicit identity".
		// So when `filename` or `size` changes, it will be a new instance of `_ContactPhoto`.
	}
	
	var uniqueId: String {
		return "\(filename ?? "<nil>")@\(size)|\(useCache)"
	}
}

fileprivate struct _ContactPhoto: View {
	
	let filename: String?
	let size: CGFloat
	let useCache: Bool
	
	@State private var bgLoadedImage: UIImage? = nil
	
	@Environment(\.displayScale) var displayScale: CGFloat
	
	init(filename: String?, size: CGFloat, useCache: Bool) {
		self.filename = filename
		self.size = size
		self.useCache = useCache
		
		log.trace("[private] init(): \(filename ?? "<nil>")")
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
					.foregroundColor(Color(UIColor.systemGray3))
			}
		}
		.frame(width: size, height: size)
		.clipShape(Circle())
		.task {
			await loadImage()
		}
	}
	
	func loadImage() async {
		log.trace("[private] loadImage(): \(filename ?? "<nil>")")

		guard let filename else {
			return
		}
		
		let targetSize = size * displayScale
		let img = await PhotosManager.shared.readFromDisk(
			fileName: filename,
			size: targetSize,
			useCache: useCache
		)
		
		log.trace("[private] loadImage(): \(filename) => done")
		bgLoadedImage = img
	}
}
