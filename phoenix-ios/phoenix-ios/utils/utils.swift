import Foundation
import PhoenixShared

struct FormattedAmount {
	
	/// The currency amount, formatted for the current locale. E.g.:
	/// - "12,845.123456"
	/// - "12 845.123456"
	/// - "12.845,123456"
	///
	let digits: String
	
	/// The currency type. E.g.:
	/// - "USD"
	/// - "btc"
	///
	let type: String
	
	/// The locale-specific separator between the integerDigits & fractionDigits.
	/// If you're doing custom formatting between the two,
	/// be sure that you use this value. Don't assume it's a dot !
	///
	let decimalSeparator: String
	
	/// Returns the simple string value. E.g.:
	/// - "42,526 sat"
	///
	var string: String {
		return "\(digits) \(type)"
	}
	
	/// Returns only the integer portion of the digits. E.g.:
	/// - digits="12,845.123456" => "12,845"
	/// - digits="12 845.123456" => "12 845"
	/// - digits="12.845,123456" => "12.845"
	///
	var integerDigits: String {
	
		guard let sRange = digits.range(of: decimalSeparator) else {
			return digits
		}
		let range = digits.startIndex ..< sRange.lowerBound
		return String(digits[range])
	}
	
	/// Returns only the fraction portion of the digits. E.g.:
	/// - digits="12,845.123456" => "123456"
	/// - digits="12 845.123456" => "123456"
	/// - digits="12.845,123456" => "123456"
	///
	var fractionDigits: String {
		
		guard let sRange = digits.range(of: decimalSeparator) else {
			return ""
		}
		let range = sRange.upperBound ..< digits.endIndex
		return String(digits[range])
	}
}

class Utils {
	
	private static var Millisatoshis_Per_Satoshi      =           1_000.0
	private static var Millisatoshis_Per_Bit          =         100_000.0
	private static var Millisatoshis_Per_Millibitcoin =     100_000_000.0
	private static var Millisatoshis_Per_Bitcoin      = 100_000_000_000.0
	
	static func format(_ currencyPrefs: CurrencyPrefs, sat: Int64) -> FormattedAmount {
		return format(currencyPrefs, msat: (sat * 1_000))
	}
	
	static func format(_ currencyPrefs: CurrencyPrefs, msat: Int64) -> FormattedAmount {
		
		if currencyPrefs.currencyType == .bitcoin {
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		} else {
			let selectedFiat = currencyPrefs.fiatCurrency
			let exchangeRate = currencyPrefs.fiatExchangeRates.first { rate -> Bool in
				return (rate.fiatCurrency == selectedFiat)
			}
			
			if let exchangeRate = exchangeRate {
				return formatFiat(msat: msat, exchangeRate: exchangeRate)
			} else {
				return FormattedAmount(
					digits: "?.??",
					type: selectedFiat.shortLabel,
					decimalSeparator: NumberFormatter().currencyDecimalSeparator
				)
			}
		}
	}
	
	static func formatBitcoin(msat: Int64, bitcoinUnit: BitcoinUnit) -> FormattedAmount {
		
		let targetAmount: Double
		switch bitcoinUnit {
			case .satoshi      : targetAmount = Double(msat) / Millisatoshis_Per_Satoshi
			case .bits         : targetAmount = Double(msat) / Millisatoshis_Per_Bit
			case .millibitcoin : targetAmount = Double(msat) / Millisatoshis_Per_Millibitcoin
			default/*.bitcoin*/: targetAmount = Double(msat) / Millisatoshis_Per_Bitcoin
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true // thousands separator (US="10,000", FR="10 000")
		
		switch bitcoinUnit {
			case .satoshi      : formatter.maximumFractionDigits = 0
			case .bits         : formatter.maximumFractionDigits = 2
			case .millibitcoin : formatter.maximumFractionDigits = 5
			default/*.bitcoin*/: formatter.maximumFractionDigits = 8
		}
		
		formatter.roundingMode = .floor
		
		let digits = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		
		return FormattedAmount(
			digits: digits,
			type: bitcoinUnit.abbrev,
			decimalSeparator: formatter.decimalSeparator
		)
	}
	
	static func formatFiat(msat: Int64, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		// exchangeRate.fiatCurrency: FiatCurrency { get }
		// exchangeRate.price: Double { get }
		//
		// exchangeRate.price => value of 1.0 BTC in fiat
		
		let btc = Double(msat) / Millisatoshis_Per_Bitcoin
		let fiat = btc * exchangeRate.price
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .currency
		
		// Fiat amount should be rounded up.
		// Otherwise 1 sat (or 1msat...) = $0.00 which is not really correct.
		// It's better to display $0.01 instead.
		formatter.roundingMode = .ceiling
		
		var digits = formatter.string(from: NSNumber(value: fiat)) ?? fiat.description
		
		// digits has the currencySymbol embedded in it:
		// - "$1,234.57"
		// - "1 234,57 €"
		// - "￥1,234.57"
		//
		// So we need to trim that, and any leftover whitespace
		
		if let range = digits.range(of: formatter.currencySymbol) {
			digits.removeSubrange(range)
		}
		digits = digits.trimmingCharacters(in: .whitespaces) // removes from only the ends
		
		return FormattedAmount(
			digits: digits,
			type: exchangeRate.fiatCurrency.shortLabel,
			decimalSeparator: formatter.currencyDecimalSeparator
		)
	}
}
