/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

extension DnaCommunicator {

	public func getKeyVersion(
		keyNum: KeySpecifier
	) async -> Result<UInt8, Error> {
		
		let result = await nxpMacCommand(
			command : 0x64,
			header  : [keyNum.rawValue],
			data    : nil
		)
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			if let err = makeErrorIfNotExpectedStatus(result) {
				return .failure(err)
			} else {
				let resultValue = result.data.count < 1 ? 0 : result.data[0]
				return .success(resultValue)
			}
		}
	}
	
	public func changeKey(
		keyNum     : KeySpecifier,
		oldKey     : [UInt8],
		newKey     : [UInt8],
		keyVersion : UInt8
	) async -> Result<Void, Error> {
		
		if activeKeyNumber != .KEY_0 {
			debugPrint(
				"""
				Not sure if changing keys when not authenticated as key0 is allowed -\
				documentation is unclear
				"""
			)
		}
		
		var data: [UInt8] = []
		if (keyNum == .KEY_0) {
			// If we are changing key0, can just send the request
			data = newKey + [keyVersion]
			
		} else {
			// Weird validation methodology
			let crc = Helper.crc32(newKey)
			let xorkey = Helper.xor(oldKey, newKey)
			data = xorkey + [keyVersion] + crc
		}
		
		let result = await nxpEncryptedCommand(
			command : 0xc4,
			header  : [keyNum.rawValue],
			data    : data
		)
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			if let err = makeErrorIfNotExpectedStatus(result) {
				return .failure(err)
			} else {
				return .success(())
			}
		}
	}
}
