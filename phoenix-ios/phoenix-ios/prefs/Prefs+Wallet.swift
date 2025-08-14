import Foundation
import Combine

fileprivate let filename = "Prefs+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = PrefsKey
fileprivate typealias KeyDeprecated = PrefsKeyDeprecated

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are NOT shared with other extensions bundled in the app,
/// such as the notification-service-extension. For preferences shared with extensions, see GroupPrefs.
///
class Prefs_Wallet {
	
	private static var defaults: UserDefaults {
		return Prefs.defaults
	}
	
	private let id: String
	private let defaults: UserDefaults
#if DEBUG
	private let isDefault: Bool
#endif
	
	init(id: String) {
		self.id = id
		self.defaults = Self.defaults
	#if DEBUG
		self.isDefault = (id == PREFS_DEFAULT_ID)
	#endif
	}
	
	// --------------------------------------------------
	// MARK: User Options
	// --------------------------------------------------
	
	var defaultPaymentDescription: String? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.string(forKey: Key.defaultPaymentDescription.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.defaultPaymentDescription.value(id))
		}
	}
	
	var invoiceExpirationDays: Int {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.integer(forKey: Key.invoiceExpirationDays.value(id), defaultValue: 7)
		}
		set {
			defaults.set(newValue, forKey: Key.invoiceExpirationDays.value(id))
		}
	}
	
	var invoiceExpirationSeconds: Int64 {
		return Int64(invoiceExpirationDays) * Int64(60 * 60 * 24)
	}
	
	var hideAmounts: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hideAmounts.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hideAmounts.value(id))
		}
	}
	
	lazy private(set) var showOriginalFiatAmountPublisher = {
		CurrentValueSubject<Bool, Never>(self.showOriginalFiatAmount)
	}()
	
	var showOriginalFiatAmount: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.showOriginalFiatAmount.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.showOriginalFiatAmount.value(id))
			runOnMainThread {
				self.showOriginalFiatAmountPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var recentPaymentsConfigPublisher = {
		CurrentValueSubject<RecentPaymentsConfig, Never>(self.recentPaymentsConfig)
	}()
	
	var recentPaymentsConfig: RecentPaymentsConfig {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.recentPaymentsConfig.value(id))?.jsonDecode() ??
			RecentPaymentsConfig.mostRecent(count: 3)
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: Key.recentPaymentsConfig.value(id))
			runOnMainThread {
				self.recentPaymentsConfigPublisher.send(newValue)
			}
		}
	}
	
	var hasUpgradedSeedCloudBackups: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasUpgradedSeedCloudBackups.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hasUpgradedSeedCloudBackups.value(id))
		}
	}
  
	lazy private(set) var serverMessageReadIndexPublisher = {
		CurrentValueSubject<Int?, Never>(self.serverMessageReadIndex)
	}()
	
	var serverMessageReadIndex: Int? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.number(forKey: Key.serverMessageReadIndex.value(id))?.intValue
		}
		set {
			let key = Key.serverMessageReadIndex.value(id)
			if let number = newValue {
				defaults.set(NSNumber(value: number), forKey: key)
			} else {
				defaults.removeObject(forKey: key)
			}
			runOnMainThread {
				self.serverMessageReadIndexPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var allowOverpaymentPublisher = {
		CurrentValueSubject<Bool, Never>(self.allowOverpayment)
	}()
	
	var allowOverpayment: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.allowOverpayment.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.allowOverpayment.value(id))
			runOnMainThread {
				self.allowOverpaymentPublisher.send(newValue)
			}
		}
	}

	var doNotShowChannelImpactWarning: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.doNotShowChannelImpactWarning.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.doNotShowChannelImpactWarning.value(id))
		}
	}
	
	var watchTower_lastAttemptDate: Date {
		get {
			maybeLogDefaultAccess(#function)
			let seconds: TimeInterval = defaults.double(forKey: Key.watchTower_lastAttemptDate.value(id))
			return Date(timeIntervalSince1970: seconds)
		}
		set {
			let seconds: TimeInterval = newValue.timeIntervalSince1970
			defaults.set(seconds, forKey: Key.watchTower_lastAttemptDate.value(id))
		}
	}
	
	var watchTower_lastAttemptFailed: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.watchTower_lastAttemptFailed.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.watchTower_lastAttemptFailed.value(id))
		}
	}
	
	// --------------------------------------------------
	// MARK: Wallet State
	// --------------------------------------------------
	
	lazy private(set) var isNewWalletPublisher: PassthroughSubject<Bool, Never> = {
		return PassthroughSubject<Bool, Never>()
	}()
	
	/**
	 * Set to true, until the user has funded their wallet at least once.
	 * A false value does NOT indicate that the wallet has funds.
	 * Just that the wallet had either a non-zero balance, or a transaction, at least once.
	 */
	var isNewWallet: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.isNewWallet.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: Key.isNewWallet.value(id))
			runOnMainThread {
				self.isNewWalletPublisher.send(newValue)
			}
		}
	}
	
	var swapInAddressIndex: Int {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.integer(forKey: Key.swapInAddressIndex.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.swapInAddressIndex.value(id))
		}
	}
	
	// --------------------------------------------------
	// MARK: Recent Tips
	// --------------------------------------------------
	
	/**
	 * The SendView includes a Quick Tips feature,
	 * where we remember recent tip-percentages used by the user.
	 */
	
	/// Most recent is at index 0
	var recentTipPercents: [Int] {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.recentTipPercents.value(id))?.jsonDecode() ?? []
		}
	}
	
	func addRecentTipPercent(_ percent: Int) {
		var recents = self.recentTipPercents
		if let idx = recents.firstIndex(of: percent) {
			recents.remove(at: idx)
		}
		recents.insert(percent, at: 0)
		while recents.count > 6 {
			recents.removeLast()
		}
		
		defaults.set(recents.jsonEncode(), forKey: Key.recentTipPercents.value(id))
	}

	// --------------------------------------------------
	// MARK: Backup
	// --------------------------------------------------
	
	lazy private(set) var backupSeed: Prefs_BackupSeed = {
		return Prefs_BackupSeed(id: id)
	}()
	
	lazy private(set) var backupTransactions: Prefs_BackupTransactions = {
		return Prefs_BackupTransactions(id: id)
	}()
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet() {
		log.trace(#function)
		
		for key in Key.allCases {
			defaults.removeObject(forKey: key.value(id))
		}

		defaults.removeObject(forKey: KeyDeprecated.showChannelsRemoteBalance.rawValue)
		defaults.removeObject(forKey: KeyDeprecated.recentPaymentSeconds.rawValue)
		defaults.removeObject(forKey: KeyDeprecated.maxFees.rawValue)
		defaults.removeObject(forKey: KeyDeprecated.hasUpgradedSeedCloudBackups_v1.rawValue)
		defaults.removeObject(forKey: "\(KeyDeprecated.hasDownloadedContacts_v1.rawValue)-\(id)")
		
		Prefs.didResetWallet(id)
	}
	
	// --------------------------------------------------
	// MARK: Debugging
	// --------------------------------------------------
	
	@inline(__always)
	func maybeLogDefaultAccess(_ functionName: String) {
	#if DEBUG
		if isDefault {
			log.info("Default access: \(functionName)")
		}
	#endif
	}
	
	#if DEBUG
	static func valueDescription(_ prefix: String, _ value: Any) -> String? {
		
		switch prefix {
		case Key.defaultPaymentDescription.prefix:
			return printString(value)
			
		case Key.invoiceExpirationDays.prefix:
			return printInt(value)
		
		case Key.hideAmounts.prefix:
			return printBool(value)
		
		case Key.showOriginalFiatAmount.prefix:
			return printBool(value)
			
		case Key.recentPaymentsConfig.prefix:
			let desc = if let data = value as? Data, let rpc: RecentPaymentsConfig = data.jsonDecode() {
				rpc.id
			} else { "unknown" }
			
			return "<RecentPaymentsConfig: \(desc)>"
			
		case Key.hasUpgradedSeedCloudBackups.prefix:
			return printBool(value)
			
		case Key.serverMessageReadIndex.prefix:
			return printInt(value)
			
		case Key.allowOverpayment.prefix:
			return printBool(value)
			
		case Key.doNotShowChannelImpactWarning.prefix:
			return printBool(value)
			
		case Key.isNewWallet.prefix:
			return printBool(value)
			
		case Key.swapInAddressIndex.prefix:
			return printInt(value)
		
		case Key.recentTipPercents.prefix:
			let desc = if let data = value as? Data, let array: [Int] = data.jsonDecode() {
				array.description
			} else { "unknown" }
			
			return "<[Int]: \(desc)>"
			
		default:
			return nil
		}
	}
	#endif
}
