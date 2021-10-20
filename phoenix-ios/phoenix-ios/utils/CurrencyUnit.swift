import Foundation
import PhoenixShared

/// Represents a displayable currency,
/// which can be either a BitcoinUnit or a FiatCurrency.
///
enum Currency: Hashable {
	case bitcoin(BitcoinUnit)
	case fiat(FiatCurrency)
	
	var abbrev: String {
		switch self {
		case .bitcoin(let unit):
			return unit.shortName
		case .fiat(let currency):
			return currency.shortName
		}
	}
}

extension Currency {
	
	/// A list of all BitcoinUnit's and the currently selected FiatCurrency (IFF we know the exchangeRate).
	///
	static func displayable(currencyPrefs: CurrencyPrefs) -> [Currency] {
		
		var all = [Currency]()
		
		for bitcoinUnit in BitcoinUnit.companion.values {
			all.append(Currency.bitcoin(bitcoinUnit))
		}
		
		let fiatCurrency = currencyPrefs.fiatCurrency
		if let _ = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
			all.append(Currency.fiat(fiatCurrency))
		} else {
			// We don't have the exchange rate for the user's selected fiat currency.
			// So we won't be able to perform conversion to millisatoshi.
		}
		
		return all
	}
}
