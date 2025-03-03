import Foundation
import SwCrypt

fileprivate let filename = "Ntag424"
#if DEBUG
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class Ntag424 {
	
	struct QueryItems {
		let piccData: Data
		let cmac: Data
		let encString: String?
	}
	
	enum QueryItemsError: Error {
		case piccDataMissing
		case piccDataInvalid
		case cmacMissing
		case cmacInvalid
	}
	
	static func extractQueryItems(
		url: URL
	) -> Result<QueryItems, QueryItemsError> {
	
		guard
			let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
			let queryItems = components.queryItems
		else {
			log.debug("extractQueryItems: failed: queryItems is nil")
			return .failure(.piccDataMissing)
		}
	
		var piccString: String? = nil
		var cmacString: String? = nil
		var encString: String? = nil
		
		for queryItem in queryItems {
			if queryItem.name.caseInsensitiveCompare("picc_data") == .orderedSame {
				piccString = queryItem.value
			} else if queryItem.name.caseInsensitiveCompare("cmac") == .orderedSame {
				cmacString = queryItem.value
			} else if queryItem.name.caseInsensitiveCompare("enc") == .orderedSame {
				encString = queryItem.value
			}
		}
	
		guard let piccString else {
			log.debug("extractQueryItems: failed: misssing 'picc_data' value")
			return .failure(.piccDataMissing)
		}
		
		guard let piccData = Data(fromHex: piccString) else {
			log.debug("extractQueryItems: failed: 'picc_data' string not hexadecimal")
			return .failure(.piccDataInvalid)
		}
		
		guard let cmacString else {
			log.debug("extractQueryItems: failed: misssing 'cmac' value")
			return .failure(.cmacMissing)
		}
		
		guard let cmacData = Data(fromHex: cmacString) else {
			log.debug("extractQueryItems: failed: 'cmac' string not hexadecimal")
			return .failure(.cmacInvalid)
		}
		
		return .success(QueryItems(piccData: piccData, cmac: cmacData, encString: encString))
	}
	
	struct KeySet {
		let piccDataKey: Data
		let cmacKey: Data
		
		static func `default`() -> KeySet {
			return KeySet(
				piccDataKey: Data(repeating: 0x00, count: 16),
				cmacKey: Data(repeating: 0x00, count: 16)
			)
		}
	}
	
	struct PiccDataInfo {
		let uid: Data       // 7 bytes
		let counter: UInt32 // 3 bytes (actual size in decrypted data)
		
		static let maxCounterValue: UInt32 = 0xffffff // 16,777,215 (it's only 3 bytes)
	}
	
	enum ExtractionError: Error {
		case decryptionFailed
		case cmacCalculationFailed
		case cmacMismatch
	}
	
	static func extractPiccDataInfo(
		piccData : Data,
		cmac     : Data,
		keySet   : KeySet
	) -> Result<PiccDataInfo, ExtractionError> {
		
		guard let tuple = decryptPiccData(piccData, keySet) else {
			log.debug("extractPiccData: failed: could not decrypt picc_data")
			return .failure(.decryptionFailed)
		}
		
		let decryptedPiccData = tuple.0
		let piccDataInfo = tuple.1
		
		guard let calculatedCmac = calculateCmac(decryptedPiccData, nil, keySet) else {
			log.debug("extractPiccData: failed: could not calculate cmac")
			return .failure(.cmacCalculationFailed)
		}
		
		guard calculatedCmac == cmac else {
			log.debug("extractPiccData: failed: calculated cmac does not match given cmac")
			return .failure(.cmacMismatch)
		}
		
		return .success(piccDataInfo)
	}
	
	private static func decryptPiccData(
		_ encryptedPiccData: Data,
		_ keySet: KeySet
	) -> (Data, PiccDataInfo)? {
		
		guard let decryptedPiccData = decrypt(data: encryptedPiccData, key: keySet.piccDataKey) else {
			log.debug("decryptPiccData: failed: cannot decrypt picc data")
			return nil
		}
		
		log.debug("decryptedPiccData: \(decryptedPiccData.toHex())")
		
		guard decryptedPiccData.count == 16 else {
			log.debug("decryptPiccData: failed: decrypted picc data not 16 bytes")
			return nil
		}
		
		let piccDataHeader: UInt8 = 0xc7
		guard decryptedPiccData[0] == piccDataHeader else {
			log.debug("decryptPiccData: failed: picc header missing")
			return nil
		}
		
		let uid: Data = decryptedPiccData[1..<8]
		log.debug("uid: \(uid.toHex())")
		
		var ctr: Data = decryptedPiccData[8..<11]
		log.debug("ctr: \(ctr.toHex())")
		
		var counter: UInt32 = 0
		ctr.append(contentsOf: [0x00])
		ctr.withUnsafeBytes { ptr in
			let littleEndian = ptr.load(as: UInt32.self)
			counter = UInt32(littleEndian: littleEndian)
		}
		
		log.debug("counter = \(counter)")
		
		let result = PiccDataInfo(uid: uid, counter: counter)
		return (decryptedPiccData, result)
	}
	
	private static func calculateCmac(
		_ decryptedPiccData: Data,
		_ encString: String?,
		_ keySet: KeySet
	) -> Data? {
		
		var inputA = Data()
		inputA.append(contentsOf: [0x3C, 0xC3, 0x00, 0x01, 0x00, 0x80])
		inputA += decryptedPiccData[1..<11]
		
		while (inputA.count % 16) != 0 {
			inputA.append(contentsOf: [0x00])
		}
		
		let resultA: Data = cmac(data: inputA, key: keySet.cmacKey)!
		log.debug("resultA: \(resultA.toHex(options: .upperCase))")
		
		var inputB = Data()
		if let encString {
			if let encData = encString.uppercased().data(using: .ascii) {
				inputB += encData
			}
			if let suffix = "&cmac=".data(using: .ascii) {
				inputB += suffix
			}
		}
		
		guard let resultB: Data = cmac(data: inputB, key: resultA) else {
			return nil
		}
		log.debug("resultB: \(resultB.toHex(options: .upperCase))")
		
		var truncated = Data()
		resultB.enumerated().forEach { (index, value) in
			if (index % 2) == 1 {
				truncated.append(contentsOf: [value])
			}
		}
		
		log.debug("calculatedCmac: \(truncated.toHex(options: .upperCase))")
		
		return truncated
	}
	
	private static func decrypt(
		data : Data,
		key  : Data
	) -> Data? {
		
		guard SwCrypt.CC.cryptorAvailable() else {
			log.error("CC.cryptorAvailable() == false")
			return nil
		}
		
		do {
			let result = try SwCrypt.CC.crypt(
				.decrypt,
				blockMode : .ecb,
				algorithm : .aes,
				padding   : .noPadding,
				data      : data,
				key       : key,
				iv        : Data(repeating: 0, count: 16) // not used in ECB mode
			)
			return result

		} catch {
			log.error("AES decrypt error: \(error)")
			return nil
		}
	}
	
	private static func cmac(
		data : Data,
		key  : Data
	) -> Data? {
		
		guard SwCrypt.CC.CMAC.available() else {
			log.error("CC.CMAC.available() == false")
			return nil
		}
		
		return SwCrypt.CC.CMAC.AESCMAC(data, key: key)
	}
}
