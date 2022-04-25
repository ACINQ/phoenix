import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TextFieldCurrencyStyler"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum TextFieldCurrencyStylerError: Error {
	case emptyInput
	case invalidInput
}

/// This tool provides a binding for TextField's,
/// allowing the value to be formatted according to the user's locale.
///
/// For example, in the `en_US` locale:
/// - user types  : "1234"
/// - UI displays : "1,234"
///
/// To use:
/// - create an instance of TextFieldCurrencyStyler within your ViewBuilder code
/// - create TextField using the styler's `amountProxy`
///
/// ```
/// var currency: Currency
/// @State var amount: String
/// @State var parsedAmount: Result<Double, TextFieldCurrencyStylerError>
///
/// var body: some View {
///   let styler = TextFieldCurrencyStyler(currency: currency, amount: $amount, parsedAmount: $parsedAmount)
///   TextField("placeholder", text: styler.amountProxy)
///       .onChange(of: amount) { _ in
///           // new values for amount & parsedAmount available now
///       }
/// }
/// ```
///
struct TextFieldCurrencyStyler {
	
	private let currency: Currency
	@Binding private var amount: String
	@Binding private var parsedAmount: Result<Double, TextFieldCurrencyStylerError>
	
	private let userDidEdit: (() -> Void)?
	
	let hideMsats: Bool
	
	init(
		currency: Currency,
		amount: Binding<String>,
		parsedAmount: Binding<Result<Double, TextFieldCurrencyStylerError>>,
		hideMsats: Bool = true,
		userDidEdit: (() -> Void)? = nil
	) {
		self.currency = currency
		self._amount = amount
		self._parsedAmount = parsedAmount
		
		self.hideMsats = hideMsats
		self.userDidEdit = userDidEdit
	}

	var amountProxy: Binding<String> {
		
		Binding<String>(
			get: {
				log.debug("<- get: \"\(self.amount)\"")
				return self.amount
			},
			set: { (input: String) in
				log.debug("-> set")
				let oldAmount = amount
				let (newAmount, result) = TextFieldCurrencyStyler.format(
					input: input,
					currency: currency,
					hideMsats: hideMsats
				)
				self.parsedAmount = result
				self.amount = newAmount // contract: always change parsedAmount before amount
				
				if let userDidEdit = userDidEdit, oldAmount != newAmount {
					userDidEdit()
				}
			}
		)
	}
	
	/**
	 * Attempts to format the given input, and returns a tuple containing the results.
	 * On success, the tuple is:
	 *   (formattedString, Result.success(parsedNumber))
	 * On failure, the tuple is:
	 *   (originalInput, Result.failure(reason))
	 *
	 * Note:
	 * - The `Utils` class can be used to convert from number to formattedString.
	 * - This class can be used to convert from string to (formattedString, parsedNumber)
	 */
	static func format(
		input: String,
		currency: Currency,
		hideMsats: Bool = true
	) -> (String, Result<Double, TextFieldCurrencyStylerError>)
	{
		let Fail = {(_ error: TextFieldCurrencyStylerError) ->
			(String, Result<Double, TextFieldCurrencyStylerError>) in
			
			log.debug("-> Fail")
			return (input, Result.failure(error))
		}
		
		let Succeed = {(_ amount: String, parsedAmount: Double) ->
			(String, Result<Double, TextFieldCurrencyStylerError>) in
			
			log.debug("-> Succeed")
			return (amount, Result.success(parsedAmount))
		}
		
		// Remove whitespace
		
		let RemoveWhitespace = {(_ str: String) -> String in
			
			return str.components(separatedBy: CharacterSet.whitespacesAndNewlines).joined()
		}
		
		var rawInput = RemoveWhitespace(input)
		
		if rawInput.count == 0 {
			log.debug("empty input")
			return Fail(.emptyInput)
		}
		
		// Get appropriate formatter for the currency
		
		let isFiatCurrency: Bool
		let formatter: NumberFormatter
		switch currency {
		case .bitcoin(let bitcoinUnit):
			isFiatCurrency = false
			formatter = Utils.bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
			
		case .fiat(let fiatCurrency):
			isFiatCurrency = true
			formatter = Utils.fiatFormatter(fiatCurrency: fiatCurrency)
		}
		
		// The formatter may be configured with minimumFractionDigits > 0.
		// For example, with Fiat(USD), the minimumFractionDigits is 2.
		//
		// This means that "42" gets formatted to "42.00".
		// But it also means that "42" cannot be parsed,
		// because the formatter requires the 2 fraction digits.
		//
		// So we want to change this setting, such that:
		// - input: "1234" -> output: "1,234" (for `en_US` locale)
		//
		formatter.minimumFractionDigits = 0
		
		// Remove formatting characters from the input
		
		let RemoveGroupingCharacters = {(_ str: String) -> String in
			
			if formatter.usesGroupingSeparator {
				let groupingSeparator = isFiatCurrency
					? formatter.currencyGroupingSeparator : formatter.groupingSeparator
				
				if let groupingSeparator = groupingSeparator {
					return str.components(separatedBy: groupingSeparator).joined()
				}
			}
			
			return str
		}
		
		rawInput = RemoveGroupingCharacters(rawInput)
		log.debug("rawInput: \"\(rawInput)\"")
		
		// Attempt to parse the rawInput using the locale-aware formatter.
		// This should work if the input is a valid number.
		
		guard let number = formatter.number(from: rawInput) else {
			log.debug("cannot convert rawInput to number")
			return Fail(.invalidInput)
		}
		
		// Now that we have the raw number, we can apply the proper formatting to it.
		
		guard let formattedInput = formatter.string(from: number) else {
			log.debug("cannot convert number to formatted string")
			return Fail(.invalidInput)
		}
		
		log.debug("formattedInput: \"\(formattedInput)\"")
		
		// We can only use the formattedInput if it properly matches the rawInput.
		//
		// For example:
		// - input = 5.0
		// - formattedInput = 5
		//
		// In this case, the user may be trying to type "5.05".
		// So we have to ignore the formattedInput at this point,
		// and allow the user to continue typing without interruption.
		
		let rawFormattedInput = RemoveGroupingCharacters(RemoveWhitespace(formattedInput))
		log.debug("rawFormattedInput: \"\(rawFormattedInput)\"")
		
		if rawInput != rawFormattedInput {
			log.debug("foiled !")
			
			// At this point we know that the rawInput is a parsable number.
			// But it doesn't quite match up with `formattedInput`.
			//
			// Sometimes this occurs because the user is hitting backspace.
			// For example, the text was "0.123 4", and then the user hit backspace.
			//
			// - input          = "0.123 " <- trailing space
			// - formattedInput = "0.123"  <- no trailing space
			//
			// If this is the case, then we can assist the user by
			// automatically removinv the trailing fractionGroupingSeparator.
			
			let decimalSeparator: String
			if isFiatCurrency {
				decimalSeparator = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
			} else {
				decimalSeparator = formatter.decimalSeparator ?? "."
			}
			
			if input.contains(decimalSeparator) {
					
				let separator = FormattedAmount.fractionGroupingSeparator
				if input.hasSuffix(separator) {
					
					let endIdx = input.index(input.endIndex, offsetBy: -separator.count)
					let substr = input[input.startIndex ..< endIdx]
					let output = String(substr)
					
					log.debug("fixup: type(1)")
					return Succeed(output, number.doubleValue)
				}
			}
			
			// Other times this occurs because of trailing zeros in the fractional component:
			// - input = "0.010"
			// - formattedInput = "0.01"
			//
			// If this is the case, we can assist the user by
			// automatically formatting the fractionDigits for them.
			
			do {
				
				let countFractionDigits = {(str: String) -> Int in
					
					let digits = "0123456789"
					var foundDecimalSeparator = false
					
					return str.reduce(into: 0) { partialResult, c in
						if foundDecimalSeparator {
							if digits.contains(c) {
								partialResult += 1
							}
						} else if "\(c)" == decimalSeparator {
							foundDecimalSeparator = true
						}
					}
				}
					
				let inputFractionDigitsCount = countFractionDigits(rawInput)
				let formattedFractionDigitsCount = countFractionDigits(rawFormattedInput)
				
				if inputFractionDigitsCount > formattedFractionDigitsCount {
					
					let missingZeros = inputFractionDigitsCount - formattedFractionDigitsCount
					
					var temp = FormattedAmount(
						amount: number.doubleValue,
						currency: currency,
						digits: formattedInput,
						decimalSeparator: formatter.decimalSeparator
					)
					
					var newFractionDigits = RemoveWhitespace(temp.fractionDigits)
					for _ in 0 ..< missingZeros {
						newFractionDigits.append("0")
					}
					
					let newDigits = temp.integerDigits + temp.decimalSeparator + newFractionDigits
					temp = FormattedAmount(
						amount: number.doubleValue,
						currency: currency,
						digits: newDigits,
						decimalSeparator: formatter.decimalSeparator
					)
					
					let betterFormattedInput = temp.withFormattedFractionDigits().digits
					
					log.debug("fixup: type(2)")
					return Succeed(betterFormattedInput, number.doubleValue)
				}
			}
			
			// There's some other unkown issue, so we fallback to the user's input.
			
			log.debug("fallback")
			return Succeed(input, number.doubleValue)
		}
		
		log.debug("sweet !")
		
		let formattedAmount = FormattedAmount(
			amount: number.doubleValue,
			currency: currency,
			digits: formattedInput,
			decimalSeparator: formatter.decimalSeparator
		)
		let betterFormattedInput = formattedAmount.withFormattedFractionDigits().digits
			
		return Succeed(betterFormattedInput, number.doubleValue)
	}
}
