import Foundation
import UIKit
import UniformTypeIdentifiers.UTType

extension NSItemProvider {
	
	@available(iOS 16.0, *)
	func asyncLoadDataRepresentation(
		 for contentType: UTType
	) async throws -> Data {
		
		return try await withCheckedThrowingContinuation { continuation in
			let _ = self.loadDataRepresentation(for: contentType) { data, error in
				if let error {
					continuation.resume(throwing: error)
				} else if let data {
					continuation.resume(returning: data)
				} else {
					preconditionFailure("NSItemProvider.loadDataRepresentation: failed API contract")
				}
			}
		}
	}
	
	@available(iOS 16.0, *)
	func asyncLoadFileRepresentation(
		 for contentType: UTType,
		 openInPlace: Bool = false
	) async throws -> URL {
		
		return try await withCheckedThrowingContinuation { continuation in
			let _ = self.loadFileRepresentation(for: contentType, openInPlace: openInPlace) { url, _, error in
				if let error {
					continuation.resume(throwing: error)
				} else if let url {
					continuation.resume(returning: url)
				} else {
					preconditionFailure("NSItemProvider.loadFileRepresentation: failed API contract")
				}
			}
		}
	}
	
	func asyncLoadImage() async throws -> UIImage? {
		
		return try await withCheckedThrowingContinuation { continuation in
			guard self.canLoadObject(ofClass: UIImage.self) else {
				continuation.resume(returning: nil)
				return
			}
			let _ = self.loadObject(ofClass: UIImage.self) { image, error in
				if let error {
					continuation.resume(throwing: error)
				} else if let image = image as? UIImage {
					continuation.resume(returning: image)
				} else {
					preconditionFailure("NSItemProvider.loadObject: failed API contract")
				}
			}
		}
	}
}
