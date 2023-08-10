import Foundation

struct EntropyGridFunction: Codable, Equatable, Hashable {
	let name: String
	let salt: String
	let rounds: UInt32
}

struct EntropyGridBackup: Codable, Equatable, Hashable {
	let entropyGrid: [UInt16]
	let bip39Language: String
	let finalWordNumber: UInt16
	let function: EntropyGridFunction
}

struct EntropyGridCloudBackup: Codable, Equatable, Hashable {
	let name: String
	let timestamp: Date
	let backup: EntropyGridBackup
}
