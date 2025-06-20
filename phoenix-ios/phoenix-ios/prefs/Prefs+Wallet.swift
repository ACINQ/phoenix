import Foundation
import Combine

fileprivate let filename = "Prefs+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
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
	
	var deprecatedValue: String {
		return prefix
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
	
	var hasMergedChannelsForSplicing: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.hasMergedChannelsForSplicing.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.hasMergedChannelsForSplicing.value(id))
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
	// MARK: Load Wallet
	// --------------------------------------------------
	
	static func loadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		let d = self.defaults
		let oldId = PREFS_DEFAULT_ID
		let newId = walletId.prefsKeySuffix
		
		for key in Key.allCases {
			let oldKey = key.value(oldId)
			if let value = d.object(forKey: oldKey) {
				
				let newKey = key.value(newId)
				if d.object(forKey: newKey) == nil {
					log.debug("move: \(oldKey) > \(newKey)")
					d.set(value, forKey: newKey)
				} else {
					log.debug("delete: \(oldKey)")
				}
				
				d.removeObject(forKey: oldKey)
			}
		}
		
		Prefs_BackupSeed.loadWallet(walletId)
		Prefs_BackupTransactions.loadWallet(walletId)
	}
	
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
		
		self.backupSeed.resetWallet()
		self.backupTransactions.resetWallet()
	}

	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "44") {
			performMigration_toBuild44()
		}
		if targetBuild.isVersion(equalTo: "92") {
			performMigration_toBuild92()
		}
	}
	
	private static func performMigration_toBuild44() {
		log.trace(#function)
		
		let d = self.defaults
		let oldKey = KeyDeprecated.recentPaymentSeconds.rawValue
		let newKey = Key.recentPaymentsConfig.deprecatedValue
		
		if d.object(forKey: oldKey) != nil {
			let seconds = d.integer(forKey: oldKey)
			if seconds <= 0 {
				let newValue = RecentPaymentsConfig.inFlightOnly
				d.set(newValue.jsonEncode(), forKey: newKey)
			} else {
				let newValue = RecentPaymentsConfig.withinTime(seconds: seconds)
				d.set(newValue.jsonEncode(), forKey: newKey)
			}
			
			d.removeObject(forKey: oldKey)
		}
	}
	
	private static func performMigration_toBuild92() {
		log.trace(#function)
		
		let d = self.defaults
		let newId = PREFS_DEFAULT_ID
		
		for key in Key.allCases {
			let oldKey = key.deprecatedValue
			if let value = d.object(forKey: oldKey) {
				
				let newKey = key.value(newId)
				if d.object(forKey: newKey) == nil {
					log.debug("move: \(oldKey) > \(newKey)")
					d.set(value, forKey: newKey)
				} else {
					log.debug("delete: \(oldKey)")
				}
				
				d.removeObject(forKey: oldKey)
			}
		}
		
		Prefs_Global.performMigration_toBuild92()
		Prefs_BackupSeed.performMigration_toBuild92()
		Prefs_BackupTransactions.performMigration_toBuild92()
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
	static func printAllKeyValues() {
		
		let output = self.defaults.dump(
			isKnownKey: self.isKnownKey,
			valueDescription: self.valueDescription
		)
		log.debug("\(output)")
	}
	
	private static func isKnownKey(_ key: String) -> Bool {
		
		for knownKey in Key.allCases {
			if key.hasPrefix(knownKey.prefix) {
				return true
			}
		}
		
		if Prefs_BackupSeed.isKnownKey(key) { return true }
		if Prefs_BackupTransactions.isKnownKey(key) { return true }
		if Prefs_Global.isKnownKey(key) { return true }
		return false
	}
	
	private static func valueDescription(_ key: String, _ value: Any) -> String {
		
		let printString = {() -> String in
			let desc = (value as? String) ?? "unknown"
			return "<String: \(desc)>"
		}
		
		let printInt = {() -> String in
			let desc = (value as? NSNumber)?.intValue.description ?? "unknown"
			return "<Int: \(desc)>"
		}
		
		let printBool = {() -> String in
			let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
			return "<Bool: \(desc)>"
		}
		
		switch key {
		case Key.defaultPaymentDescription.prefix:
			return printString()
			
		case Key.invoiceExpirationDays.prefix:
			return printInt()
		
		case Key.hideAmounts.prefix:
			return printBool()
		
		case Key.showOriginalFiatAmount.prefix:
			return printBool()
			
		case Key.recentPaymentsConfig.prefix:
			let desc = if let data = value as? Data, let rpc: RecentPaymentsConfig = data.jsonDecode() {
				rpc.id
			} else { "unknown" }
			
			return "<RecentPaymentsConfig: \(desc)>"
		
		case Key.hasMergedChannelsForSplicing.prefix:
			return printBool()
			
		case Key.hasUpgradedSeedCloudBackups.prefix:
			return printBool()
			
		case Key.serverMessageReadIndex.prefix:
			return printInt()
			
		case Key.allowOverpayment.prefix:
			return printBool()
			
		case Key.doNotShowChannelImpactWarning.prefix:
			return printBool()
			
		case Key.isNewWallet.prefix:
			return printBool()
			
		case Key.swapInAddressIndex.prefix:
			return printInt()
		
		case Key.recentTipPercents.prefix:
			let desc = if let data = value as? Data, let array: [Int] = data.jsonDecode() {
				array.description
			} else { "unknown" }
			
			return "<[Int]: \(desc)>"
			
		default:
			return Prefs_BackupSeed.valueDescription(key, value) ??
			       Prefs_BackupTransactions.valueDescription(key, value) ??
			       Prefs_Global.valueDescription(key, value) ??
			       "<?>"
		}
	}
	#endif
}
