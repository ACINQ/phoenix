import Foundation
import PhoenixShared

/**
 * We generally prefer to store Codable types in the UserDefaults system.
 * The Codable system gives us Swift native tools for serialization & deserialization.
 *
 * But the Kotlin bridge is Objective-C. So we're choosing to provide custom
 * serialization & deserialization routines for these.
 */

extension FiatCurrency {
	
	func serialize() -> String {
		return self.name
	}
	
	static func deserialize(_ str: String?) -> FiatCurrency? {
		if let str = str {
			for value in FiatCurrency.companion.values {
				if str == value.serialize() {
					return value
				}
			}
		}
		return nil
	}
	
	static func localeDefault() -> FiatCurrency? {
		
        guard let currencyCode = NSLocale.current.currency?.identifier else {
			return nil
		}
		// currencyCode examples:
		// - "USD"
		// - "JPY"
		
		for fiat in FiatCurrency.companion.values {
			
			let fiatCode = fiat.displayCode // e.g. "AUD", "BRL"
			
			if currencyCode.caseInsensitiveCompare(fiatCode) == .orderedSame {
				return fiat
			}
		}
		
		return nil
	}
	
	static func serializeList(_ list: [FiatCurrency]) -> String? {
		if list.isEmpty {
			return nil
		} else {
			return list.map { $0.serialize() }.joined(separator: ",")
		}
	}
	
	static func deserializeList(_ str: String?) -> [FiatCurrency] {
		if let str = str {
			return str.components(separatedBy: ",").compactMap { FiatCurrency.deserialize($0) }
		} else {
			return []
		}
	}
}

extension BitcoinUnit {
	
	func serialize() -> String {
		return self.name
	}
	
	static func deserialize(_ str: String?) -> BitcoinUnit? {
		if let str = str {
			for value in BitcoinUnit.companion.values {
				if str == value.serialize() {
					return value
				}
			}
		}
		return nil
	}
}

extension Currency {
	
	func serialize() -> String {
		switch self {
		case .bitcoin(let bitcoinUnit):
			return "bitcoin:\(bitcoinUnit.serialize())"
		case .fiat(let fiatCurrency):
			return "fiat:\(fiatCurrency.serialize())"
		}
	}
	
	static func deserialize(_ str: String?) -> Currency? {
		if let str = str {
			let components = str.split(separator: ":")
			if components.count >= 2 {
				switch components[0].lowercased() {
				case "bitcoin":
					if let bitcoinUnit = BitcoinUnit.deserialize(String(components[1])) {
						return Currency.bitcoin(bitcoinUnit)
					}
				case "fiat":
					if let fiatCurrency = FiatCurrency.deserialize(String(components[1])) {
						return Currency.fiat(fiatCurrency)
					}
				default: break
				}
			}
		}
		return nil
	}
	
	static func serializeList(_ list: [Currency]) -> String? {
		if list.isEmpty {
			return nil
		} else {
			return list.map { $0.serialize() }.joined(separator: ",")
		}
	}
	
	static func deserializeList(_ str: String?) -> [Currency] {
		if let str = str {
			return str.components(separatedBy: ",").compactMap { Currency.deserialize($0) }
		} else {
			return []
		}
	}
}
