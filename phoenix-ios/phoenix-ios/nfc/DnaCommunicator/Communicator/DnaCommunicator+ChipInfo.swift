/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

extension DnaCommunicator {
	
	public func getChipUid() async -> Result<[UInt8], Error> {
		
		let result = await nxpEncryptedCommand(command: 0x51, header: [], data: [])
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			if let err = self.makeErrorIfNotExpectedStatus(result) {
				return .failure(err)
			} else {
				let uid = Array(result.data[0...6])
				return .success(uid)
			}
		}
	}
}
