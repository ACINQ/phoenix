import Foundation
import PhoenixShared

/// Represents a displayable currency,
/// which can be either a BitcoinUnit or a FiatCurrency.
///
enum Currency: Hashable, Identifiable, CustomStringConvertible {
	
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
	
	var id: String {
		switch self {
		case .bitcoin(let unit):
			return unit.name.lowercased()
		case .fiat(let currency):
			return currency.name.uppercased()
		}
	}
	
	var description: String {
		switch self {
		case .bitcoin(let unit):
			return "bitcoin(\(unit.shortName))"
		case .fiat(let currency):
			return "fiat(\(currency.shortName))"
		}
	}
	
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
	
	static func displayable2(currencyPrefs: CurrencyPrefs, plus: Currency? = nil) -> [Currency] {
		
		var all = [Currency](Prefs.shared.currencyConverterList)
		
		let preferredFiatCurrency = Currency.fiat(currencyPrefs.fiatCurrency)
		if !all.contains(preferredFiatCurrency) {
			all.insert(preferredFiatCurrency, at: 0)
		}
		
		let preferredBitcoinUnit = Currency.bitcoin(currencyPrefs.bitcoinUnit)
		if !all.contains(preferredBitcoinUnit) {
			all.insert(preferredBitcoinUnit, at: 0)
		}
		
		if let plus = plus {
			if !all.contains(plus) {
				all.append(plus)
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
