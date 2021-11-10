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
/// For example, in the en_US locale:
/// - user types  : "1234"
/// - UI displays : "1,234"
///
/// To use:
/// - create an instance of TextFieldCurrencyStyler within your ViewBuilder code
/// - create TextField using the styler's `amountProxy`
///
/// ```
/// @State var unit: Currency
/// @State var amount: String
/// @State var parsedAmount: Result<Double, TextFieldCurrencyStylerError>
///
/// var body: some View {
///   let styler = TextFieldCurrencyStyler(unit: $unit, amount: $amount, parsedAmount: $parsedAmount)
///   TextField(verbatim: "123", text: styler.amountProxy)
///       .onChange(of: amount) { _ in
///           // new values for amount & parsedAmount available now
///       }
/// }
/// ```
///
/// For changed, you can list
///
struct TextFieldCurrencyStyler {
	
	@Binding private var unit: Currency
	@Binding private var amount: String
	@Binding private var parsedAmount: Result<Double, TextFieldCurrencyStylerError>
	
	let hideMsats: Bool
	
	init(
		unit: Binding<Currency>,
		amount: Binding<String>,
		parsedAmount: Binding<Result<Double, TextFieldCurrencyStylerError>>,
		hideMsats: Bool = true
	) {
		_unit = unit
		_amount = amount
		_parsedAmount = parsedAmount
		
		self.hideMsats = hideMsats
	}

	var amountProxy: Binding<String> {
		
		Binding<String>(
			get: {
				log.debug("<- get: \"\(self.amount)\"")
				return self.amount
			},
			set: { (input: String) in
				log.debug("-> set")
				let (newAmount, result) = TextFieldCurrencyStyler.format(
					input: input,
					unit: unit,
					hideMsats: hideMsats
				)
				self.parsedAmount = result
				self.amount = newAmount // contract: always change parsedAmount before amount
			}
		)
	}
	
	static func format(
		input: String,
		unit: Currency,
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
		
		// Get appropriate formatter for the current state
		
		let isFiatCurrency: Bool
		let formatter: NumberFormatter
		switch unit {
		case .bitcoin(let bitcoinUnit):
			isFiatCurrency = false
			formatter = Utils.bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
			
		case .fiat(let fiatCurrency):
			isFiatCurrency = true
			formatter = Utils.fiatFormatter(fiatCurrency: fiatCurrency)
		}
		
		if isFiatCurrency {
			// The fiatFormatter is configured with minimumFractionDigits set to 2.
			// This means that "42" gets formatted to "42.00".
			// It also means that "42" cannot be parsed,
			// because the formatter requires the 2 fraction digits.
			//
			// So we want to change this setting, such that:
			// - input: "1234" -> output: "1,234" (for en_US locale)
			//
			formatter.minimumFractionDigits = 0
		}
		
		// Setup helper tools
		
		let RemoveWhitespace = {(_ str: String) -> String in
			
			return str.components(separatedBy: CharacterSet.whitespacesAndNewlines).joined()
		}
		
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
		
		// Remove formatting characters from the input
		
		var rawInput = RemoveWhitespace(input)
		
		if rawInput.count == 0 {
			log.debug("empty input")
			return Fail(.emptyInput)
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
			// Sometimes this occurs because of trailing zeros in the fractional component:
			// - input = "0.010"
			// - formattedInput = "0.01"
			//
			// Other times it happens when the user is hitting backspace.
			// For example, the text was "0.123 4", and then the user hit backspace.
			//
			// - input          = "0.123 " <- trailing space
			// - formattedInput = "0.123"  <- no trailing space
			//
			// So to assist the user,
			// we're going to automatically remove trailing fractionGroupingSeparator's.
			
			var output = input
			if (formatter.maximumFractionDigits > 3) && input.contains(formatter.decimalSeparator) {
					
				let separator = FormattedAmount.fractionGroupingSeparator
				if input.hasSuffix(separator) {
					
					let endIdx = input.index(input.endIndex, offsetBy: -separator.count)
					let substr = input[input.startIndex ..< endIdx]
					output = String(substr)
				}
			}
			
			return Succeed(output, number.doubleValue)
		}
		
		log.debug("sweet !")
		
		if formatter.maximumFractionDigits > 3 {
			// The number may have a large fraction component.
			// See discussion in: FormattedAmount.withFormattedFractionDigits()
			//
			let formattedAmount = FormattedAmount(
				currency: unit,
				digits: formattedInput,
				decimalSeparator: formatter.decimalSeparator
			)
			let betterFormattedInput = formattedAmount.withFormattedFractionDigits().digits
			
			return Succeed(betterFormattedInput, number.doubleValue)
		} else {
			return Succeed(formattedInput, number.doubleValue)
		}
	}
}
