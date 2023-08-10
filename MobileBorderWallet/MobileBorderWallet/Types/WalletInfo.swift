import Foundation

struct WalletInfo: Equatable, Hashable {
	
	let seedPhraseWords: [String]   // length = 11 || 23
	let seedPhraseIndexes: [UInt16] // length = 11 || 23
	let finalWordNumber: UInt16
	let lang: String = "en"
}
