import Foundation
import UIKit
import UniformTypeIdentifiers.UTType

fileprivate let filename = "NSItemProvider"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension NSItemProvider {
	
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
	
	func asyncLoadFileRepresentation(
		 for contentType: UTType,
		 openInPlace: Bool = false
	) async throws -> URL {
		
		return try await withCheckedThrowingContinuation { continuation in
			let _ = self.loadFileRepresentation(for: contentType, openInPlace: openInPlace) {
				(url: URL?, openedInPlace: Bool, error: (any Error)?) in
				
				if let error {
					continuation.resume(throwing: error)
				} else if let url {
					if openedInPlace {
						// Since file was successfully opened in place,
						// we should (as far as I understand) have access to the URL outside this block.
						continuation.resume(returning: url)
					} else {
						// We have to copy the file to a safe location
						// because it will be deleted when we return from this block.
						let tempDir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
						let fileName = UUID().uuidString
						let copyUrl = tempDir.appendingPathComponent(fileName, isDirectory: false)
						do {
							try FileManager.default.copyItem(at: url, to: copyUrl)
							continuation.resume(returning: copyUrl)
						} catch {
							continuation.resume(throwing: error)
						}
					}
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
