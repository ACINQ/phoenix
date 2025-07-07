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
	
	var keychainKeyId: String {
		if chain.isMainnet() {
			return nodeId
		} else {
			return "\(nodeId)-\(chain.phoenixName)"
		}
	}
	
	var prefsKeyId: String {
		// The UserDefaults system stores files in plaintext, and the corresponding
		// plist file(s) may be accessible to other apps (especially on macOS).
		// So we're using the encryptedNodeId here to avoid leaking this information.
		
		if chain.isMainnet() {
			return encryptedNodeId
		} else {
			return "\(encryptedNodeId)-\(chain.phoenixName)"
		}
	}
}
