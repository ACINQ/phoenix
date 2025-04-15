import Foundation

extension Array where Element == UInt8 {
	
	func readLittleEndian<T: FixedWidthInteger>(
		offset: Int,
		as: T.Type
	) -> T {
		
		assert(offset + MemoryLayout<T>.size <= self.count)
		
		// Prepare a region aligned for `T`
		var value: T = 0
		// Copy the misaligned bytes at `offset` to aligned region `value`
		_ = Swift.withUnsafeMutableBytes(of: &value) {valueBP in
			self.withUnsafeBytes { bufPtr in
				let range = offset..<offset+MemoryLayout<T>.size
				bufPtr.copyBytes(to: valueBP, from: range)
			}
		}
		
		return T(littleEndian: value)
	}
	
	func readBigEndian<T: FixedWidthInteger>(
		offset: Int,
		as: T.Type
	) -> T {
		
		assert(offset + MemoryLayout<T>.size <= self.count)
		
		// Prepare a region aligned for `T`
		var value: T = 0
		// Copy the misaligned bytes at `offset` to aligned region `value`
		_ = Swift.withUnsafeMutableBytes(of: &value) {valueBP in
			self.withUnsafeBytes { bufPtr in
				let range = offset..<offset+MemoryLayout<T>.size
				bufPtr.copyBytes(to: valueBP, from: range)
			}
		}
		
		return T(bigEndian: value)
	}
}
