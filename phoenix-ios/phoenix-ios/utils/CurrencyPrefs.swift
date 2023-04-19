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
	@Published private(set) var hideAmounts: Bool
	@Published private(set) var showOriginalFiatValue: Bool
	
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
	private var delayedSave = DelayedSave()

	init() {
		currencyType = GroupPrefs.shared.currencyType
		fiatCurrency = GroupPrefs.shared.fiatCurrency
		bitcoinUnit = GroupPrefs.shared.bitcoinUnit
		hideAmounts = Prefs.shared.hideAmounts
		showOriginalFiatValue = Prefs.shared.showOriginalFiatAmount
		
		GroupPrefs.shared.fiatCurrencyPublisher.sink {[weak self](newValue: FiatCurrency) in
			self?.fiatCurrency = newValue
		}.store(in: &cancellables)
		
		GroupPrefs.shared.bitcoinUnitPublisher.sink {[weak self](newValue: BitcoinUnit) in
			self?.bitcoinUnit = newValue
		}.store(in: &cancellables)
		
		Prefs.shared.showOriginalFiatAmountPublisher.sink {[weak self](newValue: Bool) in
			self?.showOriginalFiatValue = newValue
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
		exchangeRate: Double
	) {
		self.currencyType = currencyType
		self.fiatCurrency = fiatCurrency
		self.bitcoinUnit = bitcoinUnit
		self.hideAmounts = false
		self.showOriginalFiatValue = false
		
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
		triggerDelayedSave()
	}
	
	func toggleHideAmounts() {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		hideAmounts.toggle()
		triggerDelayedSave()
	}
	
	private func triggerDelayedSave() {
		
		// We don't really want to save the settings to disk everytime the user changes it.
		// Because users tend to toggle back and forth often.
		// So we're using a timer to save the end result.
		//
		// Note that the DelaySave class also has a listener on applicationWillResignActive,
		// which automatically triggers a save too.
		
		delayedSave.save(withDelay: 10.0) {
			GroupPrefs.shared.currencyType = self.currencyType
			Prefs.shared.hideAmounts = self.hideAmounts
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
			exchangeRate: 20_000.00
		)
	}
	
	static func mockEUR() -> CurrencyPrefs {
		return CurrencyPrefs(
			currencyType: .bitcoin,
			fiatCurrency: .eur,
			bitcoinUnit: .sat,
			exchangeRate: 17_000.00
		)
	}
}
