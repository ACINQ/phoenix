import SwiftUI
import PhoenixShared
import Combine

enum CurrencyType: String, CaseIterable {
	case fiat
	case bitcoin
	
	func serialize() -> String {
		return self.rawValue
	}
	
	static func deserialize(_ str: String) -> CurrencyType? {
		for value in CurrencyType.allCases {
			if str == value.serialize() {
				return value
			}
		}
		return nil
	}
}

extension FiatCurrency {
	
	func serialize() -> String {
		return self.shortLabel
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
			
			let fiatCode = fiat.shortLabel // e.g. "AUD", "BRL"
			
			if currencyCode.caseInsensitiveCompare(fiatCode) == .orderedSame {
				return fiat
			}
		}
		
		return nil
	}
}

extension BitcoinUnit {
	
	func serialize() -> String {
		return self.abbrev
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

enum Theme: String, CaseIterable {
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
	
	func serialize() -> String {
		return self.rawValue
	}
	
	static func deserialize(_ str: String) -> Theme? {
		for value in Theme.allCases {
			if str == value.serialize() {
				return value
			}
		}
		return nil
	}
}

class Prefs {
	
	public static let shared = Prefs()
	
	private init() {/* must use shared instance */}
	
	private enum UserDefaultsKey: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
		case theme
	}
	
	lazy private(set) var currencyTypePublisher: CurrentValueSubject<CurrencyType, Never> = {
		var value = self.currencyType
		return CurrentValueSubject<CurrencyType, Never>(value)
	}()
	
	var currencyType: CurrencyType {
		get {
			var saved: CurrencyType? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.currencyType.rawValue) {
				saved = CurrencyType.deserialize(str)
			}
			return saved ?? CurrencyType.bitcoin
		}
		set {
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: UserDefaultsKey.currencyType.rawValue)
			currencyTypePublisher.send(newValue)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		var value = self.fiatCurrency
		return CurrentValueSubject<FiatCurrency, Never>(value)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			var saved: FiatCurrency? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.fiatCurrency.rawValue) {
				saved = FiatCurrency.deserialize(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: UserDefaultsKey.fiatCurrency.rawValue)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		var value = self.bitcoinUnit
		return CurrentValueSubject<BitcoinUnit, Never>(value)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			var saved: BitcoinUnit? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.bitcoinUnit.rawValue) {
				saved = BitcoinUnit.deserialize(str)
			}
			return saved ?? BitcoinUnit.satoshi
		}
		set {
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: UserDefaultsKey.bitcoinUnit.rawValue)
			bitcoinUnitPublisher.send(newValue)
		}
	}
	
	lazy private(set) var themePublisher: CurrentValueSubject<Theme, Never> = {
		var value = self.theme
		return CurrentValueSubject<Theme, Never>(value)
	}()
	
	var theme: Theme {
		get {
			var saved: Theme? = nil
			if let str = UserDefaults.standard.string(forKey: UserDefaultsKey.theme.rawValue) {
				saved = Theme.deserialize(str)
			}
			return saved ?? Theme.system
		}
		set {
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: UserDefaultsKey.theme.rawValue)
			themePublisher.send(newValue)
		}
	}
}

class CurrencyPrefs: ObservableObject {
	
	@Published var currencyType: CurrencyType
	@Published var fiatCurrency: FiatCurrency
	@Published var bitcoinUnit: BitcoinUnit
	
	@Published var fiatExchangeRates: [BitcoinPriceRate] = []
	
	private var cancellables = Set<AnyCancellable>()
	private var unsubscribe: (() -> Void)? = nil

	init() {
		currencyType = Prefs.shared.currencyType
		fiatCurrency = Prefs.shared.fiatCurrency
		bitcoinUnit = Prefs.shared.bitcoinUnit
		
		Prefs.shared.currencyTypePublisher.sink {[weak self](newValue: CurrencyType) in
			self?.currencyType = newValue
		}.store(in: &cancellables)
		
		Prefs.shared.fiatCurrencyPublisher.sink {[weak self](newValue: FiatCurrency) in
			self?.fiatCurrency = newValue
		}.store(in: &cancellables)
		
		Prefs.shared.bitcoinUnitPublisher.sink {[weak self](newValue: BitcoinUnit) in
			self?.bitcoinUnit = newValue
		}.store(in: &cancellables)
		
		let business = PhoenixApplicationDelegate.get().business
		fiatExchangeRates = business.currencyManager.getBitcoinRates()
		
		unsubscribe = business.currencyManager.events.subscribe {[weak self] event in

			if let event = event as? FiatExchangeRatesUpdated {
				self?.fiatExchangeRates = event.rates
			}
		}
	}
	
	private init(
		currencyType: CurrencyType,
		fiatCurrency: FiatCurrency,
		bitcoinUnit: BitcoinUnit,
		exchangeRate: Double
	) {
		self.currencyType = currencyType
		self.fiatCurrency = fiatCurrency
		self.bitcoinUnit = bitcoinUnit
		
		let exchangeRate = BitcoinPriceRate(fiatCurrency: fiatCurrency, price: exchangeRate)
		fiatExchangeRates.append(exchangeRate)
	}
	
	deinit {
		unsubscribe?()
	}
	
	func toggleCurrencyType() -> Void {
		
		if currencyType == .fiat {
			currencyType = .bitcoin
		} else {
			currencyType = .fiat
		}
	}
	
	/// Returns the exchangeRate for the currently set fiatCurrency.
	///
	func fiatExchangeRate() -> BitcoinPriceRate? {
		
		return fiatExchangeRate(fiatCurrency: self.fiatCurrency)
	}
	
	/// Returns the exchangeRate for the given fiatCurrency.
	/// 
	func fiatExchangeRate(fiatCurrency: FiatCurrency) -> BitcoinPriceRate? {
		
		return self.fiatExchangeRates.first { rate -> Bool in
			return (rate.fiatCurrency == fiatCurrency)
		}
	}
	
	static func mockUSD() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .usd, bitcoinUnit: .satoshi, exchangeRate: 20_000.00)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .eur, bitcoinUnit: .satoshi, exchangeRate: 17_000.00)
	}
}
