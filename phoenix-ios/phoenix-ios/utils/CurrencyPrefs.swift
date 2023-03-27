import Foundation
import PhoenixShared
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CurrencyPrefs"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


/// An ObservableObject that monitors the currently stored values in UserDefaults.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var currencyPrefs: CurrencyPrefs
///
class CurrencyPrefs: ObservableObject {
	
	@Published private(set) var currencyType: CurrencyType
	@Published private(set) var fiatCurrency: FiatCurrency
	@Published private(set) var bitcoinUnit: BitcoinUnit
	@Published private(set) var hideAmountsOnHomeScreen: Bool
	
	@Published var fiatExchangeRates: [ExchangeRate] = []
	
	var currency: Currency {
		switch currencyType {
		case .bitcoin:
			return Currency.bitcoin(bitcoinUnit)
		case .fiat:
			return Currency.fiat(fiatCurrency)
		}
	}
	
	private var cancellables = Set<AnyCancellable>()
	private var currencyTypeDelayedSave = DelayedSave()

	init() {
		currencyType = GroupPrefs.shared.currencyType
		fiatCurrency = GroupPrefs.shared.fiatCurrency
		bitcoinUnit = GroupPrefs.shared.bitcoinUnit
		hideAmountsOnHomeScreen = Prefs.shared.hideAmountsOnHomeScreen
		
		GroupPrefs.shared.fiatCurrencyPublisher.sink {[weak self](newValue: FiatCurrency) in
			self?.fiatCurrency = newValue
		}.store(in: &cancellables)
		
		GroupPrefs.shared.bitcoinUnitPublisher.sink {[weak self](newValue: BitcoinUnit) in
			self?.bitcoinUnit = newValue
		}.store(in: &cancellables)
		
		let business = Biz.business
		business.currencyManager.ratesPubliser().sink {[weak self](rates: [ExchangeRate]) in
			self?.fiatExchangeRates = rates
		}.store(in: &cancellables)
	}
	
	private init(
		currencyType: CurrencyType,
		fiatCurrency: FiatCurrency,
		bitcoinUnit: BitcoinUnit,
		exchangeRate: Double,
		hideAmountsOnHomeScreen: Bool
	) {
		self.currencyType = currencyType
		self.fiatCurrency = fiatCurrency
		self.bitcoinUnit = bitcoinUnit
		self.hideAmountsOnHomeScreen = hideAmountsOnHomeScreen
		
		let exchangeRate = ExchangeRate.BitcoinPriceRate(
			fiatCurrency: fiatCurrency,
			price: exchangeRate,
			source: "",
			timestampMillis: 0
		)
		fiatExchangeRates.append(exchangeRate)
	}
	
	func toggleCurrencyType() {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		currencyType = (currencyType == .fiat) ? .bitcoin : .fiat
		
		// I don't really want to save the currencyType to disk everytime the user changes it.
		// Because users tend to toggle back and forth often.
		// So we're using a timer, plus a listener on applicationWillResignActive.
		//
		currencyTypeDelayedSave.save(withDelay: 10.0) {
			GroupPrefs.shared.currencyType = self.currencyType
		}
	}
	
	func toggleHideAmountsOnHomeScreen() {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		hideAmountsOnHomeScreen.toggle()
		Prefs.shared.hideAmountsOnHomeScreen = self.hideAmountsOnHomeScreen
	}
	
	/// Returns the exchangeRate for the currently set fiatCurrency.
	///
	func fiatExchangeRate() -> ExchangeRate.BitcoinPriceRate? {
		
		return fiatExchangeRate(fiatCurrency: self.fiatCurrency)
	}
	
	/// Returns the exchangeRate for the given fiatCurrency.
	///
	func fiatExchangeRate(fiatCurrency: FiatCurrency) -> ExchangeRate.BitcoinPriceRate? {
		
		return Utils.exchangeRate(for: fiatCurrency, fromRates: fiatExchangeRates)
	}
	
	func convert(srcAmount: Double, srcCurrency: Currency, dstCurrency: Currency) -> Double? {
	
		var msat: Int64 = 0
		
		switch srcCurrency {
		case .bitcoin(let bitcoinUnit):
			msat = Utils.toMsat(from: srcAmount, bitcoinUnit: bitcoinUnit)
			
		case .fiat(let fiatCurrency):
			if let exchangeRate = fiatExchangeRate(fiatCurrency: fiatCurrency) {
				msat = Utils.toMsat(fromFiat: srcAmount, exchangeRate: exchangeRate)
				
			} else {
				return nil
			}
		}
		
		switch dstCurrency {
		case .bitcoin(let bitcoinUnit):
			return Utils.convertBitcoin(msat: msat, to: bitcoinUnit)
			
		case .fiat(let fiatCurrency):
			if let exchangeRate = fiatExchangeRate(fiatCurrency: fiatCurrency) {
				return Utils.convertToFiat(msat: msat, exchangeRate: exchangeRate)
				
			} else {
				return nil
			}
		}
	}
	
	static func mockUSD() -> CurrencyPrefs {
		return CurrencyPrefs(
			currencyType: .bitcoin,
			fiatCurrency: .usd,
			bitcoinUnit: .sat,
			exchangeRate: 20_000.00,
			hideAmountsOnHomeScreen: false
		)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(
			currencyType: .bitcoin,
			fiatCurrency: .eur,
			bitcoinUnit: .sat,
			exchangeRate: 17_000.00,
			hideAmountsOnHomeScreen: false
		)
	}
}
