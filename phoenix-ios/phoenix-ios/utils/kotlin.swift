import Foundation
import PhoenixShared

extension KotlinByteArray {
	
	static func fromSwiftData(_ data: Data) -> KotlinByteArray {
		
		let kba = KotlinByteArray(size: Int32(data.count))
		for (idx, byte) in data.enumerated() {
			kba.set(index: Int32(idx), value: Int8(bitPattern: byte))
		}
		return kba
	}
	
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

