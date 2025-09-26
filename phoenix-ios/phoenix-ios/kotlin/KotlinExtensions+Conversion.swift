import Foundation
import PhoenixShared

fileprivate let filename = "KotlinExtensions+Conversion"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension KotlinByteArray {
	
	func toByteVector() -> Bitcoin_kmpByteVector {
		return Bitcoin_kmpByteVector(bytes: self)
	}
	
	func toSwiftData() -> Data {
		
		return ByteArray_toNSData(buffer: self)
		
		// Do not use the below technique. It's extremely slow!
		// Up to 1.7 seconds to perform this on an image (iPhone 12 mini).
		// The better technique above allows us to perform a standard memcpy.
	 
	/*	let size = self.size
		var data = Data(count: Int(size))
		for idx in 0 ..< size {
			let byte: Int8 = self.get(index: idx)
			data[Int(idx)] = UInt8(bitPattern: byte)
		}
		return data
	*/
	}
}

extension Bitcoin_kmpByteVector {
	
	func toSwiftData() -> Data {
		
		return ByteArray_toNSData(buffer: self.toByteArray())
		
		//	Do not use the below technique. It's extremely slow!
		// Up to 1.7 seconds to perform this on an image (iPhone 12 mini).
		// The better technique above allows us to perform a standard memcpy.
	 
	/*	let size = self.size()
		var data = Data(count: Int(size))
		for idx in 0 ..< size {
			let byte: Int8 = self.get(i: idx)
			data[Int(idx)] = UInt8(bitPattern: byte)
		}
		return data
	*/
	}
}

extension Data {
	
	func toKotlinByteArray() -> KotlinByteArray {
		
		return NSData_toByteArray(data: self)
		
	//	Do not use the below technique. It's extremely slow!
	//	Up to 0.8 seconds to perform this on an image (iPhone 12 mini).
	//	It's more efficient to use the Kotlin implementation since it can perform a standard memcpy.
		
	/*	let startDate = Date.now
		let result = KotlinByteArray(size: Int32(self.count))
		for (idx, byte) in self.enumerated() {
			result.set(index: Int32(idx), value: Int8(bitPattern: byte))
		}
		let endDate = Date.now
		log.debug("Data.toKotlinByteArray(): \(endDate.timeIntervalSince(startDate)) seconds")
		return result
	*/
	}
}

extension Array {
	
	func toKotlinArray<Item: AnyObject>() -> KotlinArray<Item> {
		
		return KotlinArray(size: Int32(self.count)) { (i: KotlinInt) in
			var shutUpCompiler: Item? = nil
			shutUpCompiler = (self[i.intValue] as! Item)
			return shutUpCompiler
		}
	}
}
