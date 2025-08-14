import Swift
import PhoenixShared

struct WalletMetadata: Comparable, Identifiable {
	
	let chain: Bitcoin_kmpChain
	let nodeId: String
	let name: String
	let photo: String
	let isHidden: Bool
	let isDefault: Bool
	
	private init(
		chain     : Bitcoin_kmpChain,
		nodeId    : String,
		name      : String,
		photo     : String,
		isHidden  : Bool,
		isDefault : Bool
	) {
		self.chain = chain
		self.nodeId = nodeId
		self.name = name
		self.photo = photo
		self.isHidden = isHidden
		self.isDefault = isDefault
	}
	
	init(wallet: SecurityFile.V1.Wallet, id: WalletIdentifier, isDefault: Bool) {
		self.chain = id.chain
		self.nodeId = id.nodeId
		self.name = wallet.name
		self.photo = wallet.photo
		self.isHidden = wallet.isHidden
		self.isDefault = isDefault
	}
	
	init(wallet: SecurityFile.V1.Wallet, keyInfo: SecurityFile.V1.KeyInfo, isDefault: Bool) {
		self.chain = keyInfo.chain
		self.nodeId = keyInfo.nodeId
		self.name = wallet.name
		self.photo = wallet.photo
		self.isHidden = wallet.isHidden
		self.isDefault = isDefault
	}
	
	var id: String {
		return keychainKeyId
	}
	
	var keychainKeyId: String {
		if chain.isMainnet() {
			return nodeId
		} else {
			return "\(nodeId)-\(chain.phoenixName)"
		}
	}
	
	static func < (lhs: WalletMetadata, rhs: WalletMetadata) -> Bool {
		
		let r1 = lhs.name.localizedCaseInsensitiveCompare(rhs.name)
		if r1 == .orderedAscending {
			return true
		} else if r1 == .orderedSame {
			
			let r2 = lhs.nodeId.compare(rhs.nodeId)
			if r2 == .orderedAscending {
				return true
			} else if r2 == .orderedSame {
				
				let r3 = lhs.chain.phoenixName.compare(rhs.chain.phoenixName)
				if r3 == .orderedAscending {
					return true
				} else {
					return false
				}
				
			} else {
				return false
			}
			
		} else {
			return false
		}
	}
	
	static func `default`() -> WalletMetadata {
		return WalletMetadata(
			chain    : Bitcoin_kmpChain.Mainnet(),
			nodeId   : "",
			name     : "?",
			photo    : WalletIcon.default.filename,
			isHidden : false,
			isDefault: false
		)
	}
}
