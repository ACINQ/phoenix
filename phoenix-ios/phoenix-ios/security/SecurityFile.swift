import Foundation
import PhoenixShared
import CryptoKit

/// Represents the "security.json" file, where we store the wrapped/encrypted seed.
///
/// Here's how it works:
/// The seed is wrapped/encrypted with a randomly generated key (called the locking key).
/// The lockingKey is then stored in the iOS keychain.
/// And the SecurityFile contains the parameters needed to reproduce the seed, given the lockingKey.
/// E.g. the salt, nonce, IV, tag, rounds ... whatever the specific crypto algorithm needs.
///
class SecurityFile {
	
	enum Version {
		case v0(file: SecurityFile.V0)
		case v1(file: SecurityFile.V1)
	}
	
	struct V0: Decodable {
		static let filename = "security.json"
		
		/// The "keychain" option represents the default security.
		let keychain: SealedBox_ChaChaPoly?
		
		/// In V0 we had a "biometrics" option, which represented advanced security.
		///
		/// It worked just like the "keychain" option, except the lockingKey (stored in
		/// the iOS keychain) was configured with access-control settings that required
		/// biometrics to unlock/access the keychain item.
		///
		/// Support for this option was removed because it blocked Phoenix's ability to
		/// properly accept payments in the background (via a notification-service-extension).
		///
		let biometrics: SealedBox_ChaChaPoly?
		
		init() {
			self.keychain = nil
			self.biometrics = nil
		}
		
		init(keychain: SealedBox_ChaChaPoly) {
			self.keychain = keychain
			self.biometrics = nil
		}
	}
	
	struct V1: Codable {
		static let filename = "security.v1.json"
		
		struct Wallet: Codable {
			let keychain: SealedBox_ChaChaPoly
			let name: String
			let photo: String
			
			private let hidden: Bool?
			var isHidden: Bool {
				return hidden ?? false
			}
			
			private let chainName: String?
			var chain: Bitcoin_kmpChain {
				if let chainName, let chain = Bitcoin_kmpChain.fromString(chainName) {
					return chain
				} else {
					return Bitcoin_kmpChain.Mainnet()
				}
			}
			
			init(
				keychain: SealedBox_ChaChaPoly,
				name: String,
				photo: String,
				isHidden: Bool,
				chain: Bitcoin_kmpChain?
			) {
				self.keychain = keychain
				self.name = name
				self.photo = photo
				self.hidden = isHidden ? true : nil
				self.chainName = chain?.phoenixName
			}
			
			func updated(
				name newName: String,
				photo newPhoto: String,
				isHidden newIsHidden: Bool
			) -> Wallet {
				return Wallet(
					keychain: self.keychain,
					name: newName,
					photo: newPhoto,
					isHidden: newIsHidden,
					chain: self.chain
				)
			}
		}
		
		private let wallets: [String: Wallet]
		private let defaultKeyList: String?
		
		private enum CodingKeys: String, CodingKey {
			case wallets
			case defaultKeyList = "defaultKey"
		}
		
		private init(wallets: [String: Wallet], defaultKeyList: String?) {
			self.wallets = wallets
			self.defaultKeyList = defaultKeyList
		}
		
		init() {
			self.wallets = [:]
			self.defaultKeyList = nil
		}
		
		init(wallet: Wallet, id: WalletIdentifiable) {
			let key = id.standardKeyId
			self.wallets = [key: wallet]
			self.defaultKeyList = key
		}
		
		func getWallet(_ id: String) -> Wallet? {
			return wallets[id]
		}
		
		func getWallet(_ id: WalletIdentifiable) -> Wallet? {
			return wallets[id.standardKeyId]
		}
		
		func matchingWallets(_ chain: Bitcoin_kmpChain) -> [String: Wallet] {
			return wallets.filter({ $0.value.chain == chain })
		}
		
		private var defaultKeys: [Bitcoin_kmpChain: String] {
			
			let allKeys: [String] = defaultKeyList?
				.split(separator: ",")
				.map { $0.trimmingCharacters(in: .alphanumerics.inverted) } ?? []
			
			var result: [Bitcoin_kmpChain: String] = [:]
			for key in allKeys {
				if let wallet = wallets[key] {
					if result[wallet.chain] == nil {
						result[wallet.chain] = key
					}
				}
			}
			
			return result
		}
		
		func isDefaultWalletId(_ id: WalletIdentifier) -> Bool {
			return defaultKeys[id.chain] == id.standardKeyId
		}
		
		func defaultWallet(_ chain: Bitcoin_kmpChain) -> (String, Wallet)? {
			
			guard let key = defaultKeys[chain], let wallet = wallets[key] else {
				return nil
			}
			return (key, wallet)
		}
		
		func copyAddingWallet(_ wallet: Wallet, id: WalletIdentifiable) -> V1 {
			
			var newWallets = self.wallets
			newWallets[id.standardKeyId] = wallet
			
			return V1(wallets: newWallets, defaultKeyList: self.defaultKeyList)
		}
		
		func copyRemovingWallet(_ id: WalletIdentifiable) -> V1 {
			
			let key = id.standardKeyId
			guard let wallet = wallets[key] else {
				return self
			}
			
			var newWallets = wallets
			newWallets.removeValue(forKey: key)
			
			var newDefaultKeys = defaultKeys
			if newDefaultKeys[wallet.chain] == key {
				newDefaultKeys[wallet.chain] = nil
			}
			
			let newDefaultKeyList: String = newDefaultKeys.values.joined(separator: ",")
			return V1(wallets: newWallets, defaultKeyList: newDefaultKeyList)
		}
		
		func copySettingDefaultWalletId(_ id: WalletIdentifier) -> V1 {
			
			var newDefaultKeys = defaultKeys
			newDefaultKeys[id.chain] = id.standardKeyId
			
			let newDefaultKeyList: String = newDefaultKeys.values.joined(separator: ",")
			return V1(wallets: self.wallets, defaultKeyList: newDefaultKeyList)
		}
		
		func copyClearingDefaultWalletId(_ chain: Bitcoin_kmpChain) -> V1 {
			
			var newDefaultKeys = defaultKeys
			newDefaultKeys[chain] = nil
			
			let newDefaultKeyList: String = newDefaultKeys.values.joined(separator: ",")
			return V1(wallets: self.wallets, defaultKeyList: newDefaultKeyList)
		}
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

/// Generic typed container.
/// Allows us to switch to alternative encryption schemes in the future, if needed.
///
protocol SealedBox_Any: Codable {
	var type: String { get }
}

/// ChaCha20-Poly1305 via Apple's CryptoKit
///
struct SealedBox_ChaChaPoly: SealedBox_Any, Codable {
	let type: String // "ChaCha20-Poly1305"
	let nonce: Data
	let ciphertext: Data
	let tag: Data
	
	init(_ raw: ChaChaPoly.SealedBox) {
		type = "ChaCha20-Poly1305"
		nonce = raw.nonce.dataRepresentation
		ciphertext = raw.ciphertext
		tag = raw.tag
	}
	
	func toRaw() throws -> ChaChaPoly.SealedBox {
		return try ChaChaPoly.SealedBox(
			nonce      : ChaChaPoly.Nonce(data: self.nonce),
			ciphertext : self.ciphertext,
			tag        : self.tag
		)
	}
}
