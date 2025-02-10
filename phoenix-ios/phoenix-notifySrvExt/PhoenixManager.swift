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
 * What happens if multiple push notifications arrive ?
 *
 * iOS will launch the notification-service-extension upon receiving the first push notification.
 * Subsequent push notifications are queued by the OS. After the app extension finishes processing
 * the first notification (by invoking the `contentHandler`), then iOS will:
 *
 * - display the first push notification
 * - dealloc the `UNNotificationServiceExtension`
 * - Initialize a new `UNNotificationServiceExtension` instance
 * - And invoke it's `didReceive(_:)` function with the next item in the queue
 *
 * Note that it does **NOT** create a new app extension process.
 * It re-uses the existing process, and launches a new `UNNotificationServiceExtension` within it.
 *
 * This means that the following instances are recycled (continue existing in memory):
 * - PhoenixManager.shared
 * - XPC.shared
 *
 * ---------------------
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

	private var cancellables = Set<AnyCancellable>()
	private var oldCancellables = Set<AnyCancellable>()

	private var fiatExchangeRates: [ExchangeRate] = []

	private init() {} // Must use shared instance
	
	// --------------------------------------------------
	// MARK: Public Functions
	// --------------------------------------------------
	
	public func setupBusiness() -> PhoenixBusiness {
		log.trace("setupBusiness()")
		assertMainThread()
		
		if let currentBusiness = business {
			log.warning("setupBusiness(): business already setup")
			return currentBusiness
		}

		let newBusiness = PhoenixBusiness(ctx: PlatformContext.default)

		newBusiness.networkMonitor.disable()
		newBusiness.currencyManager.disableAutoRefresh()

		if let electrumConfigPrefs = GroupPrefs.shared.electrumConfig {
			newBusiness.appConfigurationManager.updateElectrumConfig(config: electrumConfigPrefs.customConfig)
		} else {
			newBusiness.appConfigurationManager.updateElectrumConfig(config: nil)
		}
		
		let primaryFiatCurrency = GroupPrefs.shared.fiatCurrency
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: primaryFiatCurrency,
			others: GroupPrefs.shared.preferredFiatCurrencies
		)
		newBusiness.appConfigurationManager.updatePreferredFiatCurrencies(
			current: preferredFiatCurrencies
		)

		let startupParams = StartupParams(
			requestCheckLegacyChannels: false,
			isTorEnabled: GroupPrefs.shared.isTorEnabled,
			liquidityPolicy: GroupPrefs.shared.liquidityPolicy.toKotlin(),
			trustedSwapInTxs: Set()
		)
		newBusiness.start(startupParams: startupParams)

		newBusiness.currencyManager.refreshAll(targets: [primaryFiatCurrency], force: false)

		newBusiness.currencyManager.ratesPubliser().sink {
			[weak self](rates: [ExchangeRate]) in

			assertMainThread() // var `fiatExchangeRates` should be accessed/updated only on main thread
			self?.fiatExchangeRates = rates
		}
		.store(in: &cancellables)

		// Setup complete
		business = newBusiness
		
		startAsyncUnlock()
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
			prvBusiness.stop(closeDatabases: true)
			oldBusiness = nil
			oldCancellables.removeAll()
		}

		oldBusiness = currentBusiness
		business = nil
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
	
	public func exchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		return Utils.exchangeRate(for: fiatCurrency, fromRates: fiatExchangeRates)
	}

	// --------------------------------------------------
	// MARK: Flow
	// --------------------------------------------------

	private func startAsyncUnlock() {
		log.trace("startAsyncUnlock()")
		
		let connectWithRecoveryPhrase = {(recoveryPhrase: RecoveryPhrase?) in
			DispatchQueue.main.async {
				self.connect(recoveryPhrase)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		DispatchQueue.global().async {
			
			// Fetch the "security.json" file
			let diskResult = SharedSecurity.shared.readSecurityJsonFromDisk()
			
			switch diskResult {
			case .failure(_):
				connectWithRecoveryPhrase(nil)
				
			case .success(let securityFile):
				
				let keychainResult = SharedSecurity.shared.readKeychainEntry(securityFile)
				switch keychainResult {
				case .failure(_):
					connectWithRecoveryPhrase(nil)
					
				case .success(let cleartextData):
					
					let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
					switch decodeResult {
					case .failure(_):
						connectWithRecoveryPhrase(nil)
						
					case .success(let recoveryPhrase):
						connectWithRecoveryPhrase(recoveryPhrase)
						
					} // </switch decodeResult>
				} // </switch keychainResult>
			} // </switch diskResult>
		}
	}
	
	private func connect(_ recoveryPhrase: RecoveryPhrase?) {
		log.trace("connect()")
		assertMainThread()
		
		guard let recoveryPhrase = recoveryPhrase else {
			log.warning("connect(): ignoring: recoveryPhrase == nil")
			return
		}
		guard let language = recoveryPhrase.language else {
			log.warning("connect(): ignoring: recoveryPhrase.language == nil")
			return
		}
		guard let business = business else {
			log.warning("connect(): ignoring: business == nil")
			return
		}

		let seed = business.walletManager.mnemonicsToSeed(
			mnemonics: recoveryPhrase.mnemonicsArray,
			wordList: language.wordlist(),
			passphrase: ""
		)
		business.walletManager.loadWallet(seed: seed)
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
				prvBusiness.stop(closeDatabases: true)
				oldBusiness = nil
				oldCancellables.removeAll()
			}
		}
	}
}
