/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public class AESEncryptionMode : EncryptionMode {
	weak var communicator: DnaCommunicator?
	let sessionEncryptionKey: [UInt8]
	let sessionMacKey: [UInt8]

	static let encryptionPurpose: [UInt8] = [0xa5, 0x5a]
	static let macPurpose: [UInt8] = [0x5a, 0xa5]
	static let decryptionPurpose: [UInt8] = [0x5a, 0xa5]

	init(
		communicator : DnaCommunicator,
		key          : [UInt8],
		challengeA   : [UInt8],
		challengeB   : [UInt8]
	) {
		self.communicator = communicator
		self.sessionEncryptionKey = AESEncryptionMode.generateAESSessionKey(
			key: key,
			purpose: AESEncryptionMode.encryptionPurpose,
			challengeA: challengeA,
			challengeB: challengeB
		)
		self.sessionMacKey = AESEncryptionMode.generateAESSessionKey(
			key: key,
			purpose: AESEncryptionMode.macPurpose,
			challengeA: challengeA,
			challengeB: challengeB
		)

		if communicator.debug {
			Helper.logBytes("Session Enc Key: ", sessionEncryptionKey)
			Helper.logBytes("Session Mac Key: ", sessionMacKey)
		}
	}
	
	/// Used for unit testing & debugging
	///
	init(
		communicator: DnaCommunicator,
		sessionEncryptionKey: [UInt8],
		sessionMacKey: [UInt8]
	) {
		self.communicator = communicator
		self.sessionEncryptionKey = sessionEncryptionKey
		self.sessionMacKey = sessionMacKey

		if communicator.debug {
			Helper.logBytes("Session Enc Key: ", sessionEncryptionKey)
			Helper.logBytes("Session Mac Key: ", sessionMacKey)
		}
	}
    
	func generateIv(purpose: [UInt8]) -> [UInt8] {
		let iv = Helper.simpleAesEncrypt(key: sessionEncryptionKey, data: generateIvInput(purpose: purpose))
		if let dna = communicator, dna.debug {
			Helper.logBytes("IV", iv)
		}
		return iv
	}
    
	func generateIvInput(purpose:[UInt8]) -> [UInt8] {
		let ivInput: [UInt8] = [
			purpose[0],
			purpose[1],
			communicator!.activeTransactionIdentifier[0],
			communicator!.activeTransactionIdentifier[1],
			communicator!.activeTransactionIdentifier[2],
			communicator!.activeTransactionIdentifier[3],
			UInt8(communicator!.commandCounter % 256),
			UInt8(communicator!.commandCounter / 256),
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
		]
		if let dna = communicator, dna.debug {
			Helper.logBytes("IV Input", ivInput)
		}
		return ivInput
	}
    
	func encryptData(message: [UInt8]) -> [UInt8] {
		return Helper.simpleAesEncrypt(
			key: sessionEncryptionKey,
			data: Helper.messageWithPadding(message),
			iv: generateIv(purpose: AESEncryptionMode.encryptionPurpose)
		)
	}

	func decryptData(message: [UInt8]) -> [UInt8] {
		return Helper.simpleAesDecrypt(
			key: sessionEncryptionKey,
			data: message,
			iv: generateIv(purpose: AESEncryptionMode.decryptionPurpose)
		)
	}
    
	func generateMac(message: [UInt8]) -> [UInt8] {
		let fullMac = Helper.simpleCMAC(key: sessionMacKey, data: message)
		return Helper.evensOnly(fullMac)
	}
    
	static func generateAESSessionKey(
		key        : [UInt8],
		purpose    : [UInt8],
		challengeA : [UInt8],
		challengeB : [UInt8]
	) -> [UInt8] {
		let sessionVector = generateAESSessionVector(
			purpose: purpose,
			challengeA: challengeA,
			challengeB: challengeB
		)
		return Helper.simpleCMAC(key: key, data: sessionVector)
	}
    
	static func generateAESSessionVector(
		purpose    : [UInt8],
		challengeA : [UInt8],
		challengeB : [UInt8]
	) -> [UInt8] {
		let a: [UInt8] = challengeA.reversed()
		let b: [UInt8] = challengeB.reversed()
		let sessionVector: [UInt8] = [
			purpose[0],
			purpose[1],
			0x00, 0x01, // counter
			0x00, 0x80, // bits
			a[15], a[14],
			Helper.xor(a[13], b[15]),
			Helper.xor(a[12], b[14]),
			Helper.xor(a[11], b[13]),
			Helper.xor(a[10], b[12]),
			Helper.xor(a[9], b[11]),
			Helper.xor(a[8], b[10]),
			b[9], b[8], b[7], b[6], b[5], b[4], b[3], b[2], b[1], b[0],
			a[7], a[6], a[5], a[4], a[3], a[2], a[1], a[0]
		]
		return sessionVector
	}
}
