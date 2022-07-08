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
	
	fileprivate enum Key: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
		case theme
		case fcmTokenInfo
		case pushPermissionQuery
		case electrumConfig
		case isTorEnabled
		case defaultPaymentDescription
		case showChannelsRemoteBalance
		case currencyConverterList
		case recentTipPercents
		case isNewWallet
		case invoiceExpirationDays
		case maxFees
		case hideAmountsOnHomeScreen
	}
	
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

	var currencyType: CurrencyType {
		get {
			let key = Key.currencyType.rawValue
			let saved: CurrencyType? = defaults.getCodable(forKey: key)
			return saved ?? CurrencyType.bitcoin
		}
		set {
			let key = Key.currencyType.rawValue
			defaults.setCodable(value: newValue, forKey: key)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		return CurrentValueSubject<FiatCurrency, Never>(self.fiatCurrency)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			let key = Key.fiatCurrency.rawValue
			var saved: FiatCurrency? = nil
			if let str = defaults.string(forKey: key) {
				saved = FiatCurrency.deserialize(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			let key = Key.fiatCurrency.rawValue
			let str = newValue.serialize()
			defaults.set(str, forKey: key)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		return CurrentValueSubject<BitcoinUnit, Never>(self.bitcoinUnit)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			let key = Key.bitcoinUnit.rawValue
			var saved: BitcoinUnit? = nil
			if let str = defaults.string(forKey: key) {
				saved = BitcoinUnit.deserialize(str)
			}
			return saved ?? BitcoinUnit.sat
		}
		set {
			let key = Key.bitcoinUnit.rawValue
			let str = newValue.serialize()
			defaults.set(str, forKey: key)
			bitcoinUnitPublisher.send(newValue)
		}
	}
	
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
	
	lazy private(set) var electrumConfigPublisher: CurrentValueSubject<ElectrumConfigPrefs?, Never> = {
		return CurrentValueSubject<ElectrumConfigPrefs?, Never>(self.electrumConfig)
	}()
	
	var electrumConfig: ElectrumConfigPrefs? {
		get {
			let key = Key.electrumConfig.rawValue
			let saved: ElectrumConfigPrefs? = defaults.getCodable(forKey: key)
			return saved
		}
		set {
			let key = Key.electrumConfig.rawValue
			defaults.setCodable(value: newValue, forKey: key)
			electrumConfigPublisher.send(newValue)
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
			log.debug("Prefs.maxFees: \(String(describing: newValue))")
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
	// MARK: Currency Conversion
	// --------------------------------------------------
	
	lazy private(set) var currencyConverterListPublisher: CurrentValueSubject<[Currency], Never> = {
		return CurrentValueSubject<[Currency], Never>(self.currencyConverterList)
	}()
	
	var currencyConverterList: [Currency] {
		get {
			if let list = defaults.string(forKey: Key.currencyConverterList.rawValue) {
				log.debug("get: currencyConverterList = \(list)")
				return Currency.deserializeList(list)
			} else {
				log.debug("get: currencyConverterList = nil")
				return [Currency]()
			}
		}
		set {
			if newValue.isEmpty {
				log.debug("set: currencyConverterList = nil")
				defaults.removeObject(forKey: Key.currencyConverterList.rawValue)
			} else {
				let list = Currency.serializeList(newValue)
				log.debug("set: currencyConverterList = \(list)")
				defaults.set(list, forKey: Key.currencyConverterList.rawValue)
			}
		}
	}
	
	var preferredFiatCurrencies: [FiatCurrency] {
		get {
			var resultArray = [self.fiatCurrency]
			var resultSet = Set<FiatCurrency>(resultArray)
			
			for currency in self.currencyConverterList {
				if case .fiat(let fiat) = currency {
					let (inserted, _) = resultSet.insert(fiat)
					if inserted {
						resultArray.append(fiat)
					}
				}
			}
			
			return resultArray
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
	
	var fcmTokenInfo: FcmTokenInfo? {
		get {
			let key = Key.fcmTokenInfo.rawValue
			let result: FcmTokenInfo? = defaults.getCodable(forKey: key)
			return result
		}
		set {
			let key = Key.fcmTokenInfo.rawValue
			defaults.setCodable(value: newValue, forKey: key)
		}
	}
	
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
}
