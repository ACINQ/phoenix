import Foundation

extension Array where Element == UInt8 {
	
	func toData() -> Data {
		return Data(bytes: self, count: self.count)
	}
}

extension Data {
	
	func toByteArray() -> [UInt8] {
		var buffer = [UInt8]()
		self.withUnsafeBytes {
			buffer.append(contentsOf: $0)
		}
		return buffer
	}
}

extension FixedWidthInteger {
	
	func toByteArray() -> [UInt8] {
		withUnsafeBytes(of: self, Array.init)
	}
}
