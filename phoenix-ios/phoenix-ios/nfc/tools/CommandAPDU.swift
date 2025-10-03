import Foundation
import CoreNFC

/// Parser for Command APDU (C-APDU)
/// Section 4.2.1
///
struct CommandAPDU: CustomStringConvertible {
	
	let raw: [UInt8]
	
	/// Class
	var cla : UInt8 { return raw[0] }
	
	/// Instruction code
	var ins : UInt8 { return raw[1] }
	
	/// Parameter 1
	var p1  : UInt8 { return raw[2] }
	
	/// Parameter 2
	var p2  : UInt8 { return raw[3] }
	
	/// Length command (length of data in command)
	var lc: UInt8? {
		return (raw.count >= 6) ? raw[4] : nil
	}
	
	/// Data of command
	var data: [UInt8]? {
		if let dataCount = lc {
			let dataEnd = 5 + Int(dataCount)
			return [UInt8](raw[5..<dataEnd])
		} else {
			return nil
		}
	}
	
	/// Length expected (num bytes expected in the data field of response)
	var le: UInt8? {
		if let dataCount = lc {
			let offset = 5 + Int(dataCount)
			return (raw.count >= offset) ? raw[offset] : nil
		} else if raw.count >= 5 {
			return raw[4]
		} else {
			return nil
		}
	}
	
	@available(iOS 17.4, *)
	init?(cmd: CardSession.APDU) {
		
		let bytes = cmd.payload.toByteArray()
		
		// 4 bytes are required minimum (CLA, INS, P1 & P2)
		guard bytes.count >= 4 else { return nil }
		
		if bytes.count >= 5 {
			// bytes[4] could be either Lc or Le field
			
			if bytes.count >= 6 {
				// Lc field is present, which means:
				// - data field must be present with length Lc
				let dataLength = bytes[4]
				let minLength = 5 + Int(dataLength)
				guard bytes.count >= minLength else { return nil }
			} else {
				// bytes[4] => Le field
			}
		}
		
		self.raw = bytes
	}
	
	var description: String {
		
		var desc = "CommandAPDU: "
		desc += "cla(\(String(format: "%02hhx", cla))) "
		desc += "ins(\(String(format: "%02hhx", ins))) "
		desc += "p1(\(String(format: "%02hhx", p1))) "
		desc += "p2(\(String(format: "%02hhx", p2)))"
		
		if let lc {
			desc += " lc(\(String(format: "%hhx", lc))"
		}
		if let data {
			desc += "\n - data: \(data.toHex(.lowerCase))"
		}
		if let le {
			desc += "\n - le(\(String(format: "%02hhx", le)))"
		}
		
		return desc
	}
	
	/// Section 5.4.2 - NDEF Tag Application Select Procedure
	///
	func isNdefTagApplicationSelect() -> Bool {
		let expectedPrefix: [UInt8] = [
			0x00, // CLA
			0xA4, // INS
			0x04, // P1  - Select by name
			0x00, // P2  - First or only occurrence
			0x07, // Lc
			0xD2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01
		]
		// I believe the `Le` field is optional
		
		return raw.starts(with: expectedPrefix)
	}
	
	/// Section 5.4.3 - Capability Container Select Procedure
	///
	func isCapabilityContainerSelect() -> Bool {
		let expectedPrefix: [UInt8] = [
			0x00, // CLA
			0xA4, // INS
			0x00, // P1  - Select by elementary file (EF)
			0x0c, // P2  - select by elementary file (EF)
			0x02, // Lc
			0xe1, 0x03 // File identifier (EF) of the capability container
		]
		// I believe the `Le` field is optional
		
		return raw.starts(with: expectedPrefix)
	}
	
	func asSelectFileCommand() -> SelectFileCommand? {
		// I think there's a typo in the docs.
		// - Table 13: p2 == 0x0C
		// - Table 14: p2 == 0x0C
		// - Table 19: p2 == 0x0C
		// - Table 20: p2 == 0x00 < !!!
		//
		// I'm not entirely sure it matters though.
		// But for now, I ensure that it's one of the two values.
		
		if cla == 0x00,
		   ins == 0xA4,
			p1 == 0x00, // Select by elementary file (EF)
			(p2 == 0x00 || p2 == 0x0C), // See note above
			lc == 0x02,
			let fileId = data
		{
			return SelectFileCommand(fileId: fileId)
		} else {
			return nil
		}
	}
	
	func asReadBinaryCommand() -> ReadBinaryCommand? {
		if cla == 0x00,
		   ins == 0xb0,
		   let length = le,
			length > 0
		{
			let offset = raw.readBigEndian(offset: 2, as: UInt16.self) // big endian ???
			return ReadBinaryCommand(offset: offset, length: length)
		} else {
			return nil
		}
	}
}

struct SelectFileCommand {
	let fileId: [UInt8]
}

struct ReadBinaryCommand {
	let offset: UInt16
	let length: UInt8
}
