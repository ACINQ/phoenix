import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "Prefs"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum Key: String {
	case theme
	case defaultPaymentDescription
	case showChannelsRemoteBalance
	case recentTipPercents
	case isNewWallet
	case invoiceExpirationDays
	case maxFees
	case hideAmountsOnHomeScreen
	case recentPaymentsConfig
}

fileprivate enum KeyDeprecated: String {
	case recentPaymentSeconds
}

/// Standard app preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are NOT shared with other extensions bundled in the app,
/// such as the notification-service-extension. For preferences shared with extensions, see GroupPrefs.
///
class Prefs {
	
	public static let shared = Prefs()
	
	private init() {
		UserDefaults.standard.register(defaults: [
			Key.isNewWallet.rawValue: true,
			Key.invoiceExpirationDays.rawValue: 7
		])
	}
	
	var defaults: UserDefaults {
		return UserDefaults.standard
	}
	
	// --------------------------------------------------
	// MARK: User Options
	// --------------------------------------------------
	
	lazy private(set) var themePublisher: AnyPublisher<Theme, Never> = {
		defaults.publisher(for: \.theme, options: [.new])
			.map({ (data: Data?) -> Theme in
				data?.jsonDecode() ?? self.defaultTheme
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()

	private let defaultTheme = Theme.system

	var theme: Theme {
		get { defaults.theme?.jsonDecode() ?? defaultTheme }
		set { defaults.theme = newValue.jsonEncode() }
	}
	
	var defaultPaymentDescription: String? {
		get { defaults.defaultPaymentDescription }
		set { defaults.defaultPaymentDescription = newValue }
	}
	
	var showChannelsRemoteBalance: Bool {
		get { defaults.showChannelsRemoteBalance }
		set { defaults.showChannelsRemoteBalance = newValue }
	}
	
	var invoiceExpirationDays: Int {
		get { defaults.invoiceExpirationDays }
		set { defaults.invoiceExpirationDays = newValue	}
	}
	
	var invoiceExpirationSeconds: Int64 {
		return Int64(invoiceExpirationDays) * Int64(60 * 60 * 24)
	}
	
	lazy private(set) var maxFeesPublisher: AnyPublisher<MaxFees?, Never> = {
		defaults.publisher(for: \.maxFees, options: [.new])
			.map({ (data: Data?) -> MaxFees? in
				data?.jsonDecode()
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()
	
	var maxFees: MaxFees? {
		get { defaults.maxFees?.jsonDecode() }
		set { defaults.maxFees = newValue?.jsonEncode() }
	}
	
	var hideAmountsOnHomeScreen: Bool {
		get { defaults.hideAmountsOnHomeScreen }
		set { defaults.hideAmountsOnHomeScreen = newValue }
	}
	
	lazy private(set) var recentPaymentsConfigPublisher: AnyPublisher<RecentPaymentsConfig, Never> = {
		defaults.publisher(for: \.recentPaymentsConfig, options: [.new])
			.map({ (data: Data?) -> RecentPaymentsConfig in
				data?.jsonDecode() ?? self.defaultRecentPaymentsConfig
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()
	
	let defaultRecentPaymentsConfig = RecentPaymentsConfig.mostRecent(count: 3)
	
	var recentPaymentsConfig: RecentPaymentsConfig {
		get { defaults.recentPaymentsConfig?.jsonDecode() ?? defaultRecentPaymentsConfig }
		set { defaults.recentPaymentsConfig = newValue.jsonEncode() }
	}
	
	// --------------------------------------------------
	// MARK: Wallet State
	// --------------------------------------------------
	
	/**
	 * Set to true, until the user has funded their wallet at least once.
	 * A false value does NOT indicate that the wallet has funds.
	 * Just that the wallet had either a non-zero balance, or a transaction, at least once.
	 */
	var isNewWallet: Bool {
		get { defaults.isNewWallet }
		set { defaults.isNewWallet = newValue }
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
		get { defaults.recentTipPercents?.jsonDecode() ?? [] }
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
		
		defaults.recentTipPercents = recents.jsonEncode()
	}

	// --------------------------------------------------
	// MARK: Backup
	// --------------------------------------------------
	
	lazy private(set) var backupTransactions: Prefs_BackupTransactions = {
		return Prefs_BackupTransactions()
	}()
	
	lazy private(set) var backupSeed: Prefs_BackupSeed = {
		return Prefs_BackupSeed()
	}()

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
		let newKey = Key.recentPaymentsConfig.rawValue
		
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
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet(encryptedNodeId: String) {

		// Purposefully not resetting:
		// - Key.theme: App feels weird when this changes unexpectedly.

		defaults.removeObject(forKey: Key.defaultPaymentDescription.rawValue)
		defaults.removeObject(forKey: Key.showChannelsRemoteBalance.rawValue)
		defaults.removeObject(forKey: Key.recentTipPercents.rawValue)
		defaults.removeObject(forKey: Key.isNewWallet.rawValue)
		defaults.removeObject(forKey: Key.invoiceExpirationDays.rawValue)
		defaults.removeObject(forKey: Key.maxFees.rawValue)
		defaults.removeObject(forKey: Key.hideAmountsOnHomeScreen.rawValue)
		defaults.removeObject(forKey: Key.recentPaymentsConfig.rawValue)
		
		self.backupTransactions.resetWallet(encryptedNodeId: encryptedNodeId)
		self.backupSeed.resetWallet(encryptedNodeId: encryptedNodeId)
	}
}

extension UserDefaults {

	@objc fileprivate var theme: Data? {
		get { data(forKey: Key.theme.rawValue) }
		set { set(newValue, forKey: Key.theme.rawValue) }
	}

	@objc fileprivate var defaultPaymentDescription: String? {
		get { string(forKey: Key.defaultPaymentDescription.rawValue) }
		set { setValue(newValue, forKey: Key.defaultPaymentDescription.rawValue) }
	}

	@objc fileprivate var showChannelsRemoteBalance: Bool {
		get { bool(forKey: Key.showChannelsRemoteBalance.rawValue) }
		set { set(newValue, forKey: Key.showChannelsRemoteBalance.rawValue) }
	}

	@objc fileprivate var invoiceExpirationDays: Int {
		get { integer(forKey: Key.invoiceExpirationDays.rawValue) }
		set { set(newValue, forKey: Key.invoiceExpirationDays.rawValue) }
	}

	@objc fileprivate var maxFees: Data? {
		get { data(forKey: Key.maxFees.rawValue) }
		set { set(newValue, forKey: Key.maxFees.rawValue) }
	}

	@objc fileprivate var hideAmountsOnHomeScreen: Bool {
		get { bool(forKey: Key.hideAmountsOnHomeScreen.rawValue) }
		set { set(newValue, forKey: Key.hideAmountsOnHomeScreen.rawValue) }
	}
	
	@objc fileprivate var recentPaymentsConfig: Data? {
		get { data(forKey: Key.recentPaymentsConfig.rawValue) }
		set { set(newValue, forKey: Key.recentPaymentsConfig.rawValue) }
	}

	@objc fileprivate var isNewWallet: Bool {
		get { bool(forKey: Key.isNewWallet.rawValue) }
		set { set(newValue, forKey: Key.isNewWallet.rawValue) }
	}

	@objc fileprivate var recentTipPercents: Data? {
		get { data(forKey: Key.recentTipPercents.rawValue) }
		set { set(newValue, forKey: Key.recentTipPercents.rawValue) }
	}
}
