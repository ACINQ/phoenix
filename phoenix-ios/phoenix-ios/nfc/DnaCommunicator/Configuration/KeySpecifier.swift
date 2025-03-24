/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public enum KeySpecifier: UInt8, CaseIterable, CustomStringConvertible {
	case KEY_0 = 0
	case KEY_1 = 1
	case KEY_2 = 2
	case KEY_3 = 3
	case KEY_4 = 4
	
	func next() -> KeySpecifier? {
		var found = false
		for key in KeySpecifier.allCases {
			if key == self {
				found = true
			} else if found {
				return key
			}
		}
		return nil
	}
	
	func toPermission() -> Permission {
		switch self {
			case .KEY_0 : return Permission.KEY_0
			case .KEY_1 : return Permission.KEY_1
			case .KEY_2 : return Permission.KEY_2
			case .KEY_3 : return Permission.KEY_3
			case .KEY_4 : return Permission.KEY_4
		}
	}
	
	public var description: String {
		switch self {
			case .KEY_0 : return "Key 0"
			case .KEY_1 : return "Key 1"
			case .KEY_2 : return "Key 2"
			case .KEY_3 : return "Key 3"
			case .KEY_4 : return "Key 4"
		}
	}
}
