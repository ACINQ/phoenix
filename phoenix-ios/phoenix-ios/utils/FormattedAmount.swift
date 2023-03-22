import Foundation
import PhoenixShared

/// Represents a formatted amount of currency.
/// That is, a string, and it's various components.
///
/// The structure is designed to give the UI flexibility.
/// For example, if the UI wants to display the fractional
/// component of the number using a different font.
///
struct FormattedAmount {
	
	/// The raw amount as a Double
	///
	let amount: Double
	
	/// The currency type. E.g.:
	/// - .bitcoin(.sat)
	/// - .fiat(.usd)
	///
	let currency: Currency
	
	/// Only the digits. E.g. "12,345.6789"
	///
	/// The value will be formatted for the current locale. E.g.:
	/// - "12,845.123456"
	/// - "12 845.123456"
	/// - "12.845,123456"
	///
	let digits: String
	
	/// The locale-specific separator between the integerDigits & fractionDigits.
	/// If you're doing custom formatting between the two,
	/// be sure that you use this value. Don't assume it's a dot !
	///
	let decimalSeparator: String
	
	/// The currency type. E.g. "USD" or "btc"
	///
	var type: String {
		return currency.shortName
	}
	
	/// The standard string value as typically displayed in the app. E.g.: "42,526 sat"
	///
	var string: String {
		return "\(digits) \(type)"
	}
	
	/// Returns only the integer portion of the digits. E.g.:
	/// - digits="12,845.123456" => integerDigits="12,845"
	/// - digits="12 845.123456" => integerDigits="12 845"
	/// - digits="12.845,123456" => integerDigits="12.845"
	///
	var integerDigits: String {
	
		guard let sRange = digits.range(of: decimalSeparator) else {
			return digits
		}
		let range = digits.startIndex ..< sRange.lowerBound
		return String(digits[range])
	}
	
	/// Returns only the fraction portion of the digits. E.g.:
	/// - digits="12,845.123456" => fractionDigits="123456"
	/// - digits="12 845.123456" => fractionDigits="123456"
	/// - digits="12.845,123456" => fractionDigits="123456"
	///
	var fractionDigits: String {
		
		guard let sRange = digits.range(of: decimalSeparator) else {
			return ""
		}
		let range = sRange.upperBound ..< digits.endIndex
		return String(digits[range])
	}
	
	/// Returns whether or not the amount contains any fractional digits. E.g.:
	/// - digits="12,845"   => hasFractionDigits=false
	/// - digits="12 845.1" => hasFractionDigits=true
	///
	var hasFractionDigits: Bool {
		return !fractionDigits.isEmpty
	}
	
	/// Returns the "standard" fraction digits.
	///
	/// For example, in the US Dollar system, the first 2 fractional digits represent cents.
	/// And anything after that is considered sub-fractional, because it's less than 1 cent.
	///
	/// - if currency=USD && fractionDigits="50" => stdFractionDigits="50", subFractionDigits=""
	/// - if currency=USD && fractionDigits="5012" => stdFractionDigits="50", subFractionDigits="12"
	///
	/// A similar concept applies to bitcoin, where millisatoshis are considered sub-fractional:
	///
	/// - if currency=sat && fractionDigits="102" => stdFractionDigits="", subFractionDigits="102"
	/// - if currency=btc && fractionDigits="12345678901" => stdFractionDigits="12345678", subFractionDigits="901"
	///
	var stdFractionDigits: String {
		
		let stdFractionDigitsCount = self.stdFractionDigitsCount
		if stdFractionDigitsCount == 0 {
			return ""
		}
		
		let fractionDigits = self.fractionDigits
		
		var digitsCount = 0
		return fractionDigits.reduce(into: "") { partialResult, c in
			
			if digitsCount < stdFractionDigitsCount {
				partialResult.append(c)
			}
			if "\(c)" != FormattedAmount.fractionGroupingSeparator {
				digitsCount += 1
			}
		}
	}
	
	/// Returns whether or not the value contains standard-fractional amounts.
	///
	var hasStdFractionDigits: Bool {
		return !stdFractionDigits.isEmpty
	}
	
	/// Returns the standard number of fraction digits.
	/// Any digits past this number are considered sub-fractional digits.
	///
	/// For example, in the US Dollar system, 100 cents == 1 dollar.
	/// Thus, for Fiat(USD), the `stdFractionDigitsCount` would be 2,
	/// because "3.50" is a standard amount, but "3.502" contains a sub-fractional amount of 0.2 cents,
	/// representing a value that is too small to be represented in physical units of the currency.
	///
	/// Similarly, in bitcoin, millisatoshis represent a sub-fractional amount.
	/// Thus, for satoshis, the `stdFractionDigitsCount` would be 0,
	/// because any fractional component represents millisatoshis.
	///
	var stdFractionDigitsCount: Int {
		
		switch currency {
			case .fiat(_):
				return 2 // Room for improvement: https://stripe.com/docs/currencies#zero-decimal
			
			case .bitcoin(let bitcoinUnit):
				switch bitcoinUnit {
					case .sat  : return 0
					case .bit  : return 2
					case .mbtc : return 5
					default    : return 8
				}
		}
	}
	
	/// Returns the sub-fractional digits.
	///
	/// For example, in the US Dollar system, the first 2 fractional digits represent cents.
	/// And anything after that is considered sub-fractional, because it's less than 1 cent.
	///
	/// - if currency=USD && fractionDigits="50" => stdFractionDigits="50", subFractionDigits=""
	/// - if currency=USD && fractionDigits="5012" => stdFractionDigits="50", subFractionDigits="12"
	///
	/// A similar concept applies to bitcoin, where millisatoshis are considered sub-fractional:
	///
	/// - if currency=sat && fractionDigits="102" => stdFractionDigits="", subFractionDigits="102"
	///
	var subFractionDigits: String {
		
		let fractionDigits = self.fractionDigits
		let stdFractionDigitsCount = self.stdFractionDigitsCount
		
		if fractionDigits.count <= stdFractionDigitsCount {
			return ""
		}
		
		var digitsCount = 0
		return fractionDigits.reduce(into: "") { partialResult, c in
			
			if digitsCount >= stdFractionDigitsCount {
				partialResult.append(c)
			}
			if "\(c)" != FormattedAmount.fractionGroupingSeparator {
				digitsCount += 1
			}
		}
	}
	
	/// Returns whether or not the value contains sub-fractional amounts.
	///
	/// For example:
	/// - in the US Dollar, this means anything smaller than 1 cent. (i.e. more then 2 decimal places)
	/// - in bitcoin, this means millisatoshi's are present
	///
	var hasSubFractionDigits: Bool {
		return !subFractionDigits.isEmpty
	}
}

extension FormattedAmount {
	
	/// The grouping separator used for the fractional component of the number.
	///
	/// Currently, this is set to use the unicode "narrow no-break space",
	/// which is the same character used for the groupingSeparator in several locale's.
	///
	static let fractionGroupingSeparator = "\u{202f}"
	
	/// Returns a copy with standard truncation rules applied.
	///
	/// The truncation locations are currency specific.
	/// For example:
	///
	/// - If using Fiat(USD), and the value is "43.50",
	///   then the result is "43.50", and NOT "43.5".
	///   Because it's normal and natural to think: "100 cents = 1 dollar".
	///
	/// - If using Bitcoin(sat), and the value is "43.500",
	///   then the result is "43.500", and NOT "43.5"
	///   Because it's normal and natural to think: "1000 millisats = 1 sat".
	///
	func withTruncatedFractionDigits() -> FormattedAmount {
		
		var fractionDigits = self.fractionDigits
		if fractionDigits.isEmpty { // no fractional component
			return self
		}
		
		var allowedFractionDigitsLength: [Int] = []
		switch currency {
			case .fiat(_):
				allowedFractionDigitsLength = [2]
			case .bitcoin(let bitcoinUnit):
				switch bitcoinUnit {
					case .sat  : allowedFractionDigitsLength = [0, 3]
					case .bit  : allowedFractionDigitsLength = [2, 5] // always display 2 decimals, like fiat
					case .mbtc : allowedFractionDigitsLength = [0, 2, 5, 8]
					default    : allowedFractionDigitsLength = [0, 2, 5, 8, 11]
				}
		}
		
		let maxLength = allowedFractionDigitsLength.removeLast()
		if fractionDigits.count > maxLength {
			
			let endIndex = fractionDigits.index(fractionDigits.startIndex, offsetBy: maxLength)
			let range = fractionDigits.startIndex ..< endIndex
			fractionDigits = String(fractionDigits[range])
		}
		
		for allowedLength in allowedFractionDigitsLength.reversed() {
			
			if fractionDigits.count > allowedLength {
				
				let startIndex = fractionDigits.index(fractionDigits.startIndex, offsetBy: allowedLength)
				let substring = fractionDigits[startIndex ..< fractionDigits.endIndex]
				
				// Truncate substring if all digits are zero.
				
				if substring.contains(where: { $0 != "0" }) {
					break
					
				} else { // truncate
					
					let endIndex = fractionDigits.index(fractionDigits.startIndex, offsetBy: allowedLength)
					let range = fractionDigits.startIndex ..< endIndex
					fractionDigits = String(fractionDigits[range])
				}
			}
		}
		
		let truncatedDigits: String
		if fractionDigits.count > 0 {
			truncatedDigits = self.integerDigits + self.decimalSeparator + fractionDigits
		} else {
			truncatedDigits = self.integerDigits
		}
		
		return FormattedAmount(
			amount: self.amount,
			currency: self.currency,
			digits: truncatedDigits,
			decimalSeparator: self.decimalSeparator
		)
	}
	
	/// Returns a copy with subtle formatting applied to the fractionDigits component.
	///
	/// Motivation:
	///
	/// When formatting bitcoin or millibitcoin,
	/// the number may have a large fraction component.
	///
	/// For example, 1 satoshi formatted in bitcoin is:
	/// "0.00000001"
	///
	/// Just by glancing at the above, can you tell that's 1 satoshi ?
	/// Or am I tricking you, and it's actually 10 satoshis ?
	///
	/// It's pretty hard to count those zeros isn't it ?
	/// And that also assumes you know how many zeros there should be.
	///
	/// The whole purpose for formatting is to separate
	/// large chunks of numbers into readable groups.
	/// If a similar number of zeros occurred in the integer
	/// portion of the number, it would be formatted nicely:
	/// "10,000,000"
	///
	/// So what this function does is apply a subtle format to the
	/// fractional component of the number.
	///
	/// In doing so, we are adopting the "satcomma standard":
	/// - https://bitcoin.design/guide/designing-products/units-and-symbols/#satcomma
	/// - https://medium.com/coinmonks/the-satcomma-standard-89f1e7c2aed
	///
	/// Thus the output will be something like:
	///
	/// - "0.00 025 000 btc" - you can easily see this is "25,000 sat"
	/// - "0.00 000 001 btc" - yup, that's 1 sat
	/// - "0.01 234 567 btc" - that's over 1 million sats
	///
	/// Except that spaces are much too BIG.
	/// We're going to use unicode "narrow no-break space",
	/// which is a small subtle space.
	///
	func withFormattedFractionDigits() -> FormattedAmount {
		
		let fractionDigits = self.fractionDigits
		if fractionDigits.isEmpty { // no fractional component
			return self
		}
		
		let separatorLocations: SeparatorLocations
		switch currency {
			case .fiat(_):
				separatorLocations = SeparatorLocations(firstIndex: 2, subsequentCount: 3)
			case .bitcoin(let bitcoinUnit):
				switch bitcoinUnit {
					case .sat  : separatorLocations = SeparatorLocations(firstIndex: 3, subsequentCount: 3)
					case .bit  : separatorLocations = SeparatorLocations(firstIndex: 2, subsequentCount: 3)
					case .mbtc : separatorLocations = SeparatorLocations(firstIndex: 2, subsequentCount: 3)
					default    : separatorLocations = SeparatorLocations(firstIndex: 2, subsequentCount: 3)
				}
		}
		
		let digits = "0123456789"
		var digitsRemaining = separatorLocations.firstIndex
		
		let formattedFractionDigits = fractionDigits.map { (c: Character) -> String in
			if digits.contains(c) {
				digitsRemaining -= 1
			}
			if digitsRemaining < 0 {
				digitsRemaining = separatorLocations.subsequentCount - 1
				return "\(FormattedAmount.fractionGroupingSeparator)\(c)"
			} else {
				return "\(c)"
			}
		}.joined()
		
		let formattedDigits =
			self.integerDigits + self.decimalSeparator + formattedFractionDigits
		
		return FormattedAmount(
			amount: self.amount,
			currency: self.currency,
			digits: formattedDigits,
			decimalSeparator: self.decimalSeparator
		)
	}
}

fileprivate struct SeparatorLocations {
	let firstIndex: Int
	let subsequentCount: Int
}
