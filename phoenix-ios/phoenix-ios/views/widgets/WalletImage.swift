import SwiftUI

fileprivate let filename = "WalletImage"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletImage: View {
	
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
		
		_WalletImage(filename: filename, size: size, useCache: useCache)
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
		// So when `filename` or `size` changes, it will be a new instance of `_WalletImage`.
	}
	
	var uniqueId: String {
		return "\(filename ?? "<nil>")@\(size)|\(useCache)"
	}
}

fileprivate struct _WalletImage: View {
	
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
					
			} else if let emoji = asWalletEmoji() {
				walletEmojiImage(emoji)
			} else if let systemImage = asSystemImage() {
				systemIconImage(systemImage)
			} else {
				walletEmojiImage(WalletEmoji.default)
			}
		}
		.frame(width: size, height: size)
		.clipShape(Circle())
		.task {
			await loadImage()
		}
	}
	
	@ViewBuilder
	func walletEmojiImage(_ emoji: WalletEmoji) -> some View {
		
		ZStack(alignment: Alignment.center) {
			Image(systemName: "circle.fill")
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: size, height: size)
				.foregroundColor(Color(UIColor.systemGray5))
			
			Image(systemName: "circle.fill")
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: size-1, height: size-1)
				.foregroundColor(Color(UIColor.systemGray6))
			
			let ss = squareSize()
			Text(emoji.emoji)
				.font(.system(size: 100))
				.minimumScaleFactor(0.1)
				.frame(width: ss, height: ss)
			
		} // </ZStack>
	}
	
	@ViewBuilder
	func systemIconImage(_ systemImage: String) -> some View {
		
		ZStack {
			Image(systemName: "circle.fill")
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: size, height: size)
				.foregroundColor(Color(UIColor.systemGray5))
			
			Image(systemName: "circle.fill")
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: size-1, height: size-1)
				.foregroundColor(Color(UIColor.systemGray6))
			
			let ss = squareSize()
			Image(systemName: systemImage)
				.resizable()
				.aspectRatio(contentMode: .fit)
				.frame(width: ss, height: ss)
				.foregroundColor(.appAccent)
			
		} // </ZStack>
	}
	
	func squareSize() -> CGFloat {
		let diameterOfCircle = size
		let radiusOfCircle = diameterOfCircle / 2.0
		let squareSize = radiusOfCircle * sqrt(2.0)
		let buffer: CGFloat = size / 16

		return (squareSize - buffer)
	}
	
	func asWalletEmoji() -> WalletEmoji? {
		
		guard let filename else {
			return nil
		}
		return WalletEmoji.fromFilename(filename)
	}
	
	func asSystemImage() -> String? {
		
		guard let filename, filename.hasPrefix(":") else {
			return nil
		}
		return filename.substring(location: 1)
	}
	
	func loadImage() async {
		log.trace("[private] loadImage(): \(filename ?? "<nil>")")

		guard let filename else {
			return
		}
		guard !filename.isEmpty else {
			return
		}
		guard asWalletEmoji() == nil else {
			return
		}
		guard asSystemImage() == nil else {
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
