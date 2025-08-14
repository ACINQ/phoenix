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
		let keychain: KeyInfo_ChaChaPoly?
		
		/// In V0 we had a "biometrics" option, which represented advanced security.
		///
		/// It worked just like the "keychain" option, except the lockingKey (stored in
		/// the iOS keychain) was configured with access-control settings that required
		/// biometrics to unlock/access the keychain item.
		///
		/// Support for this option was removed because it blocked Phoenix's ability to
		/// properly accept payments in the background (via a notification-service-extension).
		///
		let biometrics: KeyInfo_ChaChaPoly?
		
		init() {
			self.keychain = nil
			self.biometrics = nil
		}
		
		init(keychain: KeyInfo_ChaChaPoly) {
			self.keychain = keychain
			self.biometrics = nil
		}
	}
	
	struct V1: Codable {
		static let filename = "security.v1.json"
		
		struct Wallet: Codable {
			let keychain: KeyInfo_ChaChaPoly
			let name: String
			let photo: String
			
			private let hidden: Bool?
			var isHidden: Bool {
				return hidden ?? false
			}
			
			init(keychain: KeyInfo_ChaChaPoly, name: String, photo: String, isHidden: Bool) {
				self.keychain = keychain
				self.name = name
				self.photo = photo
				self.hidden = isHidden ? true : nil
			}
			
			func updated(
				name newName: String,
				photo newPhoto: String,
				isHidden newIsHidden: Bool
			) -> Wallet {
				return Wallet(keychain: self.keychain, name: newName, photo: newPhoto, isHidden: newIsHidden)
			}
		}
		
		let wallets: [String: Wallet]
		let defaultKey: String?
		
		private init(wallets: [String: Wallet], defaultKey: String?) {
			self.wallets = wallets
			self.defaultKey = defaultKey
		}
		
		init() {
			self.wallets = [:]
			self.defaultKey = nil
		}
		
		init(wallet: Wallet, id: WalletIdentifier) {
			let key = id.keychainKeyId
			self.wallets = [key: wallet]
			self.defaultKey = key
		}
		
		func getWallet(_ id: WalletIdentifier) -> Wallet? {
			return wallets[id.keychainKeyId]
		}
		
		func copyWithWallet(_ wallet: Wallet, id: WalletIdentifier) -> V1 {
			var newWallets = self.wallets
			newWallets[id.keychainKeyId] = wallet
			
			return V1(wallets: newWallets, defaultKey: self.defaultKey)
		}
		
		func copyRemovingWallet(_ id: WalletIdentifier) -> V1 {
			var newWallets = self.wallets
			newWallets.removeValue(forKey: id.keychainKeyId)
			
			let newDefaultKey: String? = if (defaultKey == id.keychainKeyId) { nil } else { defaultKey }
			
			return V1(wallets: newWallets, defaultKey: newDefaultKey)
		}
		
		func copyWithDefaultWalletId(_ id: WalletIdentifier?) -> V1 {
			
			return V1(wallets: self.wallets, defaultKey: id?.keychainKeyId)
		}
		
		func isDefaultWalletId(_ id: WalletIdentifier) -> Bool {
			return id.keychainKeyId == self.defaultKey
		}
		
		func defaultKeyInfo() -> KeyInfo? {
			guard let defaultKey else {
				return nil
			}
			return Self.splitKey(defaultKey)
		}
		
		func defaultWallet() -> Wallet? {
			guard let defaultKey else {
				return nil
			}
			return wallets[defaultKey]
		}
		
		struct KeyInfo {
			let chain: Bitcoin_kmpChain
			let nodeId: String
		}
		
		static func splitKey(_ id: String) -> KeyInfo? {
			
			// See `WalletIdentifier.keychainKeyId` for string format
			
			let comps = id.split(separator: "-")
			if comps.count == 1 {
				return KeyInfo(chain: Bitcoin_kmpChain.Mainnet(), nodeId: id)
				
			} else if comps.count == 2 {
				let chainName = String(comps[1])
				guard let chain = Bitcoin_kmpChain.fromString(chainName) else {
					return nil
				}
				return KeyInfo(chain: chain, nodeId: String(comps[0]))
				
			} else {
				return nil
			}
		}
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

/// Generic typed container.
/// Allows us to switch to alternative encryption schemes in the future, if needed.
///
protocol KeyInfo: Codable {
	var type: String { get }
}

/// ChaCha20-Poly1305 via Apple's CryptoKit
///
struct KeyInfo_ChaChaPoly: KeyInfo, Codable {
	let type: String // "ChaCha20-Poly1305"
	let nonce: Data
	let ciphertext: Data
	let tag: Data
	
	init(sealedBox: ChaChaPoly.SealedBox) {
		type = "ChaCha20-Poly1305"
		nonce = sealedBox.nonce.dataRepresentation
		ciphertext = sealedBox.ciphertext
		tag = sealedBox.tag
	}
	
	func toSealedBox() throws -> ChaChaPoly.SealedBox {
		return try ChaChaPoly.SealedBox(
			nonce      : ChaChaPoly.Nonce(data: self.nonce),
			ciphertext : self.ciphertext,
			tag        : self.tag
		)
	}
}
