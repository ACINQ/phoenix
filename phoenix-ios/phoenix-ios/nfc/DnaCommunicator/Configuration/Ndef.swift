/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

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
	
	static let HEADER_SIZE = 7
	
	class func ndefDataForUrl(url: URL) -> [UInt8] {
		
		// See pgs. 30-31 of AN12196
		
		let flags: NdefHeaderFlags = [.MB, .ME, .SR, .TNF_WELL_KNOWN]
		let type = NdefHeaderType.URL
		
		let header: [UInt8] = [
			0x00,           // Placeholder for data size (two bytes MSB)
			0x00,
			flags.rawValue, // NDEF header flags
			0x01,           // Length of "type" field
			0x00,           // URL size placeholder
			type.rawValue,  // This will be a URL record
			0x00            // Just the URI (no prepended protocol)
		]
		
		let urlData = url.absoluteString.data(using: .utf8) ?? Data()
		var urlBytes = Helper.bytesFromData(data: urlData)
		
		let maxUrlSize = 255 - header.count
		if urlBytes.count > maxUrlSize {
			urlBytes = Array(urlBytes[0..<maxUrlSize])
		}
		
		var result: [UInt8] = header + urlBytes
		result[1] = UInt8(result.count - 2)   // Length of everything that isn't the length
		result[4] = UInt8(urlBytes.count + 1) // Everything after type field
		
		return result
	}
	
	struct Template {
		let url: URL
		let piccDataOffset: Int
		let cmacOffset: Int
		
		var urlString: String {
			return url.absoluteString
		}
		
		var urlData: Data {
			return urlString.data(using: .utf8) ?? Data()
		}
		
		init?(baseUrl: URL) {
			
			guard var comps = URLComponents(url: baseUrl, resolvingAgainstBaseURL: false) else {
				return nil
			}
			
			var queryItems = comps.queryItems ?? []
			
			// The `baseUrl` should NOT have either `picc_data` or `cmac` parameters.
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
			
			self.url = resolvedUrl
			self.piccDataOffset = offset1 + Ndef.HEADER_SIZE
			self.cmacOffset = offset2 + Ndef.HEADER_SIZE
		}
	}
}
