/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public enum CommuncationMode: UInt8 {
	case PLAIN = 0
	case MAC   = 1
	case FULL  = 3
}
