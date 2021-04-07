import Foundation
import PhoenixShared

/// Represents a displayable currency unit,
/// which can be either a BitcoinUnit or a FiatCurrency.
///
struct CurrencyUnit: Hashable {
	
	let bitcoinUnit: BitcoinUnit?
	let fiatCurrency: FiatCurrency?
	
	init(bitcoinUnit: BitcoinUnit) {
		self.bitcoinUnit = bitcoinUnit
		self.fiatCurrency = nil
	}
	init(fiatCurrency: FiatCurrency) {
		self.bitcoinUnit = nil
		self.fiatCurrency = fiatCurrency
	}
	
	var abbrev: String {
		if let bitcoinUnit = bitcoinUnit {
			return bitcoinUnit.shortName
		} else {
			return fiatCurrency!.shortName
		}
	}
}

extension CurrencyUnit {
	
	/// A list of all BitcoinUnit's and the currently selected FiatCurrency (IFF we know the exchangeRate).
	///
	static func displayable(currencyPrefs: CurrencyPrefs) -> [CurrencyUnit] {
		
		var all = [CurrencyUnit]()
		
		for bitcoinUnit in BitcoinUnit.default().values {
			all.append(CurrencyUnit(bitcoinUnit: bitcoinUnit))
		}
		
		if let _ = currencyPrefs.fiatExchangeRate() {
			all.append(CurrencyUnit(fiatCurrency: currencyPrefs.fiatCurrency))
		} else {
			// We don't have the exchange rate for the user's selected fiat currency.
			// So we won't be able to perform conversion to millisatoshi.
		}
		
		return all
	}
}
