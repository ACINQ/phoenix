import Foundation


extension Currency {
	
	/// Returns the list of preferred currencies. This includes:
	/// - the user's chosen fiat currency (via Phoenix Settings -> Display -> Fiat)
	/// - the user's chosen bitcoin unit (via Phoenix Settings -> Display -> Bitcoin)
	/// - the list of currencies being used in the currency converter
	///
	/// The order is maintained from the currency converter (which the user can sort manually).
	///
	/// Any fiat currencies for which we don't have the current exchange rate are filtered out.
	///
	/// - Parameters:
	///   - currencyPrefs: Pass the view's EvironmentObject instance
	///   - plus: Optionally add an additional Currency to the end of the list
	///
	static func displayable(currencyPrefs: CurrencyPrefs, plus: [Currency]? = nil) -> [Currency] {
		
		var all = [Currency](GroupPrefs.current.currencyConverterList)
		
		let preferredFiatCurrency = Currency.fiat(currencyPrefs.fiatCurrency)
		if !all.contains(preferredFiatCurrency) {
			all.insert(preferredFiatCurrency, at: 0)
		}
		
		let preferredBitcoinUnit = Currency.bitcoin(currencyPrefs.bitcoinUnit)
		if !all.contains(preferredBitcoinUnit) {
			all.insert(preferredBitcoinUnit, at: 0)
		}
		
		if let plus {
			for currency in plus {
				if !all.contains(currency) {
					all.append(currency)
				}
			}
		}
		
		return all.filter { currency in
			switch currency {
			case .bitcoin(_):
				return true
			case .fiat(let fiatCurrency):
				if let _ = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					return true
				} else {
					// We don't have the exchange rate for this fiat currency.
					// So we won't be able to perform conversion to millisatoshi.
					return false
				}
			}
		}
	}
}
