import Foundation

extension Data {
	enum HexOptions {
		case lowerCase
		case upperCase
	}

	func toHex(options: HexOptions = .lowerCase) -> String {
		let format = options == .upperCase ? "%02hhX" : "%02hhx"
		return map { String(format: format, $0) }.joined()
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
