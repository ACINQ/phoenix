import Foundation
import CommonCrypto
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MobileBorderWalletUtils"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum SeedPhraseType: Int {
	case normal = 128
	case long   = 256
}

class MobileBorderWalletUtils {
	
	static let DEFAULT_ROUNDS: UInt32 = 2_000_000
	
	static func generateSalt() -> Data {
		
		let byteCount = 128 / 8 // 128 bits == 16 bytes

		var data = Data(count: byteCount)
		let _ = data.withUnsafeMutableBytes { (ptr: UnsafeMutableRawBufferPointer) in
			SecRandomCopyBytes(kSecRandomDefault, byteCount, ptr.baseAddress!)
		}
		return data
	}
	
	static func pbkdf2(userPattern: [DotPoint], salt: Data, rounds: UInt32 = DEFAULT_ROUNDS) -> Data {
		
		// Map the user's pattern to a string of the form:
		// "(x,y),(x,y)"
		//
		// The grid is numbered like this:
		// (0,0) (1,0) (2,0) ...
		// (0,1) (1,1) (2,1) ...
		// (0,2) (1,2) (2,2) ...
		//
		// The points must be sorted:
		// * first by y value
		// * second by x value
		//
		// In other words, from left to right, and top to bottom.
		// Thus: (0,0) < (1,0) < (0,1)
		
		let sortedPoints = userPattern
			.sorted()
			.map { $0.description }
			.joined(separator: ",")
			
		let saltLen = salt.count
		let passwordLen = sortedPoints.utf8.count
		
		let resultLen = 256 / 8 // 256 bits / 8 = 32 bytes
		var result = Data(count: resultLen)
		
		result.withUnsafeMutableBytes { (resultBufferPtr: UnsafeMutableRawBufferPointer) in
			sortedPoints.withCString { (passwordPtr: UnsafePointer<Int8>) in
				salt.withUnsafeBytes { (saltBufferPtr: UnsafeRawBufferPointer) in
					
					// Note: CChar is defined as: `typealias CChar = Int8`
					
					let resultPtr = resultBufferPtr.bindMemory(to: UInt8.self).baseAddress!
					let saltPtr = saltBufferPtr.bindMemory(to: UInt8.self).baseAddress!
					
					let algorithm = CCPBKDFAlgorithm(kCCPBKDF2)
					let pseudoRandomAlgorithm = CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256)
					
					CCKeyDerivationPBKDF(
						algorithm,             // algorithm     : CCPBKDFAlgorithm
						passwordPtr,           // password      : UnsafePointer<CChar>
						passwordLen,           // passwordLen   : Int
						saltPtr,               // salt          : UnsafePointer<UInt8>
						saltLen,               // saltLen       : Int
						pseudoRandomAlgorithm, // prf           : CCPseudoRandomAlgorithm
						rounds,                // rounds        : UInt32
						resultPtr,             // derivedKey    : UnsafeMutablePointer<UInt8>
						resultLen              // derivedKeyLen : Int
					)
				}
			}
		}
			
		return result
	}
	
	static func extractPoints(pbkdf2: Data, seedPhraseType: SeedPhraseType) -> [DotPoint] {
		
		var points: [DotPoint] = []
		pbkdf2.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
			
			let numPoints: Int
			switch seedPhraseType {
				case .normal : numPoints = 11
				case .long   : numPoints = 23
			}
			
			for index in 0..<numPoints {
				let location = extractLocation(ptr, index: index)
				let point = extractPoint(location)
				
				points.append(point)
			}
		}
		
		return points
	}
	
	private static func extractLocation(_ buffer: UnsafeRawBufferPointer, index: Int) -> UInt16 {
		
		// Within the large buffer (i.e. 256 bits), what are the bits we want to extract ?
		//
		// Buffer: 1100011110111110000100110000100011001010111001000011111100010111...
		//                   ^          ^
		//                   ^      bufferBitEndIdx = 20
		//             bufferBitStartIdx = 10
		
		let bufferBitStartIdx = index * 10
		let bufferBitEndIdx = bufferBitStartIdx + 10
		
		// In order to extract these bits, we're first going to extract 32 bits from the buffer.
		// We just need to make sure we don't "buffer overflow" when we're near the edges.
		//
		// Buffer: 1100011110111110000100110000100011001010111001000011111100010111...
		//                 ^                               ^
		//                 ^                      bufferByteEndOffset = 5
		//          bufferByteStartOffset = 1
		
		var bufferByteStartOffset = bufferBitStartIdx / 8
		var bufferByteEndOffset = (bufferBitEndIdx / 8) + (((bufferBitEndIdx % 8) == 0) ? 0 : 1)
		
		if bufferByteStartOffset + 4 < buffer.count {
			bufferByteEndOffset = bufferByteStartOffset + 4
		} else {
			bufferByteStartOffset = bufferByteEndOffset - 4
		}
		
		// Now we're ready to extract the 32 bits.
		//
		// chunk = 10111110 00010011 00001000 11001010
		// chunk = (  be  ) (  13  ) (  08  ) (  ca  )
		//
		// But we have to be careful with little-endian problems !
		//
		// In little-endian the above number is: 3389526974 (ca0813be)
		// In    BIG-endian the above number is: 3188918474 (be1308ca)
		//
		// Because in little-endian, the bytes are read in reverse order.
		// 10111110 00010011 00001000 11001010
		// ^^^^^^^^                   ^^^^^^^^
		// least significan byte      most significant byte
		//
		// That's not what we want, so we need to swap the bits back to big-endian.
		
		let chunkBitStartIdx = bufferByteStartOffset * 8
		let chunk_littleEndian = buffer.loadUnaligned(fromByteOffset: bufferByteStartOffset, as: UInt32.self)
		let chunk = CFSwapInt32HostToBig(chunk_littleEndian)
		
		// Now we want to extract just the correct bits from our chunk:
		//
		// 10111110000100110000100011001010
		//   ^^^^^^^^^^
		//
		// To achieve this we first "drop" the low bits by shifting to the right:
		// 00000000000000000000101111100001
		//                       ^^^^^^^^^^
		//
		// Then we drop the high bits:
		//   00000000000000000000101111100001
		// & 00000000000000000000001111111111
		//   --------------------------------
		// = 00000000000000000000001111100001
		//
		// And we're left with just our target bits.
		
		let dropLowBits = 32 - (bufferBitEndIdx - chunkBitStartIdx)
		var result = chunk >> dropLowBits
		
		let mask: UInt32 = ~(UInt32.max << 10)
		result = result & mask
		
		return UInt16(clamping: result)
	}
	
	private static func extractPoint(_ location: UInt16) -> DotPoint {
		
		// The point is expected to be 10 bits.
		// So if it has higher bits activated, it's a bug in `extractLocation`.
		precondition(location < 1024)
		
		// Example location:
		// 0000001111100001
		//       ^^^
		//        x ^^^^^^^
		//            y
		
		let x = location >> 7
		
		let mask = ~(UInt16.max << 7)
		let y = location & mask
		
		assert(x < 16)
		assert(y < 128)
		
		return DotPoint(x: x, y: y)
	}
	
	static func generateEntropyGridBackup(userPattern: [DotPoint], wallet: WalletInfo) -> EntropyGridBackup {
		
		// There are 2 different types of entropy grids we can create:
		//
		// A.) A standard entropy grid that contains all 2048 words in a random order
		// B.) A totally random grid, where each cell is created via random(0..<2048),
		//     and thus likely contains many duplicate words
		//
		// Which entropy grid we use depends on the given wallet.
		// If the wallet's seedPhrase contains duplicates, then we use B. Otherwise we use A.
		
		var entropyGrid: [UInt16]
		
		let seedPhraseHasDuplicateWords = Set(wallet.seedPhraseWords).count < wallet.seedPhraseWords.count
		if seedPhraseHasDuplicateWords {
			
			entropyGrid = Array()
			entropyGrid.reserveCapacity(2048)
			
			for _ in 0..<2048 {
				
				let randomWordIndex = UInt16.random(in: 0..<2048)
				entropyGrid.append(randomWordIndex)
			}
			
		} else {
			
			entropyGrid = Array(UInt16(0) ..< UInt16(2048))
			
			for idx in stride(from: 2047, to: 0, by: -1) {
				
				let swapIdx = Int.random(in: 0..<idx)
				if swapIdx != idx {
					
					entropyGrid.swapAt(idx, swapIdx)
				}
			}
		}
		
		// Map from the user's pattern to a list of points on the entropy grid.
		
		var salt: Data
		var entropyGridPoints: [DotPoint]
		repeat {
			
			salt = generateSalt()
			let hash = pbkdf2(userPattern: userPattern, salt: salt)
			entropyGridPoints = extractPoints(pbkdf2: hash, seedPhraseType: .normal)
			
			// We have to make sure there are no duplicate points in the output.
			// If there are, then we try again using a different salt.
			
			let hasDuplicatePoints = Set(entropyGridPoints).count < entropyGridPoints.count
			if !hasDuplicatePoints {
				break
			}
			
		} while true
		
		// Step 3: Ensure the proper words are in the proper places
		
		if seedPhraseHasDuplicateWords {
			
			// We're just plugging in the desired words into their correct locations,
			// and replacing whatever is there.
			
			for (seedPhraseIdx, point) in entropyGridPoints.enumerated() {
				
				let entropyGridIdx = (Int(point.y) * 16) + Int(point.x)
				entropyGrid[entropyGridIdx] = wallet.seedPhraseIndexes[seedPhraseIdx]
			}
			
		} else {
			
			// There are no duplicate words in the seed phrase.
			// There are no duplicate words in the entropyGrid.
			// And there are no duplicate points (generated from user's pattern).
			// So we can just need to perform 11 swaps.
			
			for (seedPhraseIdx, point) in entropyGridPoints.enumerated() {
				
				let seedPhraseIndex = wallet.seedPhraseIndexes[seedPhraseIdx]
				
				let sourceIdx = entropyGrid.firstIndex(of: seedPhraseIndex)!
				let targetIdx = (Int(point.y) * 16) + Int(point.x)
				
				entropyGrid.swapAt(sourceIdx, targetIdx)
			}
		}
		
		return EntropyGridBackup(
			entropyGrid: entropyGrid,
			bip39Language: wallet.lang,
			finalWordNumber: wallet.finalWordNumber,
			function: EntropyGridFunction(
				name: "pbkdf2-hmac-sha256",
				salt: salt.toHex(.lowerCase),
				rounds: MobileBorderWalletUtils.DEFAULT_ROUNDS
		 	)
		)
	}
	
	static func restoreFromBackup(userPattern: [DotPoint], backup: EntropyGridBackup) -> WalletInfo {
		
		let salt = Data(fromHex: backup.function.salt)!
		
		let hash = pbkdf2(userPattern: userPattern, salt: salt, rounds: backup.function.rounds)
		let points = extractPoints(pbkdf2: hash, seedPhraseType: .normal)
		
		var seedPhraseIndexes: [UInt16] = []
		for point in points {
			
			let index = (Int(point.y) * 16) + Int(point.x)
			let seedPhraseIndex = backup.entropyGrid[index]
			
			seedPhraseIndexes.append(seedPhraseIndex)
		}
		
		let seedPhraseWords = Bip39.seedPhraseWords(from: seedPhraseIndexes)
		
		return WalletInfo(
			seedPhraseWords: seedPhraseWords,
			seedPhraseIndexes: seedPhraseIndexes,
			finalWordNumber: backup.finalWordNumber
		)
	}
	
	// --------------------------------------------------
	// MARK: Unit Tests
	// --------------------------------------------------
	
	static func test_extractLocation(userPattern: [DotPoint], salt: Data, rounds: UInt32 = DEFAULT_ROUNDS) {
		log.debug("test_extractLocation()")
		
		log.debug("Salt: \(salt.toHex(.lowerCase))")
		
		log.debug("Calculating PBKDF2...")
		let hash = pbkdf2(userPattern: userPattern, salt: salt, rounds: rounds)
		
		hash.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
		
			var originalBitPattern: String = ""
			for offset in 0..<8 {
				let littleEndian = ptr.loadUnaligned(fromByteOffset: (offset*4), as: UInt32.self)
				let bigEndian = CFSwapInt32HostToBig(littleEndian)
				
				originalBitPattern += bigEndian.toBinaryString()
			}
			originalBitPattern = String(originalBitPattern.prefix(230))
			
			var extractedBitPattern: String = ""
			for index in 0..<23 {
				let bits = extractLocation(ptr, index: index)
	
				extractedBitPattern += bits.toBinaryString(minPrecision: 10)
			}
			
			if originalBitPattern == extractedBitPattern {
				log.debug("test_extractLocation: SUCCESS")
			} else {
				log.debug("test_extractLocation: FAILURE")
			}
		}
	}
	
	static func test_extractPoint(userPattern: [DotPoint], salt: Data, rounds: UInt32 = DEFAULT_ROUNDS) {
		log.debug("test_extractPoint()")
		
		log.debug("Salt: \(salt.toHex(.lowerCase))")
		
		log.debug("Calculating PBKDF2...")
		let hash = pbkdf2(userPattern: userPattern, salt: salt, rounds: rounds)
		
		hash.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
		
			var originalBitPattern: String = ""
			for offset in 0..<8 {
				let littleEndian = ptr.loadUnaligned(fromByteOffset: (offset*4), as: UInt32.self)
				let bigEndian = CFSwapInt32HostToBig(littleEndian)
				
				originalBitPattern += bigEndian.toBinaryString()
			}
			originalBitPattern = String(originalBitPattern.prefix(230))
			
			var extractedBitPattern: String = ""
			for index in 0..<23 {
				let bits = extractLocation(ptr, index: index)
				let point = extractPoint(bits)
				
				let x = UInt16(clamping: point.x).toBinaryString(minPrecision: 3)
				let y = UInt16(clamping: point.y).toBinaryString(minPrecision: 7)
				
				extractedBitPattern += x + y
			}
			
			if originalBitPattern == extractedBitPattern {
				log.debug("test_extractPoint: SUCCESS")
			} else {
				log.debug("test_extractPoint: FAILURE")
			}
		}
	}
	
	static func test_roundTrip1() {
		log.debug("test_roundTrip1()")
		
		let userPattern: [DotPoint] = [DotPoint(x: 0, y: 0)]
		
		let seedPhraseIndexes: [UInt16] = [0,1,2,3,4,5,6,7,8,9,10]
		let seedPhraseWords = Bip39.seedPhraseWords(from: seedPhraseIndexes)
		
		log.debug("seedPhraseWords: \(seedPhraseWords)")
		
		let wallet_original = WalletInfo(
			seedPhraseWords: seedPhraseWords,
			seedPhraseIndexes: seedPhraseIndexes,
			finalWordNumber: 0
		)
		
		let backup = generateEntropyGridBackup(userPattern: userPattern, wallet: wallet_original)
		let wallet_restored = restoreFromBackup(userPattern: userPattern, backup: backup)
		
		if wallet_original == wallet_restored {
			log.debug("test_roundTrip1: SUCCESS")
		} else {
			log.debug("test_roundTrip1: FAILURE")
		}
	}
}
