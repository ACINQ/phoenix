import SwiftUI

fileprivate let filename = "ContactPhoto"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactPhoto: View {
	
	@State var fileName: String?
	@State var size: CGFloat
	
	@State private var bgLoadedImage: UIImage? = nil
	
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
		.onAppear {
			log.trace("onAppear()")
		}
		.task {
			await loadImage()
		}
		.onChange(of: fileName) { _ in
			fileNameChanged()
		}
	}
	
	func loadImage() async {
		log.trace("loadImage(): \(fileName ?? "<nil>")")
		
		guard let photoFileName = fileName else {
			return
		}
		
		let filePath = PhotosManager.shared.filePathForPhoto(fileName: photoFileName)
		if let img = UIImage(contentsOfFile: filePath) {
			bgLoadedImage = img
		}
	}
	
	func fileNameChanged() {
		log.trace("fileNameChanged(): \(fileName ?? "<nil>")")
		
		// Todo...
	}
}
