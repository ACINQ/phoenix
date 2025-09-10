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
	
	// --------------------------------------------------
	// MARK: Add Wallet
	// --------------------------------------------------
	
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
		
		let securityFile: SecurityFile.V1
		
		if let existingFile = SecurityFileManager.shared.currentSecurityFile() {
			switch existingFile {
				case .v0(_)      : return .failure(.existingSecurityFileV0)
				case .v1(let v1) : securityFile = v1
			}
		} else {
			securityFile = SecurityFile.V1()
		}
		
		if let _ = securityFile.getWallet(walletId) {
			
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
			// Various examples include:
			// - user had biometrics enabled, and faceId/touchId stopped working (hardware damage)
			// - user enabled a lockPin and forgot the pin
			// - user enabled a spendingPin and forgot the pin
			//
			// Now, since they know their recovery phrase, they haven't lost any funds.
			// But they may have had iCloud backup disabled,
			// and they don't want to lose their transaction history.
			//
			// In the past, there was no solution for them.
			// They had to uninstall & reinstall the app.
			// Which meant losing their transaction history (if iCloud backup was disabled).
			// With this implementation, we can finally offer them a simple solution.
			//
			Keychain.wallet(walletId).resetWallet()
			
		} else {
			
			// Items stored in the iOS keychain remain persisted between iOS installs.
			// So it's possible the associated wallet already has associated items in the keychain.
			// So we should clear them here.
			//
			// Related issues:
			// Issue #282 - Face ID remains enabled between app installs.
			//
			Keychain.wallet(walletId).resetWallet()
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
		
		let name = randomWalletName()
		let photo = WalletIcon.random().filename
		
		let newWallet = SecurityFile.V1.Wallet(
			keychain: SealedBox_ChaChaPoly(sealedBox),
			name: name,
			photo: photo,
			isHidden: false,
			chain: chain
		)
		
		// Set this wallet as the default if:
		// - there is no default wallet set
		// - this is the first VISIBLE wallet that's being added
		//
		// Security Note:
		// It might be the case that a user has only hidden wallets.
		// If our criteria was simply:
		//   "set this wallet as default if there is only 1 wallet"
		// then an attacker would have a way to discover if there are hidden
		// wallets loaded in the system. We don't want the attacker to have that tool.
		// Thus, we only consider VISIBLE wallets in our calculation.
		//
		var makeDefaultWallet = false
		if securityFile.defaultWallet() == nil {
			let numVisibleWallets = securityFile.wallets.values.filter { !$0.isHidden }.count
			if numVisibleWallets == 0 {
				makeDefaultWallet = true
			}
		}
		
		var newSecurityFile = securityFile.copyWithWallet(newWallet, id: walletId)
		if makeDefaultWallet {
			newSecurityFile = newSecurityFile.copyWithDefaultWalletId(walletId)
		}
		
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
	
	// --------------------------------------------------
	// MARK: Remove Wallet
	// --------------------------------------------------
	
	public func removeWallet(
		walletId: WalletIdentifier,
		completion: @escaping (Result<Void, RemoveEntryError>) -> Void
	) {
		
		Task(priority: .userInitiated) {
			let result = await self._removeWallet(walletId)
			DispatchQueue.main.async { completion(result) }
		}
	}
	
	private func _removeWallet(
		_ walletId: WalletIdentifier
	) async -> Result<Void, RemoveEntryError> {
		
		let securityFile: SecurityFile.V1
		
		if let existingFile = SecurityFileManager.shared.currentSecurityFile() {
			switch existingFile {
				case .v0(_)      : return .failure(.existingSecurityFileV0)
				case .v1(let v1) : securityFile = v1
			}
		} else {
			securityFile = SecurityFile.V1()
		}
		
		guard let metadata = securityFile.getWallet(walletId) else {
			return .success
		}
		
		let photo = metadata.photo
		if !WalletIcon.isValidFilename(photo) {
			await PhotosManager.shared.deleteFromDisk(fileName: photo)
		}
		
		let newSecurityFile = securityFile.copyRemovingWallet(walletId)
		
		do throws(WriteSecurityFileError) {
			try await SecurityFileManager.shared.asyncWriteToDisk(newSecurityFile)
		} catch {
			log.error("SecurityFile.write(): error: \(error)")
			return .failure(.errorWritingSecurityFile(underlying: error))
		}
		
		return .success
	}
	
	// --------------------------------------------------
	// MARK: Load Wallet
	// --------------------------------------------------
	
	/// Hook function: Called after a wallet has been loaded.
	/// We use this to upgrade the SecurityFile from V0 to V1 (if needed).
	/// We also use this to perform the one-time keychain migration.
	///
	public func didLoadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		// If the SecurityFile is still at Version 0, then let's upgrade it now.
		
		let v0: SecurityFile.V0
		if let securityFile = SecurityFileManager.shared.currentSecurityFile() {
			switch securityFile {
			case .v0(let oldVersion):
				v0 = oldVersion
			case .v1(_):
				log.debug("No upgrade needed")
				return
			}
		} else {
			log.warning("SecurityFileManager.currentSecurityFile(): nil")
			return
		}
		
		guard let keyInfo = v0.keychain else {
			log.warning("Cannot upgrade: keychain info not set")
			return
		}
		
		// Move all: "key-default" > "key-<walletId>"
		Keychain.didLoadWallet(walletId)
		
		let name = randomWalletName()
		let photo = WalletIcon.random().filename
		
		let newWallet = SecurityFile.V1.Wallet(
			keychain: keyInfo,
			name: name,
			photo: photo,
			isHidden: false,
			chain: walletId.chain
		)
		
		let newSecurityFile = SecurityFile.V1(wallet: newWallet, id: walletId)
		
		SecurityFileManager.shared.asyncWriteToDisk(newSecurityFile) { result in
			switch result {
			case .failure(let reason):
				log.error("SecurityFileManager.writeToDisk: error: \(reason)")
				
			case .success():
				log.debug("Done")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	private func randomWalletName() -> String {
		
		let randomId = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(maxLength: 6)
		return String(localized: "Wallet \(randomId)", comment: "Default wallet name")
	}
}
