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


class Prefs {
	
	private enum Key: String {
		case theme
		case pushPermissionQuery
		case isTorEnabled
		case defaultPaymentDescription
		case showChannelsRemoteBalance
		case recentTipPercents
		case isNewWallet
		case invoiceExpirationDays
		case maxFees
		case hideAmountsOnHomeScreen
		case recentPaymentSeconds
	}
	
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
	
	lazy private(set) var themePublisher: CurrentValueSubject<Theme, Never> = {
		return CurrentValueSubject<Theme, Never>(self.theme)
	}()
	
	var theme: Theme {
		get {
			let key = Key.theme.rawValue
			let saved: Theme? = defaults.getCodable(forKey: key)
			return saved ?? Theme.system
		}
		set {
			let key = Key.theme.rawValue
			defaults.setCodable(value: newValue, forKey: key)
			themePublisher.send(newValue)
		}
	}

	lazy private(set) var isTorEnabledPublisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.isTorEnabled)
	}()

	var isTorEnabled: Bool {
		get {
			 defaults.bool(forKey: Key.isTorEnabled.rawValue)
		}
		set {
			defaults.set(newValue, forKey: Key.isTorEnabled.rawValue)
			isTorEnabledPublisher.send(newValue)
		}
	}
	
	var defaultPaymentDescription: String? {
		get {
			let key = Key.defaultPaymentDescription.rawValue
			let saved: String? = defaults.string(forKey: key)
			return saved
		}
		set {
			let key = Key.defaultPaymentDescription.rawValue
			defaults.setValue(newValue, forKey: key)
		}
	}
	
	var showChannelsRemoteBalance: Bool {
		get {
			defaults.bool(forKey: Key.showChannelsRemoteBalance.rawValue)
		}
		set {
			defaults.set(newValue, forKey: Key.showChannelsRemoteBalance.rawValue)
		}
	}
	
	var invoiceExpirationDays: Int {
		get {
			defaults.integer(forKey: Key.invoiceExpirationDays.rawValue)
		}
		set {
			defaults.set(newValue, forKey: Key.invoiceExpirationDays.rawValue)
		}
	}
	
	lazy private(set) var maxFeesPublisher: CurrentValueSubject<MaxFees?, Never> = {
		return CurrentValueSubject<MaxFees?, Never>(self.maxFees)
	}()
	
	var maxFees: MaxFees? {
		get {
			let key = Key.maxFees.rawValue
			let result: MaxFees? = defaults.getCodable(forKey: key)
			return result
		}
		set {
			let key = Key.maxFees.rawValue
			defaults.setCodable(value: newValue, forKey: key)
			maxFeesPublisher.send(newValue)
		}
	}
	
	var hideAmountsOnHomeScreen: Bool {
		get {
			 defaults.bool(forKey: Key.hideAmountsOnHomeScreen.rawValue)
		}
		set {
			defaults.set(newValue, forKey: Key.hideAmountsOnHomeScreen.rawValue)
		}
	}
	
	lazy private(set) var recentPaymentSecondsPublisher: CurrentValueSubject<Int, Never> = {
		return CurrentValueSubject<Int, Never>(self.recentPaymentSeconds)
	}()
	
	var recentPaymentSeconds: Int {
		get {
			return defaults.integer(forKey: Key.recentPaymentSeconds.rawValue)
		}
		set {
			let oldValue = recentPaymentSeconds
			if oldValue != newValue {
				defaults.set(newValue, forKey: Key.recentPaymentSeconds.rawValue)
				recentPaymentSecondsPublisher.send(newValue)
			}
		}
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
		get {
			 defaults.bool(forKey: Key.isNewWallet.rawValue)
		}
		set {
			defaults.set(newValue, forKey: Key.isNewWallet.rawValue)
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
			let key = Key.recentTipPercents.rawValue
			let saved: [Int]? = defaults.getCodable(forKey: key)
			return saved ?? []
		}
	}
	
	func addRecentTipPercent(_ percent: Int) {
		var recents = recentTipPercents
		if let idx = recents.firstIndex(of: percent) {
			recents.remove(at: idx)
		}
		recents.insert(percent, at: 0)
		while recents.count > 6 {
			recents.removeLast()
		}
		
		let key = Key.recentTipPercents.rawValue
		defaults.setCodable(value: recents, forKey: key)
	}
	
	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	var pushPermissionQuery: PushPermissionQuery {
		get {
			let key = Key.pushPermissionQuery.rawValue
			let saved: PushPermissionQuery? = defaults.getCodable(forKey: key)
			return saved ?? .neverAskedUser
		}
		set {
			let key = Key.pushPermissionQuery.rawValue
			defaults.setCodable(value: newValue, forKey: key)
		}
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
	// MARK: Close Wallet
	// --------------------------------------------------
	
	func closeWallet(encryptedNodeId: String) {
		
		// Purposefully not resetting:
		// - Key.theme: App feels weird when this changes unexpectedly.
		// - Key.pushPermissionQuery: Not related to wallet; More so to the device.
		
		defaults.removeObject(forKey: Key.isTorEnabled.rawValue)
		defaults.removeObject(forKey: Key.defaultPaymentDescription.rawValue)
		defaults.removeObject(forKey: Key.showChannelsRemoteBalance.rawValue)
		defaults.removeObject(forKey: Key.recentTipPercents.rawValue)
		defaults.removeObject(forKey: Key.isNewWallet.rawValue)
		defaults.removeObject(forKey: Key.invoiceExpirationDays.rawValue)
		defaults.removeObject(forKey: Key.maxFees.rawValue)
		defaults.removeObject(forKey: Key.hideAmountsOnHomeScreen.rawValue)
		defaults.removeObject(forKey: Key.recentPaymentSeconds.rawValue)
		
		self.backupTransactions.closeWallet(encryptedNodeId: encryptedNodeId)
		self.backupSeed.closeWallet(encryptedNodeId: encryptedNodeId)
	}
}
