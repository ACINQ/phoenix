import Foundation
import PhoenixShared
import Combine
import UIKit

/// An ObservableObject that monitors the currently stored values in UserDefaults.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var currencyPrefs: CurrencyPrefs
///
class CurrencyPrefs: ObservableObject {
	
	@Published var currencyType: CurrencyType
	@Published var fiatCurrency: FiatCurrency
	@Published var bitcoinUnit: BitcoinUnit
	
	@Published var fiatExchangeRates: [ExchangeRate] = []
	private var fiatExchangeRatesWatcher: Ktor_ioCloseable? = nil
	
	var currency: Currency {
		switch currencyType {
		case .bitcoin:
			return Currency.bitcoin(bitcoinUnit)
		case .fiat:
			return Currency.fiat(fiatCurrency)
		}
	}
	
	private var cancellables = Set<AnyCancellable>()
	private var unsubscribe: (() -> Void)? = nil
	
	private var currencyTypeDelayedSave = DelayedSave()

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
		let ratesFlow = SwiftFlow<NSArray>(origin: business.currencyManager.ratesFlow)
		fiatExchangeRatesWatcher = ratesFlow.watch {[weak self](rates: NSArray?) in
			if let rates = rates as? Array<ExchangeRate> {
				self?.fiatExchangeRates = rates
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
		
		let exchangeRate = ExchangeRate.BitcoinPriceRate(
			fiatCurrency: fiatCurrency,
			price: exchangeRate,
			source: "",
			timestampMillis: 0
		)
		fiatExchangeRates.append(exchangeRate)
	}
	
	deinit {
		unsubscribe?()
		let _watcher = fiatExchangeRatesWatcher
		DispatchQueue.main.async {
			_watcher?.close()
		}
	}
	
	func toggleCurrencyType() -> Void {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		currencyType = (currencyType == .fiat) ? .bitcoin : .fiat
		
		// I don't really want to save the currencyType to disk everytime the user changes it.
		// Because users tend to toggle back and forth often.
		// So we're using a timer, plus a listener on applicationWillResignActive.
		//
		currencyTypeDelayedSave.save(withDelay: 10.0) {
			Prefs.shared.currencyType = self.currencyType
		}
	}
	
	/// Returns the exchangeRate for the currently set fiatCurrency.
	///
	func fiatExchangeRate() -> ExchangeRate.BitcoinPriceRate? {
		
		return fiatExchangeRate(fiatCurrency: self.fiatCurrency)
	}
	
	/// Returns the exchangeRate for the given fiatCurrency.
	///
	func fiatExchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		let btcExchangeRates: [ExchangeRate.BitcoinPriceRate] = fiatExchangeRates.compactMap { rate in
			return rate as? ExchangeRate.BitcoinPriceRate
		}
		
		if let paramToBtc = btcExchangeRates.first(where: { (rate: ExchangeRate.BitcoinPriceRate) in
			rate.fiatCurrency == fiatCurrency
		}) {
			return paramToBtc
		}
		
		let usdExchangeRates: [ExchangeRate.UsdPriceRate] = fiatExchangeRates.compactMap { rate in
			return rate as? ExchangeRate.UsdPriceRate
		}
		
		guard let paramToUsd = usdExchangeRates.first(where: { (rate: ExchangeRate.UsdPriceRate) in
			rate.fiatCurrency == fiatCurrency
		}) else {
			return nil
		}
		
		guard let usdToBtc = btcExchangeRates.first(where: { (rate: ExchangeRate.BitcoinPriceRate) in
			rate.fiatCurrency == FiatCurrency.usd
		}) else {
			return nil
		}
		
		return ExchangeRate.BitcoinPriceRate(
			fiatCurrency: fiatCurrency,
			price: usdToBtc.price * paramToUsd.price,
			source: "\(usdToBtc.source), \(paramToUsd.source)",
			timestampMillis: min(usdToBtc.timestampMillis, paramToUsd.timestampMillis)
		)
	}
	
	static func mockUSD() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .usd, bitcoinUnit: .sat, exchangeRate: 20_000.00)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(currencyType: .bitcoin, fiatCurrency: .eur, bitcoinUnit: .sat, exchangeRate: 17_000.00)
	}
}
