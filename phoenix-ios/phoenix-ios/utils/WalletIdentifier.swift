import Foundation
import PhoenixShared

struct WalletIdentifier: Equatable {
	let chain: Bitcoin_kmpChain
	let nodeIdHash: String
	let encryptedNodeId: String
	
	init(chain: Bitcoin_kmpChain, walletInfo: WalletManager.WalletInfo) {
		self.chain = chain
		self.nodeIdHash = walletInfo.nodeIdHash
		self.encryptedNodeId = walletInfo.encryptedNodeId
	}
	
	var standardKeyId: String {
		if chain.isMainnet() {
			return nodeIdHash
		} else {
			return "\(nodeIdHash)-\(chain.phoenixName)"
		}
	}
	
	var deprecatedKeyId: String {
		if chain.isMainnet() {
			return encryptedNodeId
		} else {
			return "\(encryptedNodeId)-\(chain.phoenixName)"
		}
	}
}
