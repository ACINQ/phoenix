/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public enum FileSpecifier: UInt8, CustomStringConvertible {
	case CC_FILE     = 1
	case NDEF_FILE   = 2
	case PROPRIETARY = 3
	
	public var description: String {
		switch self {
			case .CC_FILE     : return "CC File (#1)"
			case .NDEF_FILE   : return "NDEF File (#2)"
			case .PROPRIETARY : return "Proprietary File (#3)"
		}
	}
	
	public var fileSize: Int { // page 10
		switch self {
			case .CC_FILE     : return 32
			case .NDEF_FILE   : return 256
			case .PROPRIETARY : return 128
		}
	}
}
