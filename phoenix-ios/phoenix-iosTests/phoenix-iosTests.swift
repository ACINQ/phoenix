import XCTest
@testable import Phoenix
import SwiftUI

class appNameTests: XCTestCase {

	override func setUp() {
		// Put setup code here.
		// This method is called before the invocation of each test method in the class.
	}
	
	override func tearDown() {
		// Put teardown code here.
		// This method is called after the invocation of each test method in the class.
	}
	
	struct TestVectors {
		let key: Data
		let iv: Data
		let plaintext: Data
		let ciphertext: Data
	}
	
	func testVectors() -> TestVectors {
		/**
		 * Source:
		 * https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38a.pdf
		 * Section: F.2.5 - CBC-AES256.Encrypt
		 */
		
		let key = Data(fromHex: "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4")!
		let iv = Data(fromHex: "000102030405060708090a0b0c0d0e0f")!
		 
		let plaintext = Data(fromHex:
		  "6bc1bee22e409f96e93d7e117393172a" + // block 1
		  "ae2d8a571e03ac9c9eb76fac45af8e51" + // block 2
		  "30c81c46a35ce411e5fbc1191a0a52ef" + // block 3
		  "f69f2445df4f9b17ad2b417be66c3710")! // block 4
		
		let ciphertext = Data(fromHex:
		  "f58c4c04d6e5f1ba779eabfb5f7bfbd6" + // block 1
		  "9cfc4e967edb808d679f777bc6702c7d" + // block 2
		  "39f23369a9d9bacfa530e26304231461" + // block 3
		  "b2eb05e2c39be9fcda6c19078c6a9d1b")! // block 4
		
		return TestVectors(key: key, iv: iv, plaintext: plaintext, ciphertext: ciphertext)
	}
	
	func testEncryptionDecryption() {
		
		let v = testVectors()
		let aes = try? AES256(key: v.key, iv: v.iv)
		
		let encrypted = try? aes?.encrypt(v.plaintext, padding: .None)
		XCTAssert(encrypted == v.ciphertext)
		
		let decrypted = try? aes?.decrypt(v.ciphertext, padding: .None)
		XCTAssert(decrypted == v.plaintext)
	}
	
	func testPadding() {
		
		let v = testVectors()
		let aes = try? AES256(key: v.key, iv: v.iv)
		
		// We should be able to drop bytes from the end of the plaintext,
		// without experiencing any problems while encrypting / decrypting.
		//
		// That is, the PKCS7 padding should work properly.
		
		var length = v.plaintext.count
		while length > 2 {
			
			let plaintext = v.plaintext.prefix(length - 1)
			
			let encrypted = try? aes?.encrypt(plaintext, padding: .PKCS7)
			let decrypted = try? aes?.decrypt(encrypted!, padding: .PKCS7)
			
			XCTAssert(decrypted == plaintext)
			
			length -= 1
		}
	}
		
}
