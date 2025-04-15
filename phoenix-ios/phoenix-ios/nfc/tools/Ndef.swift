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
		
		// NDEF Message header:
		
		let flags: NdefHeaderFlags = [.MB, .ME, .SR, .TNF_WELL_KNOWN]
		let type = NdefHeaderType.URL
		
		var messageHeader: [UInt8] = [
			flags.rawValue, // NDEF header flags
			0x01,           // Type length
			0x00,           // URL length placeholder
			type.rawValue   // Well-known type: URL
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
		var urlBytes =  urlData.toByteArray()
		
		let maxFileSize = 256 // As per NTAG 424 spcification for File #2
		let maxUrlSize = maxFileSize - (fileHeader.count + messageHeader.count + typeHeader.count)
		if urlBytes.count > maxUrlSize {
			urlBytes = Array(urlBytes[0..<maxUrlSize])
		}
		
		fileHeader[1] = UInt8(messageHeader.count + typeHeader.count + urlBytes.count)
		messageHeader[2] = UInt8(typeHeader.count + urlBytes.count)
		
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
		
		// NDEF Message header:
		
		let flags: NdefHeaderFlags = [.MB, .ME, .SR, .TNF_WELL_KNOWN]
		let type = NdefHeaderType.TEXT
		
		var messageHeader: [UInt8] = [
			flags.rawValue, // NDEF header flags
			0x01,           // Type length
			0x00,           // Text length placeholder
			type.rawValue   // Well-known type: TEXT
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
		var textBytes = textData.toByteArray()
		
		let maxFileSize = 256 // As per NTAG 424 spcification for File #2
		let maxTextSize = maxFileSize - (fileHeader.count + messageHeader.count + typeHeader.count)
		if textBytes.count > maxTextSize {
			textBytes = Array(textBytes[0..<maxTextSize])
		}
		
		fileHeader[1] = UInt8(messageHeader.count + typeHeader.count + textBytes.count)
		messageHeader[2] = UInt8(typeHeader.count + textBytes.count)
		
		let result: [UInt8] = fileHeader + messageHeader + typeHeader + textBytes
		return result
	}
	
	class func ndefDataForTemplate(_ template: Template) -> [UInt8] {
		
		switch template.value {
		case .Left(let url):
			return ndefDataForUrl(url)
		case .Right(let text):
			return ndefDataForText(text)
		}
	}
	
	struct Template {
		let value: Either<URL, String>
		let piccDataOffset: Int
		let cmacOffset: Int
		
		var valueString: String {
			switch value {
			case .Left(let url):
				return url.absoluteString
			case .Right(let text):
				return text
			}
		}
		
		init?(baseUrl: URL) {
			
			guard var comps = URLComponents(url: baseUrl, resolvingAgainstBaseURL: false) else {
				return nil
			}
			
			var queryItems = comps.queryItems ?? []
			
			// The `baseUrl` SHOULD NOT have either `picc_data` or `cmac` parameters.
			// But just to be safe, we'll remove them if they're present.
			//
			queryItems.removeAll(where: { item in
				let name = item.name.lowercased()
				return name == "picc_data" || name == "cmac"
			})
			
			// picc_data=(16_bytes_hexadecimal)
			// cmac=(8_bytes_hexadecimal)
			
			queryItems.append(URLQueryItem(name: "picc_data", value: "00000000000000000000000000000000"))
			queryItems.append(URLQueryItem(name: "cmac",      value: "0000000000000000"))
			
			comps.queryItems = queryItems
			
			guard let resolvedUrl = comps.url else {
				return nil
			}
			
			let urlString = resolvedUrl.absoluteString
			
			// Ultimately, the URL gets encoded as UTF-8,
			// and the offsets are used as indexes within this UTF-8 representation.
			//
			// So we need to do our calculations within the string's utf8View.
			
			let urlUtf8 = urlString.utf8
			
			guard let range1 = urlUtf8.ranges(of: "picc_data=".utf8).last else {
				return nil
			}
			let offset1 = urlUtf8.distance(from: urlUtf8.startIndex, to: range1.upperBound)
			
			guard let range2 = urlUtf8.ranges(of: "cmac=".utf8).last else {
				return nil
			}
			let offset2 = urlUtf8.distance(from: urlUtf8.startIndex, to: range2.upperBound)
			
			self.value = Either.Left(resolvedUrl)
			self.piccDataOffset = offset1 + Ndef.URL_HEADER_SIZE
			self.cmacOffset = offset2 + Ndef.URL_HEADER_SIZE
		}
		
		init(baseText: String) {
			
			// picc_data=(16_bytes_hexadecimal)
			// cmac=(8_bytes_hexadecimal)
			
			let fullText = "\(baseText)?picc_data=00000000000000000000000000000000&cmac=0000000000000000"
			//                         +123456789 123456789 123456789 123456789 123456789
			//                                    ^+11                                  ^+49
			
			// Ultimately, the value gets encoded as UTF-8,
			// and the offsets are used as indexes within this UTF-8 representation.
			//
			// So we need to do our calculations within the string's utf8View.
			
			let baseTextLength = baseText.utf8.count
			let offset1 = baseTextLength + 11
			let offset2 = baseTextLength + 49
			
			self.value = Either.Right(fullText)
			self.piccDataOffset = offset1 + Ndef.TEXT_HEADER_SIZE
			self.cmacOffset = offset2 + Ndef.TEXT_HEADER_SIZE
		}
		
	} // </struct Template>
}
