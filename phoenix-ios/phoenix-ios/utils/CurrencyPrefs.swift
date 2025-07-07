import Foundation
import PhoenixShared
import Combine

fileprivate let filename = "CurrencyPrefs"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// An ObservableObject that monitors the currently stored values in UserDefaults.
///
class CurrencyPrefs: ObservableObject {
	
	private static var last: CurrencyPrefs? = nil
	
	static var current: CurrencyPrefs {
		let id = Biz.walletId?.prefsKeyId ?? PREFS_DEFAULT_ID
		if let last, last.id == id {
			return last
		} else {
			let prefs = CurrencyPrefs(id)
			last = prefs
			return prefs
		}
	}
	
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
	
	private let id: String
	private var cancellables = Set<AnyCancellable>()
	private var delayedSave = DelayedSave()

	private init(_ id: String) {
		self.id = id
		
		let groupPrefs = GroupPrefs.wallet(id)
		let prefs = Prefs.wallet(id)
		
		currencyType = groupPrefs.currencyType
		fiatCurrency = groupPrefs.fiatCurrency
		bitcoinUnit = groupPrefs.bitcoinUnit
		hideAmounts = prefs.hideAmounts
		showOriginalFiatValue = prefs.showOriginalFiatAmount
		
		groupPrefs.fiatCurrencyPublisher.sink {[weak self](newValue: FiatCurrency) in
			self?.fiatCurrency = newValue
		}.store(in: &cancellables)
		
		groupPrefs.bitcoinUnitPublisher.sink {[weak self](newValue: BitcoinUnit) in
			self?.bitcoinUnit = newValue
		}.store(in: &cancellables)
		
		prefs.showOriginalFiatAmountPublisher.sink {[weak self](newValue: Bool) in
			self?.showOriginalFiatValue = newValue
		}.store(in: &cancellables)
		
		let business = Biz.business
		business.currencyManager.ratesPubliser().sink {[weak self](rates: [ExchangeRate]) in
			self?.fiatExchangeRates = rates
		}.store(in: &cancellables)
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
			GroupPrefs.wallet(self.id).currencyType = self.currencyType
			Prefs.wallet(self.id).hideAmounts = self.hideAmounts
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
}
