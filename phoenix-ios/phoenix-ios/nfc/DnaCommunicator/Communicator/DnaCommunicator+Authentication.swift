/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

extension DnaCommunicator {
	
	public static var defaultKey: [UInt8] {
		return Array(repeating: 0, count: 16)
	}
	
	public func authenticateEV2First(
		keyNum  : KeySpecifier,
		keyData : [UInt8]
	) async -> Result<Void, Error> {
		
		guard keyData.count == 16 else {
			return .failure(Helper.makeError(400, "Parameter keyData must be 16 bytes long."))
		}
		
		// STAGE 1 Authentication (pg. 46)
		let outerResult = await nxpNativeCommand(
			command : 0x71,
			header  : [keyNum.rawValue, 0x00],
			data    : []
		)
		
		switch outerResult {
		case .failure(let err):
			self.debugPrint("Err: \(err)")
			return .failure(err)
			
		case .success(let result):
			
			if (result.statusMajor != 0x91) {
				self.debugPrint("Authentication: Stage 1: Bad status major: \(result.statusMajor)")
				return .failure(Helper.makeError(103, "Bad status major: \(result.statusMajor)"))
			}
			
			if (result.statusMinor == 0xad) {
				self.debugPrint("Authentication: Stage 1: Requested retry")
				// Unsure - retry? pg. 52
				return .failure(Helper.makeError(104, "Don't know how to handle retries"))
			}
			
			if (result.statusMinor != 0xaf) {
				self.debugPrint("Authentication: Stage 1: Bad status minor: \(result.statusMinor)")
				return .failure(Helper.makeError(105, "Bad status minor: \(result.statusMinor)"))
			}
			
			if (result.data.count != 16) {
				self.debugPrint("Authentication: Stage 1: Incorrect data count")
				return .failure(Helper.makeError(106, "Incorrect data size"))
			}
			
			let encryptedChallengeB = result.data
			let challengeB = Helper.simpleAesDecrypt(key: keyData, data: encryptedChallengeB)
			let challengeBPrime = Helper.rotateLeft(Array(challengeB[0...]))
			let challengeA = Helper.randomBytes(ofLength: 16)
			self.debugPrint("Challenge A: \(challengeA)")
			let combinedChallenge = Helper.simpleAesEncrypt(key: keyData, data: (challengeA + challengeBPrime))
			
			// STAGE 2 (pg. 47)
			let innerResult = await self.nxpNativeCommand(
				command : 0xaf,
				header  : combinedChallenge,
				data    : nil
			)
			
			// {innerResult, err in
			
			switch innerResult {
			case .failure(let error):
				return .failure(error)
				
			case .success(let result):
				
				if (result.statusMajor != 0x91) {
					self.debugPrint("Authentication: Stage 2: Bad status major: \(result.statusMajor)")
					return .failure(Helper.makeError(107, "Bad status major: \(result.statusMajor)"))
				}
				
				if (result.statusMinor != 0x00) {
					self.debugPrint("Authentication: Stage 2: Bad status minor: \(result.statusMinor)")
					return .failure(Helper.makeError(108, "Bad status minor: \(result.statusMinor)"))
				}
				
				let resultData = Helper.simpleAesDecrypt(key: keyData, data: result.data)
				let ti = Array(resultData[0...3])
				let challengeAPrime = Array(resultData[4...19])
				let pdCap = resultData[20...25]
				let pcCap = resultData[26...31]
				let newChallengeA = Helper.rotateRight(challengeAPrime)
				
				if !newChallengeA.elementsEqual(challengeA) {
					self.debugPrint("Challenge A response not valid")
					return .failure(Helper.makeError(109, "Invalid Challenge A response"))
				}
				
				self.debugPrint("Data: TI: \(ti), challengeA: \(newChallengeA), pdCap: \(pdCap), pcCap: \(pcCap)")
				
				// Activate Session
				self.activeKeyNumber = keyNum
				self.commandCounter = 0
				self.activeTransactionIdentifier = ti
				
				self.debugPrint("Starting AES encryption")
				self.sessionEncryptionMode = AESEncryptionMode(
					communicator : self,
					key          : keyData,
					challengeA   : challengeA,
					challengeB   : challengeB
				)
				
				return .success(())
			}
		}
	}
}
