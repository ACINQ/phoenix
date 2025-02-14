/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public enum Permission: UInt8, CustomStringConvertible {
	case KEY_0 = 0
	case KEY_1 = 1
	case KEY_2 = 2
	case KEY_3 = 3
	case KEY_4 = 4
	case ALL   = 0xe
	case NONE  = 0xf
	
	init(from byte: UInt8) {
		switch byte {
			case Permission.KEY_0.rawValue : self = .KEY_0
			case Permission.KEY_1.rawValue : self = .KEY_1
			case Permission.KEY_2.rawValue : self = .KEY_2
			case Permission.KEY_3.rawValue : self = .KEY_3
			case Permission.KEY_4.rawValue : self = .KEY_4
			case Permission.ALL.rawValue   : self = .ALL
			case Permission.NONE.rawValue  : self = .NONE
			default                        : self = .NONE
		}
	}
	
	public var description: String {
		switch self {
			case .KEY_0 : return "Key 0"
			case .KEY_1 : return "Key 1"
			case .KEY_2 : return "Key 2"
			case .KEY_3 : return "Key 3"
			case .KEY_4 : return "Key 4"
			case .ALL   : return "All"
			case .NONE  : return "None"
		}
	}
}
