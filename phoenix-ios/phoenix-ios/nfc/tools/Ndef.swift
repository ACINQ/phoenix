import Foundation

struct NdefHeaderFlags: OptionSet {
	let rawValue: UInt8
	
	/// Message begin flag
	static let MB = NdefHeaderFlags(rawValue: 0b10000000)
	/// Message end flag
	static let ME = NdefHeaderFlags(rawValue: 0b01000000)
	/// Chunked flag
	static let CF = NdefHeaderFlags(rawValue: 0b00100000)
	/// Short record flag
	static let SR = NdefHeaderFlags(rawValue: 0b00010000)
	/// IL (ID Length) is present
	static let IL = NdefHeaderFlags(rawValue: 0b00001000)
	
	/// Type Name Format options:
	static let TNF_WELL_KNOWN   = NdefHeaderFlags(rawValue: 0b00000001) // 0x01
	static let TNF_MIME         = NdefHeaderFlags(rawValue: 0b00000010) // 0x02
	/// Note: don't use this for URLS, use WELLKNOWN instead.
	static let TNF_ABSOLUTE_URI = NdefHeaderFlags(rawValue: 0b00000011) // 0x03
	static let TNF_EXTERNAL     = NdefHeaderFlags(rawValue: 0b00000100) // 0x04
	static let TNF_UNKNOWN      = NdefHeaderFlags(rawValue: 0b00000101) // 0x05
	static let TNF_UNCHANGED    = NdefHeaderFlags(rawValue: 0b00000110) // 0x06
	static let TNF_RESERVED     = NdefHeaderFlags(rawValue: 0b00000111) // 0x07
}

enum NdefHeaderType: UInt8 {
	case TEXT = 0x54 // 'T'.ascii
	case URL  = 0x55 // 'U'.ascii
}

public class Ndef {
	
	static let URL_HEADER_SIZE = 7
	static let TEXT_HEADER_SIZE = 9
	
	class func ndefDataForUrl(_ url: URL) -> [UInt8] {
		
		// See pgs. 30-31 of AN12196
		
		// NDEF File Format:
		//
		// Field        | Length     | Description
		// ---------------------------------------------------------------
		// NLEN         | 2 bytes    | Length of the NDEF message in big-endian format.
		// NDEF Message | NLEN bytes | NDEF message. See NFC Data Exchange Format (NDEF).
		//
		// https://docs.nordicsemi.com/bundle/ncs-latest/page/nrfxlib/nfc/doc/type_4_tag.html#t4t-format
		//
		var fileHeader: [UInt8] = [
			0x00, // Placeholder for NLEN
			0x00, // Placeholder for NLEN
		]
		
		// Header for: Well-known-type(URL)
		//
		// Note: If you have a long URL that doesn't fit, you can change the typeHeader here.
		// For example, if you specify 0x02 for the typeHeader, it means:
		// - prepend `https://www.` to the URL content, saving a few bytes.
		//
		let typeHeader: [UInt8] = [
			0x00 // Just the URI (no prepended protocol)
		]
		
		let urlData = url.absoluteString.data(using: .utf8) ?? Data()
		let urlBytes =  urlData.toByteArray()
		
		// NDEF Message header:
		
		let messageHeader: [UInt8]
		
		let fitsInShortRecord = (typeHeader.count + urlBytes.count) <= 255
		if fitsInShortRecord {
			
			let payloadLength = UInt8(typeHeader.count + urlBytes.count)
			
			let flags: NdefHeaderFlags = [.MB, .ME, .SR, .TNF_WELL_KNOWN]
			let type = NdefHeaderType.URL
			
			messageHeader = [
				flags.rawValue, // NDEF header flags
				0x01,           // Type length
				payloadLength,  // Payload length (SR = 1 byte)
				type.rawValue   // Well-known type: URL
			]
			
		} else {
			
			let payloadLengthLE = UInt32(typeHeader.count + urlBytes.count)
			let payloadLength: [UInt8] = payloadLengthLE.bigEndian.toByteArray()
			
			let flags: NdefHeaderFlags = [.MB, .ME, .TNF_WELL_KNOWN]
			let type = NdefHeaderType.URL
			
			messageHeader = [
				flags.rawValue,   // NDEF header flags
				0x01,             // Type length
				payloadLength[0], // Payload length (!SR = 4 bytes)
				payloadLength[1], // Payload length
				payloadLength[2], // Payload length
				payloadLength[3], // Payload length
				type.rawValue     // Well-known type: URL
			]
		}
		
		let fileLengthLE = UInt16(messageHeader.count + typeHeader.count + urlBytes.count)
		let fileLength: [UInt8] = fileLengthLE.bigEndian.toByteArray()
		
		fileHeader[0] = fileLength[0]
		fileHeader[1] = fileLength[1]
		
		let result: [UInt8] = fileHeader + messageHeader + typeHeader + urlBytes
		return result
	}
	
	class func ndefDataForText(_ text: String) -> [UInt8] {
		
		// See pgs. 30-31 of AN12196
		
		// NDEF File Format:
		//
		// Field        | Length     | Description
		// ---------------------------------------------------------------
		// NLEN         | 2 bytes    | Length of the NDEF message in big-endian format.
		// NDEF Message | NLEN bytes | NDEF message. See NFC Data Exchange Format (NDEF).
		//
		// https://docs.nordicsemi.com/bundle/ncs-latest/page/nrfxlib/nfc/doc/type_4_tag.html#t4t-format
		//
		var fileHeader: [UInt8] = [
			0x00, // Placeholder for NLEN
			0x00  // Placeholder for NLEN
		]
		
		// Header for: Well-known-type(TEXT)
		//
		// RTD TEXT specification:
		//
		// Byte 0 bit pattern:
		//
		// |     7    |    6     |   5, 4, 3, 2, 1, 0   |
		// ----------------------------------------------
		// | UTF 8/16 | Reserved | Language code length |
		//
		// UTF-8  => 0
		// UTF-16 => 1
		//
		// Reserved => must be 0
		//
		// Language code should use ISO/IANA language code.
		// We will use "en" - although for our use case it will be ignored.
		//
		// Thus our bit pattern is:
		// 0b00000010 = 0x02
		//
		let typeHeader: [UInt8] = [
			0x02, // UTF-8; langCode.length = 2
			0x65, // 'e'
			0x6e  // 'n'
		]
		
		let textData = text.data(using: .utf8) ?? Data()
		let textBytes = textData.toByteArray()
		
		// NDEF Message header:
		
		let messageHeader: [UInt8]
		
		let fitsInShortRecord = (typeHeader.count + textBytes.count) <= 255
		if fitsInShortRecord {
			
			let payloadLength = UInt8(typeHeader.count + textBytes.count)
			
			let flags: NdefHeaderFlags = [.MB, .ME, .SR, .TNF_WELL_KNOWN]
			let type = NdefHeaderType.TEXT
			
			messageHeader = [
				flags.rawValue, // NDEF header flags
				0x01,           // Type length
				payloadLength,  // Payload length (SR = 1 byte)
				type.rawValue   // Well-known type: TEXT
			]
			
		} else {
			
			let payloadLengthLE = UInt32(typeHeader.count + textBytes.count)
			let payloadLength: [UInt8] = payloadLengthLE.bigEndian.toByteArray()
			
			let flags: NdefHeaderFlags = [.MB, .ME, .TNF_WELL_KNOWN]
			let type = NdefHeaderType.URL
			
			messageHeader = [
				flags.rawValue,   // NDEF header flags
				0x01,             // Type length
				payloadLength[0], // Payload length (!SR = 4 bytes)
				payloadLength[1], // Payload length
				payloadLength[2], // Payload length
				payloadLength[3], // Payload length
				type.rawValue     // Well-known type: URL
			]
		}
		
		let fileLengthLE = UInt16(messageHeader.count + typeHeader.count + textBytes.count)
		let fileLength: [UInt8] = fileLengthLE.bigEndian.toByteArray()
		
		fileHeader[0] = fileLength[0]
		fileHeader[1] = fileLength[1]
		
		let result: [UInt8] = fileHeader + messageHeader + typeHeader + textBytes
		return result
	}
}
