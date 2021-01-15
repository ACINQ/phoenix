import Foundation
import PhoenixShared
import Combine

/// An ObservableObject that monitors the currently stored values in UserDefaults.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var currencyPrefs: CurrencyPrefs
///
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
		
		let business = AppDelegate.get().business
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
