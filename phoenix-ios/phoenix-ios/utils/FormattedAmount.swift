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
	
	/// Only the digits. E.g. "12,345.6789"
	///
	/// The value will be formatted for the current locale. E.g.:
	/// - "12,845.123456"
	/// - "12 845.123456"
	/// - "12.845,123456"
	///
	let digits: String
	
	/// The currency type. E.g. "USD" or "btc"
	///
	let type: String
	
	/// The locale-specific separator between the integerDigits & fractionDigits.
	/// If you're doing custom formatting between the two,
	/// be sure that you use this value. Don't assume it's a dot !
	///
	let decimalSeparator: String
	
	/// The standard string value as typically displayed in the app. E.g.: "42,526 sat"
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

extension FormattedAmount {
	
	/// The grouping separator used for the fractional component of the number.
	///
	/// Currently, this is set to use the unicode "narrow no-break space",
	/// which is the same character used for the groupingSeparator in several locale's.
	///
	static let fractionGroupingSeparator = "\u{202f}"
	
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
	/// Just by glancing at the above, can you tell that's a satoshi ?
	/// Or am I tricking you, and it's actually 10 satoshis ?
	///
	/// You know a satoshi should have 7 zeros in front of it.
	/// But it's pretty hard to count those zeros isn't it ?
	///
	/// The whole purpose for formatting is to separate
	/// large chunks of numbers into readable groups.
	/// If a similar number of zeros occurred in the integer
	/// portion of the number, it would be formatted nicely:
	/// "10,000,000"
	///
	/// So what this function does is apply a subtle format to the
	/// fractional component of the number:
	///
	/// "0.000 000 01" - but NOT using spaces!
	///
	/// Spaces are WAY too BIG.
	/// We're going to use unicode "narrow no-break space",
	/// which is a very small subtle space.
	///
	func withFormattedFractionDigits() -> FormattedAmount {
		
		let fractionDigits = self.fractionDigits
		if fractionDigits.count == 0 { // no fractional component
			return self
		}
		
		let digits = "0123456789"
		var digitsCount = 0
		
		let betterFractionDigits = fractionDigits.map { (c: Character) -> String in
			if digits.contains(c) {
				digitsCount += 1
			}
			if digitsCount == 4 { // after every 3 digits (before 4th)
				digitsCount = 1
				return "\(FormattedAmount.fractionGroupingSeparator)\(c)" // narrow no-break space
			} else {
				return "\(c)"
			}
		}.joined()
		
		let betterDigits =
			self.integerDigits + self.decimalSeparator + betterFractionDigits
		
		return FormattedAmount(
			digits: betterDigits,
			type: self.type,
			decimalSeparator: self.decimalSeparator
		)
	}
}
