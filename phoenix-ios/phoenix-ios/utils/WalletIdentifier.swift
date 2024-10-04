import Foundation
import PhoenixShared

struct WalletIdentifier {
	let chain: Bitcoin_kmpChain
	let encryptedNodeId: String
	
	init(chain: Bitcoin_kmpChain, encryptedNodeId: String) {
		self.chain = chain
		self.encryptedNodeId = encryptedNodeId
	}
	
	init(chain: Bitcoin_kmpChain, walletInfo: WalletManager.WalletInfo) {
		self.init(chain: chain, encryptedNodeId: walletInfo.encryptedNodeId)
	}
	
	var prefsKeySuffix: String {
		if chain.isMainnet() {
			return encryptedNodeId
		} else {
			return "\(encryptedNodeId)-\(chain.phoenixName)"
		}
	}
}
