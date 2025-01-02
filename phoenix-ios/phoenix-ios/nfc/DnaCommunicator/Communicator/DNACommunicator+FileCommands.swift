/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

extension DnaCommunicator {
    
	func readFileData(
		fileNum : FileSpecifier,
		offset  : Int = 0,
		length  : Int,
		mode    : CommuncationMode
	) async -> Result<[UInt8], Error> {
		
		// Pg. 73
		let offsetBytes = Helper.byteArrayLE(from: Int32(offset))[0...2]
		let lengthBytes = Helper.byteArrayLE(from: Int32(length))[0...2] // <- Bug fix
		
		let result = await nxpSwitchedCommand(
			mode    : mode,
			command : 0xad,
			header  : [fileNum.rawValue] + offsetBytes + lengthBytes,
			data    : []
		)
		
		switch result {
		case .failure(let error):
			return .failure(error)
			
		case .success(let result):
			if let error = makeErrorIfNotExpectedStatus(result) {
				return .failure(error)
			} else {
				return .success(result.data)
			}
		}
	}
	
	func writeFileData(
		fileNum : FileSpecifier,
		offset  : Int = 0,
		data    : [UInt8],
		mode    : CommuncationMode
	) async -> Result<Void, Error> {
		
		// Pg. 75
		let dataSizeBytes = Helper.byteArrayLE(from: Int32(data.count))[0...2]
		let offsetBytes = Helper.byteArrayLE(from: Int32(offset))[0...2]
		
		let result = await nxpSwitchedCommand(
			mode    : mode,
			command : 0x8d,
			header  : [fileNum.rawValue] + offsetBytes + dataSizeBytes,
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
	
	func getFileSettings(
		fileNum: FileSpecifier
	) async -> Result<FileSettings, Error> {
		
		// Pg. 69
		let result = await nxpMacCommand(
			command : 0xf5,
			header  : [fileNum.rawValue],
			data    : []
		)
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			if let err = makeErrorIfNotExpectedStatus(result) {
				return .failure(err)
			}
			
			if let settings = FileSettings(data: result.data) {
				return .success(settings)
			} else {
				let err = Helper.makeError(110, "Invalid FileSettings response")
				return .failure(err)
			}
		}
	}
	
	func changeFileSettings(
		fileNum : FileSpecifier,
		data    : [UInt8]
	) async -> Result<Void, Error> {
		
		// Pg. 65
		let result = await nxpEncryptedCommand(
			command : 0x5f,
			header  : [fileNum.rawValue],
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
