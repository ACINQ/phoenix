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
	
	@ViewBuilder
	var body: some View {
		
		_ContactPhoto(fileName: fileName, size: size)
			.id("\(fileName ?? "<nil>")@\(size)") // <- required
		
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
}

fileprivate struct _ContactPhoto: View {
	
	let fileName: String?
	let size: CGFloat
	
	@State private var bgLoadedImage: UIImage? = nil
	
	init(fileName: String?, size: CGFloat) {
		self.fileName = fileName
		self.size = size
		
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
		
		let fileUrl = PhotosManager.shared.urlForPhoto(fileName: fileName)
		DispatchQueue.global(qos: .userInteractive).async {
			do {
				let data = try Data(contentsOf: fileUrl, options: [.mappedIfSafe, .uncached])
				guard let fullSizePhoto = UIImage(data: data) else {
					return
				}
				
				let cgsize = CGSize(width: size, height: size)
				let scaledPhoto = UIGraphicsImageRenderer(size: cgsize).image { _ in
					fullSizePhoto.draw(in: CGRect(origin: .zero, size: cgsize))
				}
				
				bgLoadedImage = scaledPhoto
				
			} catch {
				log.warning("loadImage: error: \(error)")
			}
		}
	}
}
