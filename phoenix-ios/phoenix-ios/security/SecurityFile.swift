import Foundation
import CryptoKit

/// Represents the security options enabled by the user.
///
struct EnabledSecurity: OptionSet {
	let rawValue: Int

	static let biometrics = EnabledSecurity(rawValue: 1 << 0)
	static let passphrase = EnabledSecurity(rawValue: 1 << 1)

	static let none: EnabledSecurity = []
	static let both: EnabledSecurity = [.biometrics, .passphrase]
}

/// Represents the "security.json" file, where we store the wrapped credentials needed to decrypt the databaseKey.
///
struct SecurityFile: Codable {
	
	private enum CodingKeys: String, CodingKey {
		case keychain
		case biometrics
		case passphrase
	}
	
	/// The "keychain" option represents the default security.
	/// That is, the user has not enabled any additional security measures such as touchID or passphrase.
	///
	/// Here's how it works:
	/// The databaseKey is wrapped with a randomly generated key (called the locking key).
	/// The lockingKey is then stored in the iOS keychain.
	/// And the SecurityFile contains the parameters needed to reproduce the databaseKey
	/// if given the lockingKey.
	/// E.g. the salt, nonce, IV, tag, rounds ... whatever the specific crypto algorithm needs
	///
	let keychain: KeyInfo?
	
	/// The `biometrics` option is created when the user enables Biometrics
	/// (e.g. TouchID or FaceID) in the app-access settings screen.
	///
	/// Here's how it works:
	/// The databaseKey is wrapped with a randomly generated key (called the locking key).
	/// The lockingKey is then stored in the iOS keychain, and configured with access-control
	/// settings that require biometrics to unlock/access the keychain item.
	/// And the SecurityFile contains the parameters needed to reproduce the databaseKey
	/// if given the lockingKey.
	/// E.g. the salt, nonce, IV, tag, rounds ... whatever the specific crypto algorithm needs
	///
	let biometrics: KeyInfo?
	
	let passphrase: KeyInfo?
	
	var hasKeychain: Bool {
		get { return self.keychain != nil }
	}
	var hasBiometrics: Bool {
		get { return self.biometrics != nil }
	}
	var hasPassphrase: Bool {
		get { return self.passphrase != nil }
	}
	
	var enabledSecurity: EnabledSecurity {
		get {
			if self.hasBiometrics {
				if self.hasPassphrase {
					return EnabledSecurity.both
				} else {
					return EnabledSecurity.biometrics
				}
			}
			if self.hasPassphrase {
				return EnabledSecurity.passphrase
			}
			return EnabledSecurity.none
		}
	}
	
	init() {
		self.keychain = nil
		self.biometrics = nil
		self.passphrase = nil
	}
	
	init(keychain: KeyInfo) {
		self.keychain = keychain
		self.biometrics = nil
		self.passphrase = nil
	}
	
	init(touchID: KeyInfo?, passphrase: KeyInfo?) {
		self.keychain = nil
		self.biometrics = touchID
		self.passphrase = passphrase
	}

	init(from decoder: Decoder) throws {
		let container = try decoder.container(keyedBy: CodingKeys.self)
		
		self.keychain = try container.decodeIfPresent(KeyInfo_ChaChaPoly.self, forKey: .keychain)
		self.biometrics = try container.decodeIfPresent(KeyInfo_ChaChaPoly.self, forKey: .biometrics)
		self.passphrase = try container.decodeIfPresent(KeyInfo_ChaChaPoly.self, forKey: .passphrase)
	}
	
	func encode(to encoder: Encoder) throws {
		
		var container = encoder.container(keyedBy: CodingKeys.self)
		
		if let keychain = self.keychain as? KeyInfo_ChaChaPoly {
			try container.encode(keychain, forKey: .keychain)
		}
		if let biometrics = self.biometrics as? KeyInfo_ChaChaPoly {
			try container.encode(biometrics, forKey: .biometrics)
		}
		if let passphrase = self.passphrase as? KeyInfo_ChaChaPoly {
			try container.encode(passphrase, forKey: .passphrase)
		}
	}
}

// --------------------------------------------------------------------------------
// MARK:-

/// Generic typed container.
/// Allows us to switch to alternative encryption schemes in the future, if needed.
///
protocol KeyInfo: Codable {
	var type: String { get }
}

// --------------------------------------------------------------------------------
// MARK:-

/// ChaCha20-Poly1305
/// Via Apple's CryptoKit using ChaChaPoly.
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
