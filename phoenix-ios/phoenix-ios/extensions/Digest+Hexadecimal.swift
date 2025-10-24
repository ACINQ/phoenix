import Foundation
import CryptoKit
import DnaCommunicator

extension SHA256.Digest {
	
	func toHex(_ options: HexOptions = .lowerCase) -> String {
		return self.map { String(format: options.formatString, $0) }.joined()
	}
}
