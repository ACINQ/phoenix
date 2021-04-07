import SwiftUI
import PhoenixShared
import Combine


class Prefs {
	
	public static let shared = Prefs()
	
	private init() {/* must use shared instance */}
	
	private enum Keys: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
		case theme
		case fcmTokenInfo
		case pushPermissionQuery
		case electrumConfig
		case isTorEnabled
	}
	
	lazy private(set) var currencyTypePublisher: CurrentValueSubject<CurrencyType, Never> = {
		var value = self.currencyType
		return CurrentValueSubject<CurrencyType, Never>(value)
	}()
	
	var currencyType: CurrencyType {
		get {
			let key = Keys.currencyType.rawValue
			let saved: CurrencyType? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? CurrencyType.bitcoin
		}
		set {
			let key = Keys.currencyType.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
			currencyTypePublisher.send(newValue)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		var value = self.fiatCurrency
		return CurrentValueSubject<FiatCurrency, Never>(value)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			let key = Keys.fiatCurrency.rawValue
			var saved: FiatCurrency? = nil
			if let str = UserDefaults.standard.string(forKey: key) {
				saved = FiatCurrency.deserialize(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			let key = Keys.fiatCurrency.rawValue
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: key)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		var value = self.bitcoinUnit
		return CurrentValueSubject<BitcoinUnit, Never>(value)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			let key = Keys.bitcoinUnit.rawValue
			var saved: BitcoinUnit? = nil
			if let str = UserDefaults.standard.string(forKey: key) {
				saved = BitcoinUnit.deserialize(str)
			}
			return saved ?? BitcoinUnit.sat
		}
		set {
			let key = Keys.bitcoinUnit.rawValue
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: key)
			bitcoinUnitPublisher.send(newValue)
		}
	}
	
	lazy private(set) var themePublisher: CurrentValueSubject<Theme, Never> = {
		var value = self.theme
		return CurrentValueSubject<Theme, Never>(value)
	}()
	
	var theme: Theme {
		get {
			let key = Keys.theme.rawValue
			let saved: Theme? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? Theme.system
		}
		set {
			let key = Keys.theme.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
			themePublisher.send(newValue)
		}
	}

	lazy private(set) var isTorEnabledPublisher: CurrentValueSubject<Bool, Never> = {
		var value = self.isTorEnabled
		return CurrentValueSubject<Bool, Never>(value)
	}()

	var isTorEnabled : Bool {
		get {
			 UserDefaults.standard.bool(forKey: Keys.isTorEnabled.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.isTorEnabled.rawValue)
			isTorEnabledPublisher.send(newValue)
		}
	}
	
	var fcmTokenInfo: FcmTokenInfo? {
		get {
			let key = Keys.fcmTokenInfo.rawValue
			let result: FcmTokenInfo? = UserDefaults.standard.getCodable(forKey: key)
			return result
		}
		set {
			let key = Keys.fcmTokenInfo.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
		}
	}
	
	var pushPermissionQuery: PushPermissionQuery {
		get {
			let key = Keys.pushPermissionQuery.rawValue
			let saved: PushPermissionQuery? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? .neverAskedUser
		}
		set {
			let key = Keys.pushPermissionQuery.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
		}
	}
	
	var electrumConfig: ElectrumConfigPrefs? {
		get {
			let key = Keys.electrumConfig.rawValue
			let saved: ElectrumConfigPrefs? = UserDefaults.standard.getCodable(forKey: key)
			return saved
		}
		set {
			let key = Keys.electrumConfig.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
		}
	}
}

// MARK:-
/**
 * We prefer to store Codable types in the UserDefaults system.
 * The Codable system gives us Swift native tools for serialization & deserialization.
 *
 * But the Kotlin bridge is Objective-C. So we're choosing to provide custom
 * serialization & deserialization routines for these.
 */

extension FiatCurrency {
	
	func serialize() -> String {
		return self.name
	}
	
	static func deserialize(_ str: String) -> FiatCurrency? {
		for value in FiatCurrency.default().values {
			if str == value.serialize() {
				return value
			}
		}
		return nil
	}
	
	static func localeDefault() -> FiatCurrency? {
		
		guard let currencyCode = NSLocale.current.currencyCode else {
			return nil
		}
		// currencyCode examples:
		// - "USD"
		// - "JPY"
		
		for fiat in FiatCurrency.default().values {
			
			let fiatCode = fiat.name // e.g. "AUD", "BRL"
			
			if currencyCode.caseInsensitiveCompare(fiatCode) == .orderedSame {
				return fiat
			}
		}
		
		return nil
	}
}

extension BitcoinUnit {
	
	func serialize() -> String {
		return self.name
	}
	
	static func deserialize(_ str: String) -> BitcoinUnit? {
		for value in BitcoinUnit.default().values {
			if str == value.serialize() {
				return value
			}
		}
		return nil
	}
}

// MARK:-

enum CurrencyType: String, CaseIterable, Codable {
	case fiat
	case bitcoin
}

enum Theme: String, CaseIterable, Codable {
	case light
	case dark
	case system
	
	func localized() -> String {
		switch self {
		case .light  : return NSLocalizedString("Light", comment: "App theme option")
		case .dark   : return NSLocalizedString("Dark", comment: "App theme option")
		case .system : return NSLocalizedString("System", comment: "App theme option")
		}
	}
	
	func toInterfaceStyle() -> UIUserInterfaceStyle {
		switch self {
		case .light  : return .light
		case .dark   : return .dark
		case .system : return .unspecified
		}
	}
	
	func toColorScheme() -> ColorScheme? {
		switch self {
		case .light  : return ColorScheme.light
		case .dark   : return ColorScheme.dark
		case .system : return nil
		}
	}
}

enum PushPermissionQuery: String, Codable {
	case neverAskedUser
	case userDeclined
	case userAccepted
}

struct FcmTokenInfo: Equatable, Codable {
	let nodeID: String
	let fcmToken: String
}

struct ElectrumConfigPrefs: Codable {
	let host: String
	let port: UInt16
	
	private let version: Int // for potential future upgrades
	
	init(host: String, port: UInt16) {
		self.host = host
		self.port = port
		self.version = 1
	}
	
	var serverAddress: Lightning_kmpServerAddress {
		return Lightning_kmpServerAddress(host: host, port: Int32(port), tls: Lightning_kmpTcpSocketTLS.safe)
	}
}
