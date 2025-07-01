import Foundation
import PhoenixShared

struct WalletIdentifier: Equatable {
	let chain: Bitcoin_kmpChain
	let nodeId: String
	let encryptedNodeId: String
	
	init(chain: Bitcoin_kmpChain, walletInfo: WalletManager.WalletInfo) {
		self.chain = chain
		self.nodeId = walletInfo.nodeIdString
		self.encryptedNodeId = walletInfo.encryptedNodeId
	}
	
	var keyId: String {
		if chain.isMainnet() {
			return encryptedNodeId
		} else {
			return "\(encryptedNodeId)-\(chain.phoenixName)"
		}
	}
}
