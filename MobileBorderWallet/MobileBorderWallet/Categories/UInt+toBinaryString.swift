import Foundation

extension UInt32 {
	func toBinaryString(minPrecision: Int = 32) -> String {
		return String(self, radix: 2).leftPadding(toLength: minPrecision, withPad: "0")
	}
}

extension UInt16 {
	func toBinaryString(minPrecision: Int = 16) -> String {
		return String(self, radix: 2).leftPadding(toLength: minPrecision, withPad: "0")
	}
}
