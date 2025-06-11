import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "Prefs"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key {
	case theme
	case defaultPaymentDescription
	case recentTipPercents
	case isNewWallet
	case invoiceExpirationDays
	case hideAmounts
	case showOriginalFiatAmount
	case recentPaymentsConfig
	case hasMergedChannelsForSplicing
	case swapInAddressIndex
	case hasUpgradedSeedCloudBackups
	case serverMessageReadIndex
	case allowOverpayment
	case doNotShowChannelImpactWarning
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .theme                         : return "theme"
			case .defaultPaymentDescription     : return "defaultPaymentDescription"
			case .recentTipPercents             : return "recentTipPercents"
			case .isNewWallet                   : return "isNewWallet"
			case .invoiceExpirationDays         : return "invoiceExpirationDays"
			case .hideAmounts                   : return "hideAmountsOnHomeScreen"
			case .showOriginalFiatAmount        : return "showOriginalFiatAmount"
			case .recentPaymentsConfig          : return "recentPaymentsConfig"
			case .hasMergedChannelsForSplicing  : return "hasMergedChannelsForSplicing"
			case .swapInAddressIndex            : return "swapInAddressIndex"
			case .hasUpgradedSeedCloudBackups   : return "hasUpgradedSeedCloudBackups_v2"
			case .serverMessageReadIndex        : return "serverMessageReadIndex"
			case .allowOverpayment              : return "allowOverpayment"
			case .doNotShowChannelImpactWarning : return "doNotShowChannelImpactWarning"
		}
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

fileprivate enum KeyDeprecated: String {
	case showChannelsRemoteBalance
	case recentPaymentSeconds
	case maxFees
	case hasUpgradedSeedCloudBackups_v1 = "hasUpgradedSeedCloudBackups"
}

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are NOT shared with other extensions bundled in the app,
/// such as the notification-service-extension. For preferences shared with extensions, see GroupPrefs.
///
class Prefs {
	
	private static var instances: [String: Prefs] = [:]
	
	public static var current: Prefs {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet("default")
		}
	}
	
	public static func wallet(_ walletId: WalletIdentifier) -> Prefs {
		return wallet(walletId.prefsKeySuffix)
	}
	
	public static func wallet(_ id: String) -> Prefs {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = Prefs(id: id)
			instances[id] = instance
			return instance
		}
	}
	
	// --------------------------------------------------
	// MARK: Setup
	// --------------------------------------------------
	
	private let id: String
	
	private init(id: String) {
		self.id = id
	}
	
	var defaults: UserDefaults {
		return UserDefaults.standard
	}
	
	// --------------------------------------------------
	// MARK: User Options
	// --------------------------------------------------
	
	lazy private(set) var themePublisher = {
		CurrentValueSubject<Theme, Never>(self.theme)
	}()

	var theme: Theme {
		get { defaults.data(forKey: Key.theme.value(id))?.jsonDecode() ?? Theme.system }
		set {
			defaults.set(newValue, forKey: Key.theme.value(id))
			runOnMainThread {
				self.themePublisher.send(newValue)
			}
		}
	}
	
	var defaultPaymentDescription: String? {
		get { defaults.string(forKey: Key.defaultPaymentDescription.value(id)) }
		set { defaults.set(newValue, forKey: Key.defaultPaymentDescription.value(id)) }
	}
	
	var invoiceExpirationDays: Int {
		get { defaults.integer(forKey: Key.invoiceExpirationDays.value(id), defaultValue: 7) }
		set { defaults.set(newValue, forKey: Key.invoiceExpirationDays.value(id)) }
	}
	
	var invoiceExpirationSeconds: Int64 {
		return Int64(invoiceExpirationDays) * Int64(60 * 60 * 24)
	}
	
	var hideAmounts: Bool {
		get { defaults.bool(forKey: Key.hideAmounts.value(id)) }
		set { defaults.set(newValue, forKey: Key.hideAmounts.value(id)) }
	}
	
	lazy private(set) var showOriginalFiatAmountPublisher = {
		CurrentValueSubject<Bool, Never>(self.showOriginalFiatAmount)
	}()
	
	var showOriginalFiatAmount: Bool {
		get { defaults.bool(forKey: Key.showOriginalFiatAmount.value(id), defaultValue: true) }
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
			defaults.data(forKey: Key.recentPaymentsConfig.value(id))?.jsonDecode() ??
			RecentPaymentsConfig.mostRecent(count: 3)
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: Key.recentPaymentsConfig.value(id))
			runOnMainThread {
				self.recentPaymentsConfigPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var hasMergedChannelsForSplicingPublisher = {
		CurrentValueSubject<Bool, Never>(self.hasMergedChannelsForSplicing)
	}()
	
	var hasMergedChannelsForSplicing: Bool {
		get { defaults.bool(forKey: Key.hasMergedChannelsForSplicing.value(id)) }
		set {
			defaults.set(newValue, forKey: Key.hasMergedChannelsForSplicing.value(id))
			runOnMainThread {
				self.hasMergedChannelsForSplicingPublisher.send(newValue)
			}
		}
	}
	
	var hasUpgradedSeedCloudBackups: Bool {
		get { defaults.bool(forKey: Key.hasUpgradedSeedCloudBackups.value(id)) }
		set { defaults.set(newValue, forKey: Key.hasUpgradedSeedCloudBackups.value(id)) }
	}
  
	lazy private(set) var serverMessageReadIndexPublisher = {
		CurrentValueSubject<Int?, Never>(self.serverMessageReadIndex)
	}()
	
	var serverMessageReadIndex: Int? {
		get { defaults.number(forKey: Key.serverMessageReadIndex.value(id))?.intValue }
		set {
			if let number = newValue {
				defaults.set(NSNumber(value: number), forKey: Key.serverMessageReadIndex.value(id))
			} else {
				defaults.removeObject(forKey: Key.serverMessageReadIndex.value(id))
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
		get { defaults.bool(forKey: Key.allowOverpayment.value(id)) }
		set {
			defaults.set(newValue, forKey: Key.allowOverpayment.value(id))
			runOnMainThread {
				self.allowOverpaymentPublisher.send(newValue)
			}
		}
	}

	var doNotShowChannelImpactWarning: Bool {
		get { defaults.bool(forKey: Key.doNotShowChannelImpactWarning.value(id)) }
		set { defaults.set(newValue, forKey: Key.doNotShowChannelImpactWarning.value(id)) }
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
		get { defaults.bool(forKey: Key.isNewWallet.value(id), defaultValue: true) }
		set {
			defaults.set(newValue, forKey: Key.isNewWallet.value(id))
			runOnMainThread {
				self.isNewWalletPublisher.send(newValue)
			}
		}
	}
	
	var swapInAddressIndex: Int {
		get { defaults.integer(forKey: Key.swapInAddressIndex.value(id)) }
		set { defaults.set(newValue, forKey: Key.swapInAddressIndex.value(id)) }
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
		get { defaults.data(forKey: Key.recentTipPercents.value(id))?.jsonDecode() ?? [] }
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

		// Purposefully not resetting:
		// - Key.theme: App feels weird when this changes unexpectedly.

		defaults.removeObject(forKey: Key.defaultPaymentDescription.value(id))
		defaults.removeObject(forKey: Key.recentTipPercents.value(id))
		defaults.removeObject(forKey: Key.isNewWallet.value(id))
		defaults.removeObject(forKey: Key.invoiceExpirationDays.value(id))
		defaults.removeObject(forKey: Key.hideAmounts.value(id))
		defaults.removeObject(forKey: Key.showOriginalFiatAmount.value(id))
		defaults.removeObject(forKey: Key.recentPaymentsConfig.value(id))
		defaults.removeObject(forKey: Key.hasMergedChannelsForSplicing.value(id))
		defaults.removeObject(forKey: Key.swapInAddressIndex.value(id))
		defaults.removeObject(forKey: Key.hasUpgradedSeedCloudBackups.value(id))
		defaults.removeObject(forKey: Key.serverMessageReadIndex.value(id))
		defaults.removeObject(forKey: Key.allowOverpayment.value(id))
		defaults.removeObject(forKey: Key.doNotShowChannelImpactWarning.value(id))

	//	defaults.removeObject(forKey: KeyDeprecated.showChannelsRemoteBalance.rawValue)
	//	defaults.removeObject(forKey: KeyDeprecated.recentPaymentSeconds.rawValue)
	//	defaults.removeObject(forKey: KeyDeprecated.maxFees.rawValue)
	//	defaults.removeObject(forKey: KeyDeprecated.hasUpgradedSeedCloudBackups_v1.rawValue)
		
		self.backupSeed.resetWallet()
		self.backupTransactions.resetWallet()
	}

	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	public func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) -> Void {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "44") {
			performMigration_toBuild44()
		}
	}
	
	private func performMigration_toBuild44() {
		log.trace("performMigration_toBuild44()")
		
		let oldKey = KeyDeprecated.recentPaymentSeconds.rawValue
		let newKey = Key.recentPaymentsConfig.prefix
		
		if defaults.object(forKey: oldKey) != nil {
			let seconds = defaults.integer(forKey: oldKey)
			if seconds <= 0 {
				let newValue = RecentPaymentsConfig.inFlightOnly
				defaults.set(newValue.jsonEncode(), forKey: newKey)
			} else {
				let newValue = RecentPaymentsConfig.withinTime(seconds: seconds)
				defaults.set(newValue.jsonEncode(), forKey: newKey)
			}
			
			defaults.removeObject(forKey: oldKey)
		}
	}
}
