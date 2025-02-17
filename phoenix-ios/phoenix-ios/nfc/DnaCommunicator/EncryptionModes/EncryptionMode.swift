/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

protocol EncryptionMode {
    func encryptData(message: [UInt8]) -> [UInt8]
    func decryptData(message: [UInt8]) -> [UInt8]
    func generateMac(message: [UInt8]) -> [UInt8]
}
