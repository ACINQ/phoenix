import Foundation
import PhoenixShared
import CryptoKit

fileprivate let filename = "AppSecurity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class AppSecurity {
	
	/// Singleton instance
	public static let shared = AppSecurity()
	
	private init() { /* must use shared instance */ }
	
	public func addWallet(
		chain: Bitcoin_kmpChain,
		recoveryPhrase: RecoveryPhrase,
		seed knownSeed: KotlinByteArray? = nil,
		completion: @escaping (Result<Void, AddEntryError>) -> Void
	) {
		
		DispatchQueue.global(qos: .userInteractive).async {
			let result = self._addWallet(chain, recoveryPhrase, knownSeed)
			DispatchQueue.main.async { completion(result) }
		}
	}
	
	private func _addWallet(
		_ chain: Bitcoin_kmpChain,
		_ recoveryPhrase: RecoveryPhrase,
		_ knownSeed: KotlinByteArray? = nil
	) -> Result<Void, AddEntryError> {
		
		let walletInfo = _calculateWalletInfo(chain, recoveryPhrase, knownSeed)
		let walletId = WalletIdentifier(chain: chain, walletInfo: walletInfo)
		let nodeId = walletInfo.nodeIdString
		
		let securityFile: SecurityFile.V1
		
		switch SecurityFileManager.shared.readFromDisk() {
		case .failure(let reason):
			switch reason {
				case .fileNotFound         : securityFile = SecurityFile.V1()
				case .errorReadingFile(_)  : return .failure(.errorReadingSecurityFile(underlying: reason))
				case .errorDecodingFile(_) : return .failure(.errorReadingSecurityFile(underlying: reason))
			}
			
		case .success(let existingFile):
			switch existingFile {
				case .v0(_)      : return .failure(.existingSecurityFileV0)
				case .v1(let v1) : securityFile = v1
			}
		}
		
		let lockingKey = SymmetricKey(size: .bits256)
		
		let sealedBox: ChaChaPoly.SealedBox
		do {
			let recoveryPhraseData = try JSONEncoder().encode(recoveryPhrase)
			sealedBox = try ChaChaPoly.seal(recoveryPhraseData, using: lockingKey)
		} catch {
			return .failure(.errorEncodingRecoveryPhrase(underlying: error))
		}
		
		switch Keychain.wallet(walletId).setLockingKey(lockingKey) {
		case .failure(let error):
			return .failure(.errorWritingToKeychain(underlying: error))
		case .success:
			break
		}
		
		let name = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(maxLength: 6)
		
		let newWallet = SecurityFile.V1.Wallet(
			keychain: KeyInfo_ChaChaPoly(sealedBox: sealedBox),
			hidden: false,
			name: name,
			photo: nil
		)
		
		if let _ = securityFile.wallets[nodeId] {
			
			// What do we do if the user is restoring a wallet that already exists on the system ?
			//
			// - overwrite the existing value in the security.json file
			// - remove any associated keychain entries
			//
			// And there's a very good reason to do it this way:
			//
			// We have received reports from users who have locked themselves out of their wallet,
			// but they know their recovery phrase.
			//
			// For example:
			// - they had biometrics enabled, and faceId/touchId stopped working (hardware damage)
			// - they enabled a lockPin and forgot the pin
			// - they enabled a spendingPin and forgot the pin
			//
			// Now, since they know their recovery phrase, they haven't lost any funds.
			// But they also had iCloud backup disabled,
			// and they don't want to lose their transaction history.
			//
			// In the past, there was no solution for them. They had to uninstall & reinstall the app.
			// With this implementation, we can finally offer them a simple solution.
			
			Keychain.wallet(walletId).resetWallet()
		}
		
		let newSecurityFile = securityFile.copyWithWallet(newWallet, id: walletId)
		
		switch SecurityFileManager.shared.writeToDisk(newSecurityFile) {
		case .failure(let reason):
			return .failure(.errorWritingSecurityFile(underlying: reason))
		case .success():
			return .success
		}
	}
	
	private func _calculateWalletInfo(
		_ chain: Bitcoin_kmpChain,
		_ recoveryPhrase: RecoveryPhrase,
		_ knownSeed: KotlinByteArray?
	) -> WalletManager.WalletInfo {
		
		let language = recoveryPhrase.language ?? MnemonicLanguage.english
		let seed = knownSeed ?? Biz.business.walletManager.mnemonicsToSeed(
			mnemonics  : recoveryPhrase.mnemonicsArray,
			wordList   : language.wordlist(),
			passphrase : ""
		)
		
		let keyManager = Lightning_kmpLocalKeyManager(
			seed: Bitcoin_kmpByteVector(bytes: seed),
			chain: chain,
			remoteSwapInExtendedPublicKey: NodeParamsManager.companion.remoteSwapInXpub)
		
		return WalletManager.WalletInfo(
			nodeId: keyManager.nodeKeys.nodeKey.publicKey,
			nodeIdHash: keyManager.nodeIdHash(),
			cloudKey: keyManager.cloudKey(),
			cloudKeyHash: keyManager.cloudKeyHash()
		)
	}
	
	public func loadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		let v0: SecurityFile.V0
		switch SecurityFileManager.shared.readFromDisk() {
		case .failure(let reason):
			log.warning("SecurityFileManager.readFromDisk: error: \(reason)")
			return
			
		case .success(let securityFile):
			switch securityFile {
			case .v0(let oldVersion):
				v0 = oldVersion
			case .v1(_):
				log.debug("No upgrade needed")
				return
			}
		}
		
		guard let keyInfo = v0.keychain else {
			log.warning("Cannot upgrade: keychain info not set")
			return
		}
		
		// Move all: "key-default" > "key-<walletId>"
		Keychain.loadWallet(walletId)
		
		let name = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(maxLength: 6)
		let newWallet = SecurityFile.V1.Wallet(
			keychain: keyInfo,
			hidden: false,
			name: name,
			photo: nil
		)
		
		let newSecurityFile = SecurityFile.V1(wallet: newWallet, id: walletId)
		
		switch SecurityFileManager.shared.writeToDisk(newSecurityFile) {
		case .failure(let reason):
			log.error("SecurityFileManager.writeToDisk: error: \(reason)")
			
		case .success():
			log.debug("Done")
		}
	}
}
