import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "GroupPrefs+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum Key: CaseIterable {
	case currencyType
	case fiatCurrency
	case bitcoinUnit
	case currencyConverterList
	case electrumConfig
	case isTorEnabled
	case discreetNotifications
	case liquidityPolicy
	case srvExtConnection
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String {
		switch self {
			case .currencyType          : return "currencyType"
			case .fiatCurrency          : return "fiatCurrency"
			case .bitcoinUnit           : return "bitcoinUnit"
			case .currencyConverterList : return "currencyConverterList"
			case .electrumConfig        : return "electrumConfig"
			case .isTorEnabled          : return "isTorEnabled"
			case .discreetNotifications : return "discreetNotifications"
			case .liquidityPolicy       : return "liquidityPolicy"
			case .srvExtConnection      : return "srvExtConnection"
		}
	}
	
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

/// Group preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
///
class GroupPrefs_Wallet {
	
	private static var defaults: UserDefaults {
		return GroupPrefs.defaults
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
	// MARK: Currencies
	// --------------------------------------------------
	
	var currencyType: CurrencyType {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.currencyType.value(id))?.jsonDecode() ?? .bitcoin
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: Key.currencyType.value(id))
		}
	}
	
	lazy private(set) var fiatCurrencyPublisher = {
		CurrentValueSubject<FiatCurrency, Never>(self.fiatCurrency)
	}()

	var fiatCurrency: FiatCurrency {
		get {
			maybeLogDefaultAccess(#function)
			return FiatCurrency.deserialize(defaults.string(forKey: Key.fiatCurrency.value(id))) ??
			FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			defaults.set(newValue.serialize(), forKey: Key.fiatCurrency.value(id))
			runOnMainThread {
				self.fiatCurrencyPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var bitcoinUnitPublisher = {
		CurrentValueSubject<BitcoinUnit, Never>(self.bitcoinUnit)
	}()

	var bitcoinUnit: BitcoinUnit {
		get {
			maybeLogDefaultAccess(#function)
			return BitcoinUnit.deserialize(defaults.string(forKey: Key.bitcoinUnit.value(id))) ??
			BitcoinUnit.sat
		}
		set {
			defaults.set(newValue.serialize(), forKey: Key.bitcoinUnit.value(id))
			runOnMainThread {
				self.bitcoinUnitPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var currencyConverterListPublisher = {
		CurrentValueSubject<[Currency], Never>(self.currencyConverterList)
	}()
	
	var currencyConverterList: [Currency] {
		get {
			maybeLogDefaultAccess(#function)
			return Currency.deserializeList(defaults.string(forKey: Key.currencyConverterList.value(id)))
		}
		set {
			defaults.set(Currency.serializeList(newValue), forKey: Key.currencyConverterList.value(id))
			runOnMainThread {
				self.currencyConverterListPublisher.send(newValue)
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
	// MARK: User Config
	// --------------------------------------------------
	
	lazy private(set) var electrumConfigPublisher = {
		CurrentValueSubject<ElectrumConfigPrefs?, Never>(self.electrumConfig)
	}()

	var electrumConfig: ElectrumConfigPrefs? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.electrumConfig.value(id))?.jsonDecode()
		}
		set {
			defaults.set(newValue?.jsonEncode(), forKey: Key.electrumConfig.value(id))
			runOnMainThread {
				self.electrumConfigPublisher.send(newValue)
			}
		}
	}

	lazy private(set) var isTorEnabledPublisher = {
		CurrentValueSubject<Bool, Never>(self.isTorEnabled)
	}()

	var isTorEnabled: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.isTorEnabled.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.isTorEnabled.value(id))
			runOnMainThread {
				self.isTorEnabledPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var liquidityPolicyPublisher = {
		CurrentValueSubject<LiquidityPolicy, Never>(self.liquidityPolicy)
	}()
	
	var liquidityPolicy: LiquidityPolicy {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.liquidityPolicy.value(id))?.jsonDecode() ??
			LiquidityPolicy.defaultPolicy()
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: Key.liquidityPolicy.value(id))
			runOnMainThread {
				self.liquidityPolicyPublisher.send(newValue)
			}
		}
	}
	
	lazy private(set) var srvExtConnectionPublisher = {
		CurrentValueSubject<Date, Never>(self.srvExtConnection)
	}()
	
	var srvExtConnection: Date {
		get {
			maybeLogDefaultAccess(#function)
			return Date(timeIntervalSince1970: defaults.double(forKey: Key.srvExtConnection.value(id)))
		}
		set {
			defaults.set(newValue.timeIntervalSince1970, forKey: Key.srvExtConnection.value(id))
			runOnMainThread {
				self.srvExtConnectionPublisher.send(newValue)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	var discreetNotifications: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.discreetNotifications.value(id))
		}
		set {
			defaults.setValue(newValue, forKey: Key.discreetNotifications.value(id))
		}
	}
	
	// --------------------------------------------------
	// MARK: Load Wallet
	// --------------------------------------------------
	
	static func loadWallet(_ walletId: WalletIdentifier) {
		log.trace(#function)
		
		let d = Self.defaults
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
	}
	
	// --------------------------------------------------
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet() {
		log.trace(#function)
		
		let d = self.defaults
		for key in Key.allCases {
			d.removeObject(forKey: key.value(id))
		}
	}

	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	static func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) -> Void {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "40") {
			performMigration_toBuild40()
		}
		if targetBuild.isVersion(equalTo: "65") {
			performMigration_toBuild65()
		}
		if targetBuild.isVersion(equalTo: "92") {
			performMigration_toBuild92()
		}
	}
	
	private static func performMigration_toBuild40() {
		log.trace(#function)
		
		migrateToGroup(Key.currencyType)
		migrateToGroup(Key.bitcoinUnit)
		migrateToGroup(Key.fiatCurrency)
		migrateToGroup(Key.currencyConverterList)
		migrateToGroup(Key.electrumConfig)
	}
	
	private static func performMigration_toBuild65() {
		log.trace(#function)
		
		migrateToGroup(Key.liquidityPolicy)
	}
	
	private static func migrateToGroup(_ key: Key) {
		
		let savedGrp = UserDefaults.group.value(forKey: key.deprecatedValue)
		if savedGrp == nil {
			
			let savedStd = UserDefaults.standard.value(forKey: key.deprecatedValue)
			if savedStd != nil {
				
				UserDefaults.group.set(savedStd, forKey: key.deprecatedValue)
				UserDefaults.standard.removeObject(forKey: key.deprecatedValue)
			}
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
		
		GroupPrefs_Global.performMigration_toBuild92()
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
		
		if GroupPrefs_Global.isKnownKey(key) { return true }
		return false
	}
	
	private static func valueDescription(_ key: String, _ value: Any) -> String {
		
		let printBool = {() -> String in
			let desc = (value as? NSNumber)?.boolValue.description ?? "unknown"
			return "<Bool: \(desc)>"
		}
		
		switch key {
		case Key.currencyType.prefix:
			let desc = if let data = value as? Data, let type: CurrencyType = data.jsonDecode() {
				type.rawValue
			} else { "unknown" }
			
			return "<CurrencyType: \(desc)>"
			
		case Key.fiatCurrency.prefix:
			let desc = if let str = value as? String, let fc = FiatCurrency.deserialize(str) {
				fc.shortName
			} else { "unknown" }
			
			return "<FiatCurrency: \(desc)>"
			
		case Key.bitcoinUnit.prefix:
			let desc = if let str = value as? String, let bu = BitcoinUnit.deserialize(str) {
				bu.shortName
			} else { "unknown" }
			
			return "<BitcoinUnit: \(desc)>"
		
		case Key.currencyConverterList.prefix:
			let desc = if let str = value as? String {
				Currency.deserializeList(str).description
			} else { "unknown" }
			
			return "<CurrencyList: \(desc)>"
		
		case Key.electrumConfig.prefix:
			let desc = if let data = value as? Data, let prefs: ElectrumConfigPrefs = data.jsonDecode() {
				prefs.description
			} else { "unknown" }
			
			return "<ElectrumConfigPrefs: \(desc)>"
			
		case Key.isTorEnabled.prefix:
			return printBool()
			
		case Key.discreetNotifications.prefix:
			return printBool()
			
		case Key.liquidityPolicy.prefix:
			let desc = if let data = value as? Data, let policy: LiquidityPolicy = data.jsonDecode() {
				policy.description
			} else { "unknown" }
			
			return "<LiquidityPolicy: \(desc)>"
			
		case Key.srvExtConnection.prefix:
			let desc = if let num = value as? NSNumber {
				Date(timeIntervalSince1970: num.doubleValue).description
			} else { "unknown" }
			
			return "<Date: \(desc)>"
			
		default:
			return GroupPrefs_Global.valueDescription(key, value) ??
			"<?>"
		}
	}
	#endif
}
