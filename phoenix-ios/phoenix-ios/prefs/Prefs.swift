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
	case pushPermissionQuery
	case defaultPaymentDescription
	case showChannelsRemoteBalance
	case recentTipPercents
	case isNewWallet
	case invoiceExpirationDays
	case maxFees
	case hideAmountsOnHomeScreen
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
			Key.invoiceExpirationDays.rawValue: 7,
			Key.recentPaymentSeconds.rawValue: (60 * 60 * 24 * 3)
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
	
	lazy private(set) var recentPaymentSecondsPublisher: AnyPublisher<Int, Never> = {
		defaults.publisher(for: \.recentPaymentSeconds, options: [.new])
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()
	
	var recentPaymentSeconds: Int {
		get { defaults.recentPaymentSeconds }
		set { defaults.recentPaymentSeconds = newValue }
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
	// MARK: Push Notifications
	// --------------------------------------------------
	
	var pushPermissionQuery: PushPermissionQuery {
		get { defaults.pushPermissionQuery?.jsonDecode() ?? .neverAskedUser }
		set { defaults.pushPermissionQuery = newValue.jsonEncode() }
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
	// MARK: Reset Wallet
	// --------------------------------------------------
	
	func resetWallet(encryptedNodeId: String) {
		
		// Purposefully not resetting:
		// - Key.theme: App feels weird when this changes unexpectedly.
		// - Key.pushPermissionQuery: Not related to wallet; More so to the device.
		
		defaults.removeObject(forKey: Key.defaultPaymentDescription.rawValue)
		defaults.removeObject(forKey: Key.showChannelsRemoteBalance.rawValue)
		defaults.removeObject(forKey: Key.recentTipPercents.rawValue)
		defaults.removeObject(forKey: Key.isNewWallet.rawValue)
		defaults.removeObject(forKey: Key.invoiceExpirationDays.rawValue)
		defaults.removeObject(forKey: Key.maxFees.rawValue)
		defaults.removeObject(forKey: Key.hideAmountsOnHomeScreen.rawValue)
		defaults.removeObject(forKey: Key.recentPaymentSeconds.rawValue)
		
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
	
	@objc fileprivate var recentPaymentSeconds: Int {
		get { integer(forKey: Key.recentPaymentSeconds.rawValue) }
		set { set(newValue, forKey: Key.recentPaymentSeconds.rawValue) }
	}
	
	@objc fileprivate var isNewWallet: Bool {
		get { bool(forKey: Key.isNewWallet.rawValue) }
		set { set(newValue, forKey: Key.isNewWallet.rawValue) }
	}
	
	@objc fileprivate var recentTipPercents: Data? {
		get { data(forKey: Key.recentTipPercents.rawValue) }
		set { set(newValue, forKey: Key.recentTipPercents.rawValue) }
	}
	
	@objc fileprivate var pushPermissionQuery: Data? {
		get { data(forKey: Key.pushPermissionQuery.rawValue) }
		set { set(newValue, forKey: Key.pushPermissionQuery.rawValue) }
	}
}
