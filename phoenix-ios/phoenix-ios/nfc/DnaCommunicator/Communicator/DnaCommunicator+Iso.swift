/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

extension DnaCommunicator {
	
	public func isoSelectFileByFileId(
		mode   : UInt8,
		fileId : Int
	) async -> Result<Void, Error> {
		
		let packet: [UInt8] = [
			0x00, // class
			0xa4, // ISOSelectFile
			0x00, // select by file identifier (1, 2, 3, and 4 have various meanings as well)
			0x0c, // Don't return FCI
			0x02, // Length of file identifier
			UInt8(fileId / 256),  // File identifier
			UInt8(fileId % 256),
			0x00 // Length of expected response
		]
		
		let result = await isoTransceive(packet: packet)
		
		switch result {
		case .success(_):
			return .success(())
			
		case .failure(let err):
			return .failure(err)
		}
	}
}
