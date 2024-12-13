import Foundation
import CoreTransferable
import CryptoKit

enum HexOptions {
	case lowerCase
	case upperCase
	
	var formatString: String {
		switch self {
			case .lowerCase: return "%02hhx" // <- lowercase 'x'
			case .upperCase: return "%02hhX" // <- UPPERCASE 'X'
		}
	}
}

extension SHA256.Digest {
	
	func toHex(options: HexOptions = .lowerCase) -> String {
		return self.map { String(format: options.formatString, $0) }.joined()
	}
}

extension Array where Element == UInt8 {
	
	func toHex(options: HexOptions = .lowerCase) -> String {
		return self.map { String(format: options.formatString, $0) }.joined()
	}
}


extension Data {

	func toHex(options: HexOptions = .lowerCase) -> String {
		return self.map { String(format: options.formatString, $0) }.joined()
	}
	
	init?(fromHex string: String) {

		// Convert 0 ... 9, a ... f, A ...F to their decimal value,
		// return nil for all other input characters
		func decodeNibble(u: UInt16) -> UInt8? {
			switch(u) {
			case 0x30 ... 0x39:
				return UInt8(u - 0x30)
			case 0x41 ... 0x46:
				return UInt8(u - 0x41 + 10)
			case 0x61 ... 0x66:
				return UInt8(u - 0x61 + 10)
			default:
				return nil
			}
		}
		
		self.init(capacity: string.utf16.count/2)
		var even = true
		var byte: UInt8 = 0
		for c in string.utf16 {
			guard let val = decodeNibble(u: c) else { return nil }
			if even {
				byte = val << 4
			} else {
				byte += val
				self.append(byte)
			}
			even = !even
		}
		guard even else { return nil }
	}
}
