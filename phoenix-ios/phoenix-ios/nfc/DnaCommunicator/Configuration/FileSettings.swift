/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

public enum FileSettingsEncodingError: Error {
	case sdmUidOffsetRequired
	case sdmReadCounterOffsetRequired
	case sdmPiccDataOffsetRequired
	case sdmMacInputOffsetRequired
	case sdmEncOffsetRequired
	case sdmEncLengthRequired
	case sdmMacOffsetRequired
	case sdmReadCounterLimitRequired
}

public struct FileSettings {
	
	static let minByteCount: Int = 7
	
	var fileType: UInt8 = 0
	var sdmEnabled: Bool = false
	var communicationMode: CommuncationMode = .PLAIN
	var readPermission: Permission = .NONE
	var writePermission: Permission = .NONE
	var readWritePermission: Permission = .NONE
	var changePermission: Permission = .NONE
	var fileSize: Int = 0
	var sdmOptionUid: Bool = false
	var sdmOptionReadCounter: Bool = false
	var sdmOptionReadCounterLimit: Bool = false
	var sdmOptionEncryptFileData: Bool = false
	var sdmOptionUseAscii: Bool = false
	var sdmMetaReadPermission: Permission = .NONE
	var sdmFileReadPermission: Permission = .NONE
	var sdmReadCounterRetrievalPermission: Permission = .NONE
	var sdmUidOffset: Int?
	var sdmReadCounterOffset: Int?
	var sdmPiccDataOffset: Int?
	var sdmMacInputOffset: Int?
	var sdmMacOffset: Int?
	var sdmEncOffset: Int?
	var sdmEncLength: Int?
	var sdmReadCounterLimit: Int?
	
	init() {}
	
	init?(data: [UInt8]) {
		// Pg. 13
		
		guard data.count >= FileSettings.minByteCount else { return nil }
		
		self.fileType = data[0]
		let options = data[1]
		self.sdmEnabled = Helper.getBitLSB(options, 6)
		
		if Helper.getBitLSB(options, 0) {
			if Helper.getBitLSB(options, 1) {
				self.communicationMode = .FULL
			} else {
				self.communicationMode = .MAC
			}
		}
		
		readPermission = Permission(from: Helper.leftNibble(data[3]))
		writePermission = Permission(from: Helper.rightNibble(data[3]))
		readWritePermission = Permission(from: Helper.leftNibble(data[2]))
		changePermission = Permission(from: Helper.rightNibble(data[2]))
		
		fileSize = Helper.bytesToIntLE(Array(data[4...6]))
		
		var currentOffset = 7
		
		if sdmEnabled {
			
			guard data.count >= (currentOffset + 3) else { return nil }
			
			let sdmOptions = data[currentOffset]
			currentOffset += 1
			 
			sdmOptionUid = Helper.getBitLSB(sdmOptions, 7)
			sdmOptionReadCounter = Helper.getBitLSB(sdmOptions, 6)
			sdmOptionReadCounterLimit = Helper.getBitLSB(sdmOptions, 5)
			sdmOptionEncryptFileData = Helper.getBitLSB(sdmOptions, 4)
			sdmOptionUseAscii = Helper.getBitLSB(sdmOptions, 0)
			
			let sdmAccessRights1 = data[currentOffset]
			currentOffset += 1
			let sdmAccessRights2 = data[currentOffset]
			currentOffset += 1
			sdmMetaReadPermission = Permission(from: Helper.leftNibble(sdmAccessRights2))
			sdmFileReadPermission = Permission(from: Helper.rightNibble(sdmAccessRights2))
			sdmReadCounterRetrievalPermission = Permission(from: Helper.rightNibble(sdmAccessRights1))
			 
			if sdmMetaReadPermission == .ALL {
				if sdmOptionUid {
					guard data.count >= (currentOffset + 3) else { return nil }
					sdmUidOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset + 2)]))
					currentOffset += 3
				}
				if sdmOptionReadCounter {
					guard data.count >= (currentOffset + 3) else { return nil }
					sdmReadCounterOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset + 2)]))
					currentOffset += 3
				}
			} else if sdmMetaReadPermission != .NONE {
				guard data.count >= (currentOffset + 3) else { return nil }
				sdmPiccDataOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset + 2)]))
				currentOffset += 3
			}

			if sdmFileReadPermission != .NONE {
				guard data.count >= (currentOffset + 3) else { return nil }
				sdmMacInputOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset + 2)]))
				currentOffset += 3
				
				if sdmOptionEncryptFileData {
					guard data.count >= (currentOffset + 6) else { return nil }
					sdmEncOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset+2)]))
					currentOffset += 3
					sdmEncLength = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset+2)]))
					currentOffset += 3
				}
				
				guard data.count >= (currentOffset + 3) else { return nil }
				sdmMacOffset = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset+2)]))
				currentOffset += 3
			}

			if sdmOptionReadCounterLimit {
				guard data.count >= (currentOffset + 3) else { return nil }
				sdmReadCounterLimit = Helper.bytesToIntLE(Array(data[currentOffset...(currentOffset+2)]))
				currentOffset += 3
			}
		}
	}
	
	enum EncodingMode {
		case GetFileSettings
		case ChangeFileSettings
	}
	
	func encode(mode: EncodingMode = .ChangeFileSettings) -> Result<[UInt8], FileSettingsEncodingError> {
		
		var buffer: [UInt8] = Array<UInt8>()
		
		if mode == .GetFileSettings {
			buffer.append(fileType)
		}
		
		do { // File Options
			
			let maskA: UInt8 = sdmEnabled ? 0b01000000 : 0b00000000
			
			let maskB: UInt8
			switch communicationMode {
				case .PLAIN : maskB = 0b00000000
				case .MAC   : maskB = 0b00000001
				case .FULL  : maskB = 0b00000011
			}
			
			let fileOptions: UInt8 = maskA | maskB
			buffer.append(fileOptions)
		}
		do { // Access Rights
			
			let byteA: UInt8 = readWritePermission.rawValue << 4 | changePermission.rawValue
			let byteB: UInt8 = readPermission.rawValue << 4 | writePermission.rawValue
			
			buffer.append(byteA)
			buffer.append(byteB)
		}
		if mode == .GetFileSettings { // File Size
			
			let bytes = Helper.byteArrayLE(from: fileSize)[0...2]
			buffer.append(contentsOf: bytes)
			
		}
		if sdmEnabled {
			
			do { // SDM Options
				
				let maskA: UInt8 = sdmOptionUid              ? 0b10000000 : 0b00000000 // bit 7
				let maskB: UInt8 = sdmOptionReadCounter      ? 0b01000000 : 0b00000000 // bit 6
				let maskC: UInt8 = sdmOptionReadCounterLimit ? 0b00100000 : 0b00000000 // bit 5
				let maskD: UInt8 = sdmOptionEncryptFileData  ? 0b00010000 : 0b00000000 // bit 4
				let maskE: UInt8 = sdmOptionUseAscii         ? 0b00000001 : 0b00000000 // bit 0
				
				let options: UInt8 = maskA | maskB | maskC | maskD | maskE
				buffer.append(options)
			}
			do { // SDM Access Rights
				
				let byteA: UInt8 = 0xF << 4 | sdmReadCounterRetrievalPermission.rawValue
				let byteB: UInt8 = sdmMetaReadPermission.rawValue << 4 | sdmFileReadPermission.rawValue
				
				buffer.append(byteA)
				buffer.append(byteB)
			}
			
			if sdmMetaReadPermission == .ALL {
				if sdmOptionUid {
					if let sdmUidOffset {
						let bytes = Helper.byteArrayLE(from: sdmUidOffset)[0...2]
						buffer.append(contentsOf: bytes)
					} else {
						return .failure(.sdmUidOffsetRequired)
					}
				}
				if sdmOptionReadCounter {
					if let sdmReadCounterOffset {
						let bytes = Helper.byteArrayLE(from: sdmReadCounterOffset)[0...2]
						buffer.append(contentsOf: bytes)
					} else {
						return .failure(.sdmReadCounterOffsetRequired)
					}
				}
			} else if sdmMetaReadPermission != .NONE {
				if let sdmPiccDataOffset {
					let bytes = Helper.byteArrayLE(from: sdmPiccDataOffset)[0...2]
					buffer.append(contentsOf: bytes)
				} else {
					return .failure(.sdmPiccDataOffsetRequired)
				}
			}
			
			if sdmFileReadPermission != .NONE {
				if let sdmMacInputOffset {
					let bytes = Helper.byteArrayLE(from: sdmMacInputOffset)[0...2]
					buffer.append(contentsOf: bytes)
				} else {
					return .failure(.sdmMacInputOffsetRequired)
				}
				
				if sdmOptionEncryptFileData {
					if let sdmEncOffset {
						let bytes = Helper.byteArrayLE(from: sdmEncOffset)[0...2]
						buffer.append(contentsOf: bytes)
					} else {
						return .failure(.sdmEncOffsetRequired)
					}
					
					if let sdmEncLength {
						let bytes = Helper.byteArrayLE(from: sdmEncLength)[0...2]
						buffer.append(contentsOf: bytes)
					} else {
						return .failure(.sdmEncLengthRequired)
					}
				}
				
				if let sdmMacOffset {
					let bytes = Helper.byteArrayLE(from: sdmMacOffset)[0...2]
					buffer.append(contentsOf: bytes)
				} else {
					return .failure(.sdmMacOffsetRequired)
				}
			}
			
			if sdmOptionReadCounterLimit {
				if let sdmReadCounterLimit {
					let bytes = Helper.byteArrayLE(from: sdmReadCounterLimit)[0...2]
					buffer.append(contentsOf: bytes)
				} else {
					return .failure(.sdmReadCounterLimitRequired)
				}
			}
		}
		
		return .success(buffer)
	}
	
	static func defaultFile1() -> FileSettings {
		
		var settings = FileSettings()
		settings.readPermission = .ALL
		settings.writePermission = .KEY_0
		settings.readWritePermission = .KEY_0
		settings.changePermission = .KEY_0
		settings.fileSize = 32
		
		return settings
	}
	
	static func defaultFile2() -> FileSettings {
		
		var settings = FileSettings()
		settings.readPermission = .ALL
		settings.writePermission = .ALL
		settings.readWritePermission = .ALL
		settings.changePermission = .KEY_0
		settings.fileSize = 256
		
		return settings
	}
	
	static func defaultFile3() -> FileSettings {
		
		var settings = FileSettings()
		settings.communicationMode = .FULL
		settings.readPermission = .KEY_2
		settings.writePermission = .KEY_3
		settings.readWritePermission = .KEY_3
		settings.changePermission = .KEY_0
		settings.fileSize = 128
		
		return settings
	}
}
