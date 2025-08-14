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
	
//	var prefsKeyId: String {
//		// The UserDefaults system stores files in plaintext, and the corresponding
//		// plist file(s) may be accessible to other apps (especially on macOS).
//		// So we're using the encryptedNodeId here to avoid leaking this information.
//		
//		if chain.isMainnet() {
//			return encryptedNodeId
//		} else {
//			return "\(encryptedNodeId)-\(chain.phoenixName)"
//		}
//	}
	
	var standardKeyId: String {
		if chain.isMainnet() {
			return nodeIdHash
		} else {
			return "\(nodeIdHash)-\(chain.phoenixName)"
		}
	}
}
