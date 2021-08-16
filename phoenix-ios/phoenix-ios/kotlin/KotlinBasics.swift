import Foundation
import PhoenixShared


extension KotlinByteArray {
	
	func toSwiftData() -> Data {

		let size = self.size
		var data = Data(count: Int(size))
		for idx in 0 ..< size {
			let byte: Int8 = self.get(index: idx)
			data[Int(idx)] = UInt8(bitPattern: byte)
		}
		return data
	}
}

extension Data {
	
	func toKotlinByteArray() -> KotlinByteArray {
		
		let result = KotlinByteArray(size: Int32(self.count))
		for (idx, byte) in self.enumerated() {
			result.set(index: Int32(idx), value: Int8(bitPattern: byte))
		}
		return result
	}
}
