import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "GroupPrefs"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


extension UserDefaults {
	static var group: UserDefaults {
		return UserDefaults(suiteName: "group.co.acinq.phoenix")!
	}
}

fileprivate enum Key: String {
	case currencyType
	case fiatCurrency
	case bitcoinUnit
	case currencyConverterList
	case electrumConfig
	case isTorEnabled
	case badgeCount
	case discreetNotifications
}

/// Group preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
///
class GroupPrefs {
	
	public static let shared = GroupPrefs()
	
	var defaults: UserDefaults {
		return UserDefaults.group
	}
	
	// --------------------------------------------------
	// MARK: Currencies
	// --------------------------------------------------
	
	var currencyType: CurrencyType {
		get { defaults.currencyType?.jsonDecode() ?? .bitcoin }
		set { defaults.currencyType = newValue.jsonEncode() }
	}
	
	lazy private(set) var fiatCurrencyPublisher: AnyPublisher<FiatCurrency, Never> = {
		defaults.publisher(for: \.fiatCurrency, options: [.initial, .new])
			.map({ (str: String?) -> FiatCurrency in
				FiatCurrency.deserialize(str) ?? self.defaultFiatCurrency()
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()

	private func defaultFiatCurrency() -> FiatCurrency {
		return FiatCurrency.localeDefault() ?? FiatCurrency.usd
	}

	var fiatCurrency: FiatCurrency {
		get { FiatCurrency.deserialize(defaults.fiatCurrency) ?? defaultFiatCurrency() }
		set { defaults.fiatCurrency = newValue.serialize() }
	}
	
	lazy private(set) var bitcoinUnitPublisher: AnyPublisher<BitcoinUnit, Never> = {
		defaults.publisher(for: \.bitcoinUnit, options: [.initial, .new])
			.map({ (str: String?) -> BitcoinUnit in
				BitcoinUnit.deserialize(str) ?? self.defaultBitcoinUnit
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()

	private let defaultBitcoinUnit = BitcoinUnit.sat

	var bitcoinUnit: BitcoinUnit {
		get { BitcoinUnit.deserialize(defaults.bitcoinUnit) ?? defaultBitcoinUnit }
		set { defaults.bitcoinUnit = newValue.serialize() }
	}
	
	lazy private(set) var currencyConverterListPublisher: AnyPublisher<[Currency], Never> = {
		defaults.publisher(for: \.currencyConverterList, options: [.initial, .new])
			.map({ (str: String?) -> [Currency] in
				Currency.deserializeList(str)
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()
	
	var currencyConverterList: [Currency] {
		get { Currency.deserializeList(defaults.currencyConverterList) }
		set { defaults.currencyConverterList = Currency.serializeList(newValue) }
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
	// MARK: User Config
	// --------------------------------------------------
	
	lazy private(set) var electrumConfigPublisher: AnyPublisher<ElectrumConfigPrefs?, Never> = {
		defaults.publisher(for: \.electrumConfig, options: [.initial, .new])
			.map({ (data: Data?) -> ElectrumConfigPrefs? in
				data?.jsonDecode()
			})
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()

	var electrumConfig: ElectrumConfigPrefs? {
		get { defaults.electrumConfig?.jsonDecode() }
		set { defaults.electrumConfig = newValue?.jsonEncode() }
	}

	lazy private(set) var isTorEnabledPublisher: AnyPublisher<Bool, Never> = {
		defaults.publisher(for: \.isTorEnabled, options: [.initial, .new])
			.removeDuplicates()
			.eraseToAnyPublisher()
	}()

	var isTorEnabled: Bool {
		get { defaults.isTorEnabled }
		set { defaults.isTorEnabled = newValue }
	}
	
	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	var badgeCount: Int {
		get { defaults.badgeCount }
		set { defaults.badgeCount = newValue }
	}
	
	var discreetNotifications: Bool {
		get { defaults.discreetNotifications }
		set { defaults.discreetNotifications = newValue }
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet() {

		defaults.removeObject(forKey: Key.currencyType.rawValue)
		defaults.removeObject(forKey: Key.fiatCurrency.rawValue)
		defaults.removeObject(forKey: Key.bitcoinUnit.rawValue)
		defaults.removeObject(forKey: Key.currencyConverterList.rawValue)
		defaults.removeObject(forKey: Key.electrumConfig.rawValue)
		defaults.removeObject(forKey: Key.isTorEnabled.rawValue)
		defaults.removeObject(forKey: Key.badgeCount.rawValue)
		defaults.removeObject(forKey: Key.discreetNotifications.rawValue)
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
		
		if targetBuild.isVersion(equalTo: "40") {
			performMigration_toBuild40()
		}
	}
	
	private func performMigration_toBuild40() {
		log.trace("performMigration_toBuild40()")
		
		let MigrateToGroup = {(key: Key) in
			
			let savedGrp = UserDefaults.group.value(forKey: key.rawValue)
			if savedGrp == nil {
				
				let savedStd = UserDefaults.standard.value(forKey: key.rawValue)
				if savedStd != nil {
					
					UserDefaults.group.set(savedStd, forKey: key.rawValue)
					UserDefaults.standard.removeObject(forKey: key.rawValue)
				}
			}
		}
		
		MigrateToGroup(Key.currencyType)
		MigrateToGroup(Key.bitcoinUnit)
		MigrateToGroup(Key.fiatCurrency)
		MigrateToGroup(Key.currencyConverterList)
		MigrateToGroup(Key.electrumConfig)
	}
}

extension UserDefaults {

	@objc fileprivate var currencyType: Data? {
		get { data(forKey: Key.currencyType.rawValue) }
		set { set(newValue, forKey: Key.currencyType.rawValue) }
	}

	@objc fileprivate var fiatCurrency: String? {
		get { string(forKey: Key.fiatCurrency.rawValue) }
		set { set(newValue, forKey: Key.fiatCurrency.rawValue) }
	}

	@objc fileprivate var bitcoinUnit: String? {
		get { string(forKey: Key.bitcoinUnit.rawValue) }
		set { set(newValue, forKey: Key.bitcoinUnit.rawValue) }
	}

	@objc fileprivate var currencyConverterList: String? {
		get { string(forKey: Key.currencyConverterList.rawValue) }
		set { set(newValue, forKey: Key.currencyConverterList.rawValue) }
	}

	@objc fileprivate var electrumConfig: Data? {
		get { data(forKey: Key.electrumConfig.rawValue) }
		set { set(newValue, forKey: Key.electrumConfig.rawValue) }
	}

	@objc fileprivate var isTorEnabled: Bool {
		get { bool(forKey: Key.isTorEnabled.rawValue) }
		set { set(newValue, forKey: Key.isTorEnabled.rawValue) }
	}
	
	@objc fileprivate var badgeCount: Int {
		get { integer(forKey: Key.badgeCount.rawValue) }
		set { set(newValue, forKey: Key.badgeCount.rawValue) }
	}
	
	@objc fileprivate var discreetNotifications: Bool {
		get { bool(forKey: Key.discreetNotifications.rawValue) }
		set { set(newValue, forKey: Key.discreetNotifications.rawValue) }
	}
}
