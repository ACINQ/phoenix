import Foundation
import PhoenixShared

/// Represents a displayable currency,
/// which can be either a BitcoinUnit or a FiatCurrency.
///
enum Currency: Hashable, Identifiable, CustomStringConvertible {
	
	case bitcoin(BitcoinUnit)
	case fiat(FiatCurrency)
	
	var type: CurrencyType {
		switch self {
		case .bitcoin(_):
			return .bitcoin
		case .fiat(_):
			return .fiat
		}
	}
	
	var shortName: String {
		switch self {
		case .bitcoin(let unit):
			return unit.shortName
		case .fiat(let currency):
			return currency.shortName
		}
	}
	
	var splitShortName: (String, String) {
		switch self {
		case .bitcoin(let unit):
			return (unit.shortName, "")
		case .fiat(let currency):
			return currency.splitShortName
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
}
