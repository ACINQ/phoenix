import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "GroupPrefs+Wallet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate typealias Key = GroupPrefsKey


/// Group preferences, stored in the iOS UserDefaults system.
///
/// Note that the values here are SHARED with other extensions bundled in the app,
/// such as the notification-service-extension.
///
final class GroupPrefs_Wallet: Sendable {
	
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
	
	func fiatCurrencyPublisher() -> AnyAsyncSequence<FiatCurrency> {
		maybeLogDefaultAccess(#function)
		return defaults.observeKey(Key.fiatCurrency.value(id), valueType: String.self)
			.map { Self.deserializeFiatCurrency($0) }
			.eraseToAnyAsyncSequence()
	}
	
	private static func deserializeFiatCurrency(_ str: String?) -> FiatCurrency {
		return FiatCurrency.deserialize(str)
			?? FiatCurrency.localeDefault()
			?? FiatCurrency.usd
	}

	var fiatCurrency: FiatCurrency {
		get {
			maybeLogDefaultAccess(#function)
			let str = defaults.string(forKey: Key.fiatCurrency.value(id))
			return Self.deserializeFiatCurrency(str)
		}
		set {
			defaults.set(newValue.serialize(), forKey: Key.fiatCurrency.value(id))
		}
	}
	
	func bitcoinUnitPublisher() -> AnyAsyncSequence<BitcoinUnit> {
		maybeLogDefaultAccess(#function)
		return defaults.observeKey(Key.bitcoinUnit.value(id), valueType: String.self)
			.map { Self.deserializeBitcoinUnit($0) }
			.eraseToAnyAsyncSequence()
	}

	private static func deserializeBitcoinUnit(_ str: String?) -> BitcoinUnit {
		return BitcoinUnit.deserialize(str)
			?? BitcoinUnit.sat
	}
	
	var bitcoinUnit: BitcoinUnit {
		get {
			maybeLogDefaultAccess(#function)
			let str = defaults.string(forKey: Key.bitcoinUnit.value(id))
			return Self.deserializeBitcoinUnit(str)
		}
		set {
			defaults.set(newValue.serialize(), forKey: Key.bitcoinUnit.value(id))
		}
	}
	
	func currrencyConverterListPublisher() -> AnyAsyncSequence<[Currency]> {
		maybeLogDefaultAccess(#function)
		return defaults.observeKey(Key.currencyConverterList.value(id), valueType: String.self)
			.map { Currency.deserializeList($0) }
			.eraseToAnyAsyncSequence()
	}
	
	var currencyConverterList: [Currency] {
		get {
			maybeLogDefaultAccess(#function)
			return Currency.deserializeList(defaults.string(forKey: Key.currencyConverterList.value(id)))
		}
		set {
			defaults.set(Currency.serializeList(newValue), forKey: Key.currencyConverterList.value(id))
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

	var electrumConfig: ElectrumConfigPrefs? {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.electrumConfig.value(id))?.jsonDecode()
		}
		set {
			defaults.set(newValue?.jsonEncode(), forKey: Key.electrumConfig.value(id))
		}
	}
	
	func isTorEnabledPubliser() -> AnyAsyncSequence<Bool> {
		return defaults.observeKey(Key.isTorEnabled.value(id), valueType: NSNumber.self)
			.map { $0?.boolValue ?? false }
			.eraseToAnyAsyncSequence()
	}

	var isTorEnabled: Bool {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.bool(forKey: Key.isTorEnabled.value(id))
		}
		set {
			defaults.set(newValue, forKey: Key.isTorEnabled.value(id))
		}
	}
	
	func altLiquidityPolicyPublisher() -> AnyAsyncSequence<LiquidityPolicy> {
		return defaults.observeKey(Key.liquidityPolicy.value(id), valueType: Data.self)
			.map { $0?.jsonDecode() ?? LiquidityPolicy.defaultPolicy() }
			.eraseToAnyAsyncSequence()
	}
	
	var liquidityPolicy: LiquidityPolicy {
		get {
			maybeLogDefaultAccess(#function)
			return defaults.data(forKey: Key.liquidityPolicy.value(id))?.jsonDecode() ??
			LiquidityPolicy.defaultPolicy()
		}
		set {
			defaults.set(newValue.jsonEncode(), forKey: Key.liquidityPolicy.value(id))
		}
	}
	
	func srvExtConnectionPublisher() -> AnyAsyncSequence<Date> {
		maybeLogDefaultAccess(#function)
		return defaults.observeKey(Key.srvExtConnection.value(id), valueType: NSNumber.self)
			.map { $0 ?? NSNumber(value: 0.0) }
			.map { Date(timeIntervalSince1970: $0.doubleValue) }
			.eraseToAnyAsyncSequence()
	}
	
	var srvExtConnection: Date {
		get {
			maybeLogDefaultAccess(#function)
			return Date(timeIntervalSince1970: defaults.double(forKey: Key.srvExtConnection.value(id)))
		}
		set {
			defaults.set(newValue.timeIntervalSince1970, forKey: Key.srvExtConnection.value(id))
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
	// MARK: Reset Wallet
	// --------------------------------------------------

	func resetWallet() {
		log.trace(#function)
		
		let d = self.defaults
		for key in Key.allCases {
			d.removeObject(forKey: key.value(id))
		}
		
		GroupPrefs.didResetWallet(id)
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
			return printBool(value)
			
		case Key.discreetNotifications.prefix:
			return printBool(value)
			
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
			return nil
		}
	}
	#endif
}
