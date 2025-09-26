import Foundation
import PhoenixShared
import Combine

fileprivate let filename = "PhoenixManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

typealias ConnectionsListener = (Connections) -> Void
typealias PaymentListener = (Lightning_kmpIncomingPayment) -> Void

/**
 * # Architecture notes:
 *
 * In a previous implementation, we created the `PhoenixBusiness` instance once,
 * and then simply performed a reconnect if we needed to handle multiple push notifications.
 * However, we encountered some technical problems with the approach...
 *
 * In lightning-kmp, the IncomingPaymentsHandler stores payment parts in memory.
 * If payment parts arrive slowly, the the background & foreground processes may
 * have a different view of the current state, or even handle the same part differently.
 *
 * Ultimately, this is a problem we'd like to fix in lightning-kmp. But for now,
 * a simple half-fix is to ensure the background process always uses a fresh lightning instance.
 */
class PhoenixManager {
	
	public static let shared = PhoenixManager()
	
	private var business: PhoenixBusiness? = nil
	private var oldBusiness: PhoenixBusiness? = nil
	
	private var walletId: WalletIdentifier? = nil

	private var cancellables = Set<AnyCancellable>()
	private var oldCancellables = Set<AnyCancellable>()

	private var fiatExchangeRates: [ExchangeRate] = []

	private init() {} // Must use shared instance
	
	// --------------------------------------------------
	// MARK: Public Functions
	// --------------------------------------------------
	
	public func setupBusiness(_ target: String?) -> PhoenixBusiness {
		log.trace("setupBusiness()")
		assertMainThread()
		
		if let currentBusiness = business {
			log.warning("setupBusiness(): business already setup")
			return currentBusiness
		}

		let newBusiness = PhoenixBusiness(ctx: PlatformContext.default)

		newBusiness.networkMonitor.disable()
		newBusiness.currencyManager.disableAutoRefresh()

		// Setup complete
		business = newBusiness
		
		startAsyncUnlock(target)
		return newBusiness
	}

	public func teardownBusiness() {
		log.trace("teardownBusiness()")
		assertMainThread()

		guard let currentBusiness = business else {
			log.warning("teardownBusiness(): business already nil")
			return
		}

		if let prvBusiness = oldBusiness {
			prvBusiness.stop()
			oldBusiness = nil
			oldCancellables.removeAll()
		}

		oldBusiness = currentBusiness
		business = nil
		walletId = nil
		cancellables.removeAll()

		currentBusiness.connectionsManager.connectionsPublisher().sink {
			[weak self](connections: Connections) in

			self?.oldConnectionsChanged(connections)
		}
		.store(in: &oldCancellables)

		currentBusiness.appConnectionsDaemon?.incrementDisconnectCount(
			target: AppConnectionsDaemon.ControlTarget.companion.All
		)

		// Safety mechanism: To make sure nothing falls between the cracks,
		// and the oldBusiness doesn't get properly cleaned up.
		oldConnectionsChanged(currentBusiness.connectionsManager.currentValue)
	}
	
	public func groupPrefs() -> GroupPrefs_Wallet? {
		
		if let walletId {
			return GroupPrefs.wallet(walletId)
		} else {
			return nil
		}
	}
	
	public func exchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		return Utils.exchangeRate(for: fiatCurrency, fromRates: fiatExchangeRates)
	}

	// --------------------------------------------------
	// MARK: Flow
	// --------------------------------------------------

	private func startAsyncUnlock(_ target: String?) {
		log.trace("startAsyncUnlock()")
		
		let unlockWithRecoveryPhrase = {(recoveryPhrase: RecoveryPhrase?) in
			DispatchQueue.main.async {
				self.unlock(recoveryPhrase, target)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		DispatchQueue.global().async {
			
			// Fetch the "security.json" file
			let diskResult = SharedSecurity.shared.readSecurityJsonFromDisk()
			
			switch diskResult {
			case .failure(_):
				unlockWithRecoveryPhrase(nil)
				
			case .success(let securityFile):
				
				var sealedBox: SealedBox_ChaChaPoly? = nil
				var id: String? = nil
				
				switch securityFile {
				case .v0(let v0):
					sealedBox = v0.keychain
					id = KEYCHAIN_DEFAULT_ID
					
				case .v1(let v1):
					if let target {
						sealedBox = v1.wallets[target]?.keychain
						id = target
					} else if let defaultTarget = v1.defaultKey {
						sealedBox = v1.wallets[defaultTarget]?.keychain
						id = defaultTarget
					}
				}
				
				guard let sealedBox, let id else {
					unlockWithRecoveryPhrase(nil)
					return
				}
				
				let keychainResult = SharedSecurity.shared.readKeychainEntry(id, sealedBox)
				switch keychainResult {
				case .failure(_):
					unlockWithRecoveryPhrase(nil)
					
				case .success(let cleartextData):
					
					let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
					switch decodeResult {
					case .failure(_):
						unlockWithRecoveryPhrase(nil)
						
					case .success(let recoveryPhrase):
						unlockWithRecoveryPhrase(recoveryPhrase)
						
					} // </switch decodeResult>
				} // </switch keychainResult>
			} // </switch diskResult>
		} // </DispatchQueue.global().async>
	}
	
	private func unlock(_ recoveryPhrase: RecoveryPhrase?, _ targetNodeIdHash: String?) {
		log.trace("unlock()")
		assertMainThread()
		
		guard let recoveryPhrase else {
			log.warning("unlock(): ignoring: recoveryPhrase == nil")
			return
		}
		guard let language = recoveryPhrase.language else {
			log.warning("unlock(): ignoring: recoveryPhrase.language == nil")
			return
		}
		guard let business else {
			log.warning("unlock(): ignoring: business == nil")
			return
		}

		let seed = business.walletManager.mnemonicsToSeed(
			mnemonics: recoveryPhrase.mnemonicsArray,
			wordList: language.wordlist(),
			passphrase: ""
		)
		let walletInfo = business.walletManager.loadWallet(seed: seed)
		
		let wid = WalletIdentifier(chain: business.chain, walletInfo: walletInfo)
		walletId = wid
		
		if let targetNodeIdHash {
			guard targetNodeIdHash == wid.nodeIdHash else {
				log.warning("unlock(): ignoring: target.nodeIdHash != unlocked.nodeIdHash")
				return
			}
		}
		
		let groupPrefs = GroupPrefs.wallet(wid)
		
		if let electrumConfigPrefs = groupPrefs.electrumConfig {
			business.appConfigurationManager.updateElectrumConfig(config: electrumConfigPrefs.customConfig)
		} else {
			business.appConfigurationManager.updateElectrumConfig(config: nil)
		}
		
		let primaryFiatCurrency = groupPrefs.fiatCurrency
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: primaryFiatCurrency,
			others: groupPrefs.preferredFiatCurrencies
		)
		business.appConfigurationManager.updatePreferredFiatCurrencies(
			current: preferredFiatCurrencies
		)

		let startupParams = StartupParams(
			isTorEnabled: groupPrefs.isTorEnabled,
			liquidityPolicy: groupPrefs.liquidityPolicy.toKotlin()
		)
		business.start(startupParams: startupParams)

		business.currencyManager.refreshAll(targets: [primaryFiatCurrency], force: false)
		business.currencyManager.ratesPubliser().sink {
			[weak self](rates: [ExchangeRate]) in

			assertMainThread() // var `fiatExchangeRates` should be accessed/updated only on main thread
			self?.fiatExchangeRates = rates
		}
		.store(in: &cancellables)
	}

	// --------------------------------------------------
	// MARK: Cleanup
	// --------------------------------------------------

	private func oldConnectionsChanged(_ connections: Connections) {
		log.trace("oldConnectionsChanged(_)")

		switch connections.peer {
			case is Lightning_kmpConnection.ESTABLISHED  : log.debug("oldConnections.peer = ESTABLISHED")
			case is Lightning_kmpConnection.ESTABLISHING : log.debug("oldConnections.peer = ESTABLISHING")
			case is Lightning_kmpConnection.CLOSED       : log.debug("oldConnections.peer = CLOSED")
			default                                      : log.debug("oldConnections.peer = UNKNOWN")
		}
		switch connections.electrum {
			case is Lightning_kmpConnection.ESTABLISHED  : log.debug("oldConnections.electrum = ESTABLISHED")
			case is Lightning_kmpConnection.ESTABLISHING : log.debug("oldConnections.electrum = ESTABLISHING")
			case is Lightning_kmpConnection.CLOSED       : log.debug("oldConnections.electrum = CLOSED")
			default                                      : log.debug("oldConnections.electrum = UNKNOWN")
		}

		if connections.peer is Lightning_kmpConnection.CLOSED &&
			connections.electrum is Lightning_kmpConnection.CLOSED
		{
			if let prvBusiness = oldBusiness {
				prvBusiness.stop()
				oldBusiness = nil
				oldCancellables.removeAll()
			}
		}
	}
}
