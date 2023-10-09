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
	private var listener: PaymentListener? = nil
	
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
	
	public func register(didReceivePayment newListener: @escaping PaymentListener) {
		log.trace("register(didReceivePayment:)")
		
		queue.async { [self] in
			if listener == nil {
				listener = newListener
			}
		}
	}
	
	public func unregister() {
		log.trace("unregister()")
		
		queue.async { [self] in
			listener = nil
		}
	}
	
	public func connect() {
		log.trace("connect()")
		
		queue.async { [self] in
			
			if isFirstConnect {
				isFirstConnect = false
				_unlock()
			} else {
				_reconnect()
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
	
	private func _reconnect() {
		log.trace("_reconnect()")
		
		// Kotlin needs to be accessed only on the main thread
		DispatchQueue.main.async { [self] in
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	private func _unlock() {
		log.trace("_unlock()")
		
		let connectWithMnemonics = {(mnemonics: [String]?) in
			DispatchQueue.main.async {
				self._connect(mnemonics: mnemonics)
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
	
	private func _connect(mnemonics: [String]?) {
		log.trace("_connect(mnemoncis:)")
		
		guard let mnemonics = mnemonics else {
			return
		}

		let pushReceivedAt = Date()
		
		business.paymentsManager.lastIncomingPaymentPublisher().sink {
			[weak self](payment: Lightning_kmpIncomingPayment) in
				
			assertMainThread()
			guard
				let paymentReceivedAt = payment.received?.receivedAtDate,
				paymentReceivedAt > pushReceivedAt
			else {
				// Ignoring - this is the most recently received incomingPayment, but not a new one
				return
			}
			
			self?._didReceivedPayment(payment)
		
		}.store(in: &cancellables)
		
		business.currencyManager.ratesPubliser().sink {[weak self](rates: [ExchangeRate]) in
			
			self?.fiatExchangeRates = rates
			
		}.store(in: &cancellables)
		
		let seed = business.walletManager.mnemonicsToSeed(mnemonics: mnemonics, passphrase: "")
		business.walletManager.loadWallet(seed: seed)
		
		_refreshCurrencyExchangeRate()
	}
	
	private func _didReceivedPayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("_didReceivePayment(_)")
		
		queue.async { [self] in
			
			if let listener = listener {
				DispatchQueue.main.async {
					listener(payment)
				}
			}
		}
	}
	
	private func _refreshCurrencyExchangeRate() {
		log.trace("_refreshCurrencyExchangeRate()")
		assertMainThread()
		
		let primaryFiatCurrency = GroupPrefs.shared.fiatCurrency
		business.currencyManager.refreshAll(targets: [primaryFiatCurrency], force: false)
	}
	
	public func exchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		return Utils.exchangeRate(for: fiatCurrency, fromRates: fiatExchangeRates)
	}
}
