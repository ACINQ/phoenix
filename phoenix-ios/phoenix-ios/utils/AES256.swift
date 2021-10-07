import Foundation
import CommonCrypto

struct AES256 {
	
	private var key: Data
	private var iv: Data
	
	public init(key: Data, iv: Data) throws {
		guard key.count == kCCKeySizeAES256 else {
			throw Error.badKeyLength
		}
		guard iv.count == kCCBlockSizeAES128 else {
			throw Error.badInputVectorLength
		}
		self.key = key
		self.iv = iv
	}
	
	enum Error: Swift.Error {
		case badKeyLength
		case badInputVectorLength
		case keyGeneration(status: Int)
		case cryptoFailed(status: CCCryptorStatus)
	}
	
	enum Padding {
		case None
		case PKCS7
	}
	
	func encrypt(_ digest: Data, padding: Padding = .PKCS7) throws -> Data {
		return try crypt(
			input: digest,
			operation: CCOperation(kCCEncrypt),
			options: padding == .PKCS7 ? CCOptions(kCCOptionPKCS7Padding) : CCOptions()
		)
	}
	
	func decrypt(_ encrypted: Data, padding: Padding = .PKCS7) throws -> Data {
		return try crypt(
			input: encrypted,
			operation: CCOperation(kCCDecrypt),
			options: padding == .PKCS7 ? CCOptions(kCCOptionPKCS7Padding) : CCOptions()
		)
	}
	
	private func crypt(
		input: Data,
		operation: CCOperation,
		options: CCOptions
	) throws -> Data {
		
		var outLength = Int(0)
		var outBytes = [UInt8](repeating: 0, count: input.count + kCCBlockSizeAES128)
		var status: CCCryptorStatus = CCCryptorStatus(kCCSuccess)
		input.withUnsafeBytes { (encryptedBytes: UnsafeRawBufferPointer) in
			iv.withUnsafeBytes { (ivBytes: UnsafeRawBufferPointer) in
				key.withUnsafeBytes { (keyBytes: UnsafeRawBufferPointer) in
					status = CCCrypt(operation,
					                 CCAlgorithm(kCCAlgorithmAES), // algorithm
					                 options,                      // options
					                 keyBytes.baseAddress,         // key: UnsafeRawPointer!
					                 key.count,                    // keylength
					                 ivBytes.baseAddress,          // iv: UnsafeRawPointer!
					                 encryptedBytes.baseAddress,   // dataIn: UnsafeRawPointer!
					                 input.count,                  // dataInLength
					                 &outBytes,                    // dataOut
					                 outBytes.count,               // dataOutAvailable
					                 &outLength)                   // dataOutMoved : UnsafeMutablePointer<Int>!
				}
			}
		}
		guard status == kCCSuccess else {
			throw Error.cryptoFailed(status: status)
		}
		return Data(bytes: outBytes, count: outLength)
	}
}
