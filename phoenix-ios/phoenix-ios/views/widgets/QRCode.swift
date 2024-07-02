import Foundation
import SwiftUI

class QRCode : ObservableObject {
	@Published var value: String? = nil
	@Published var cgImage: CGImage? = nil
	@Published var image: Image? = nil

	func clear() {
		self.value = nil
		self.cgImage = nil
		self.image = nil
	}
	
	func generate(value: String) {
		if value == self.value { return }
		self.value = value
		self.cgImage = nil
		self.image = nil

		DispatchQueue.global(qos: .userInitiated).async {
			let data = value.data(using: .ascii)
			guard let qrFilter = CIFilter(name: "CIQRCodeGenerator") else {
				fatalError("No CIQRCodeGenerator")
			}
			qrFilter.setValue(data, forKey: "inputMessage")
			let cgTransform = CGAffineTransform(scaleX: 8, y: 8)
			guard let ciImage = qrFilter.outputImage?.transformed(by: cgTransform) else {
				fatalError("Could not scale QRCode")
			}
			guard let cgImage = CIContext().createCGImage(ciImage, from: ciImage.extent) else {
				fatalError("Could not generate QRCode image")
			}
			let image = Image(decorative: cgImage, scale: 1.0)
			DispatchQueue.main.async {
				if value == self.value {
					self.cgImage = cgImage
					self.image = image
				} else {
					// Ignore: qrCode value has changed since we started image generation
				}
			}
		}
	}
}
