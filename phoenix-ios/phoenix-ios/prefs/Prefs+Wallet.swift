import Foundation
import Combine

fileprivate let filename = "Prefs+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

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
			return defaults.string(forKey: PrefsKey.defaultPaymentDescription.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.defaultPaymentDescription.value(id))
		}
	}
	
	var invoiceExpirationDays: Int {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.integer(forKey: PrefsKey.invoiceExpirationDays.value(id), defaultValue: 7)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.invoiceExpirationDays.value(id))
		}
	}
	
	var invoiceExpirationSeconds: Int64 {
		return Int64(invoiceExpirationDays) * Int64(60 * 60 * 24)
	}
	
	var hideAmounts: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hideAmounts.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hideAmounts.value(id))
		}
	}
	
	lazy private(set) var showOriginalFiatAmountPublisher = {
		CurrentValueSubject<Bool, Never>(self.showOriginalFiatAmount)
	}()
	
	var showOriginalFiatAmount: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.showOriginalFiatAmount.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.showOriginalFiatAmount.value(id))
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
			return defaults.data(forKey: PrefsKey.recentPaymentsConfig.value(id))?.jsonDecode() ??
			RecentPaymentsConfig.mostRecent(count: 3)
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: PrefsKey.recentPaymentsConfig.value(id))
			runOnMainThread {
				self.recentPaymentsConfigPublisher.send(newValue)
			}
		}
	}
	
	var hasMergedChannelsForSplicing: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hasMergedChannelsForSplicing.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hasMergedChannelsForSplicing.value(id))
		}
	}
	
	var hasUpgradedSeedCloudBackups: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.hasUpgradedSeedCloudBackups.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.hasUpgradedSeedCloudBackups.value(id))
		}
	}
  
	lazy private(set) var serverMessageReadIndexPublisher = {
		CurrentValueSubject<Int?, Never>(self.serverMessageReadIndex)
	}()
	
	var serverMessageReadIndex: Int? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.number(forKey: PrefsKey.serverMessageReadIndex.value(id))?.intValue
		}
		set {
			let key = PrefsKey.serverMessageReadIndex.value(id)
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
			return defaults.bool(forKey: PrefsKey.allowOverpayment.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.allowOverpayment.value(id))
			runOnMainThread {
				self.allowOverpaymentPublisher.send(newValue)
			}
		}
	}

	var doNotShowChannelImpactWarning: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: PrefsKey.doNotShowChannelImpactWarning.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.doNotShowChannelImpactWarning.value(id))
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
			return defaults.bool(forKey: PrefsKey.isNewWallet.value(id), defaultValue: true)
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.isNewWallet.value(id))
			runOnMainThread {
				self.isNewWalletPublisher.send(newValue)
			}
		}
	}
	
	var swapInAddressIndex: Int {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.integer(forKey: PrefsKey.swapInAddressIndex.value(id))
		}
		set {
			defaults.set(newValue, forKey: PrefsKey.swapInAddressIndex.value(id))
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
			return defaults.data(forKey: PrefsKey.recentTipPercents.value(id))?.jsonDecode() ?? []
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
		
		defaults.set(recents.jsonEncode(), forKey: PrefsKey.recentTipPercents.value(id))
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
		
		for key in PrefsKey.allCases {
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
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet() {
		log.trace(#function)
		
		for key in PrefsKey.allCases {
			defaults.removeObject(forKey: key.value(id))
		}

		defaults.removeObject(forKey: PrefsKeyDeprecated.showChannelsRemoteBalance.rawValue)
		defaults.removeObject(forKey: PrefsKeyDeprecated.recentPaymentSeconds.rawValue)
		defaults.removeObject(forKey: PrefsKeyDeprecated.maxFees.rawValue)
		defaults.removeObject(forKey: PrefsKeyDeprecated.hasUpgradedSeedCloudBackups_v1.rawValue)
		defaults.removeObject(forKey: "\(PrefsKeyDeprecated.hasDownloadedContacts_v1.rawValue)-\(id)")
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
		let oldKey = PrefsKeyDeprecated.recentPaymentSeconds.rawValue
		let newKey = PrefsKey.recentPaymentsConfig.deprecatedValue
		
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
		
		for key in PrefsKey.allCases {
			let oldKey = key.deprecatedValue
			if let value = d.object(forKey: oldKey) {
				
				let newId = (key.group == .global) ? PREFS_GLOBAL_ID : PREFS_DEFAULT_ID
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
		
		for knownKey in PrefsKey.allCases {
			if key.hasPrefix(knownKey.prefix) {
				return true
			}
		}
		
		return false
	}
	
	private static func valueDescription(_ key: String, _ value: Any) -> String {
		
		switch key {
		case PrefsKey.defaultPaymentDescription.prefix:
			return Prefs.printString(value)
			
		case PrefsKey.invoiceExpirationDays.prefix:
			return Prefs.printInt(value)
		
		case PrefsKey.hideAmounts.prefix:
			return Prefs.printBool(value)
		
		case PrefsKey.showOriginalFiatAmount.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.recentPaymentsConfig.prefix:
			let desc = if let data = value as? Data, let rpc: RecentPaymentsConfig = data.jsonDecode() {
				rpc.id
			} else { "unknown" }
			
			return "<RecentPaymentsConfig: \(desc)>"
		
		case PrefsKey.hasMergedChannelsForSplicing.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.hasUpgradedSeedCloudBackups.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.serverMessageReadIndex.prefix:
			return Prefs.printInt(value)
			
		case PrefsKey.allowOverpayment.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.doNotShowChannelImpactWarning.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.isNewWallet.prefix:
			return Prefs.printBool(value)
			
		case PrefsKey.swapInAddressIndex.prefix:
			return Prefs.printInt(value)
		
		case PrefsKey.recentTipPercents.prefix:
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
