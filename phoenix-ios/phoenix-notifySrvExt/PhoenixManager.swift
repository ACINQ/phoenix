import Foundation
import PhoenixShared
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PhoenixManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
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
 * - XpcManager.shared
 */
class PhoenixManager {
	
	public static let shared = PhoenixManager()
	
	public let business: PhoenixBusiness
	
	private var queue = DispatchQueue(label: "PhoenixManager")
	private var connectionsListener: ConnectionsListener? = nil
	private var paymentListener: PaymentListener? = nil
	
	private var isFirstConnect = true
	
	private var publisher: AnyPublisher<Lightning_kmpIncomingPayment, Never>? = nil
	
	private var fiatExchangeRates: [ExchangeRate] = []
	private var cancellables = Set<AnyCancellable>()
	
	private init() {
		business = PhoenixBusiness(ctx: PlatformContext())
		
		let electrumConfig = GroupPrefs.shared.electrumConfig
		business.appConfigurationManager.updateElectrumConfig(server: electrumConfig?.serverAddress)
		
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: GroupPrefs.shared.fiatCurrency,
			others: GroupPrefs.shared.preferredFiatCurrencies
		)
		business.appConfigurationManager.updatePreferredFiatCurrencies(current: preferredFiatCurrencies)
		
		business.networkMonitor.disable()
		business.currencyManager.disableAutoRefresh()
		
		let startupParams = StartupParams(
			requestCheckLegacyChannels: false,
			isTorEnabled: GroupPrefs.shared.isTorEnabled,
			liquidityPolicy: GroupPrefs.shared.liquidityPolicy.toKotlin(),
			trustedSwapInTxs: Set()
		)
		business.start(startupParams: startupParams)
	}
	
	// --------------------------------------------------
	// MARK: Public Functions
	// --------------------------------------------------
	
	public func register(
		connectionsListener: @escaping ConnectionsListener,
		paymentListener: @escaping PaymentListener
	) {
		log.trace("register(::)")
		
		let wasAlreadyUnlocked = business.walletManager.isLoaded()
		
		queue.async { [self] in
			self.connectionsListener = connectionsListener
			self.paymentListener = paymentListener
		
			if wasAlreadyUnlocked {
				// The new instance (`UNNotificationServiceExtension`) needs to know the current connection state.
				DispatchQueue.main.async {
					let connections = business.connectionsManager.currentValue
					self.connectionsChanged(connections)
				}
			}
		}
	}
	
	public func unregister() {
		log.trace("unregister()")
		
		queue.async { [self] in
			self.connectionsListener = nil
			self.paymentListener = nil
		}
	}
	
	public func connect() {
		log.trace("connect()")
		
		queue.async { [self] in
			
			if isFirstConnect {
				isFirstConnect = false
				unlock()
			} else {
				reconnect()
			}
		}
	}
	
	public func disconnect() {
		log.trace("disconnect()")
		
		// Kotlin needs to be accessed only on the main thread
		DispatchQueue.main.async { [self] in
			business.appConnectionsDaemon?.incrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	public func exchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		return Utils.exchangeRate(for: fiatCurrency, fromRates: fiatExchangeRates)
	}
	
	// --------------------------------------------------
	// MARK: Private Functions
	// --------------------------------------------------
	
	private func reconnect() {
		log.trace("reconnect()")
		
		// Kotlin needs to be accessed only on the main thread
		DispatchQueue.main.async { [self] in
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	private func unlock() {
		log.trace("unlock()")
		
		let connectWithMnemonics = {(mnemonics: [String]?) in
			DispatchQueue.main.async {
				self.connect(mnemonics: mnemonics)
			}
		}
		
		// Disk IO ahead - get off the main thread.
		DispatchQueue.global().async {
			
			// Fetch the "security.json" file
			let diskResult = SharedSecurity.shared.readSecurityJsonFromDisk()
			
			switch diskResult {
			case .failure(_):
				connectWithMnemonics(nil)
				
			case .success(let securityFile):
				
				let keychainResult = SharedSecurity.shared.readKeychainEntry(securityFile)
				switch keychainResult {
				case .failure(_):
					connectWithMnemonics(nil)
				case .success(let mnemonics):
					connectWithMnemonics(mnemonics)
				}
			}
		}
	}
	
	private func connect(mnemonics: [String]?) {
		log.trace("connect(mnemoncis:)")
		assertMainThread()
		
		guard let mnemonics = mnemonics else {
			return
		}

		business.connectionsManager.connectionsPublisher().sink {
			[weak self](connections: Connections) in
			
			self?.connectionsChanged(connections)
		}
		.store(in: &cancellables)
		
		let pushReceivedAt = Date()
		business.paymentsManager.lastIncomingPaymentPublisher().sink {
			[weak self](payment: Lightning_kmpIncomingPayment) in
			
			guard
				let paymentReceivedAt = payment.received?.receivedAtDate,
				paymentReceivedAt > pushReceivedAt
			else {
				// Ignoring - this is the most recently received incomingPayment, but not a new one
				return
			}
			
			self?.didReceivePayment(payment)
		}
		.store(in: &cancellables)
		
		business.currencyManager.ratesPubliser().sink {
			[weak self](rates: [ExchangeRate]) in
			
			assertMainThread() // var `fiatExchangeRates` should be accessed/updated only on main thread
			self?.fiatExchangeRates = rates
		}
		.store(in: &cancellables)
		
		let seed = business.walletManager.mnemonicsToSeed(mnemonics: mnemonics, passphrase: "")
		business.walletManager.loadWallet(seed: seed)
		
		let primaryFiatCurrency = GroupPrefs.shared.fiatCurrency
		business.currencyManager.refreshAll(targets: [primaryFiatCurrency], force: false)
	}
	
	private func connectionsChanged(_ connections: Connections) {
		log.trace("connectionsChanged(_)")
		
		queue.async { [self] in
			if let listener = self.connectionsListener {
				DispatchQueue.main.async {
					listener(connections)
				}
			}
		}
	}
	
	private func didReceivePayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("didReceivePayment(_)")
		
		queue.async { [self] in
			if let listener = self.paymentListener {
				DispatchQueue.main.async {
					listener(payment)
				}
			}
		}
	}
}
