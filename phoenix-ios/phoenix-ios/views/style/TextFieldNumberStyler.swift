import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TextFieldNumberStyler"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum TextFieldNumberStylerError: Error {
	case emptyInput
	case invalidInput
}

/// This tool allows a TextField to be styled & parsed while typing.
///
/// To use:
/// - create an instance of TextFieldNumberStyler within your ViewBuilder code
/// - create TextField using the styler's `amountProxy`
///
/// ```
/// let formatter: NumberFormatter
/// @State var amount String
/// @State var parsedAmount: Result<NSNumber, TextFieldPercentParserError>
///
/// var body: some View {
///   let parser = TextFieldNumberStyler(formatter: formatter, amount: $amount, parsedAmount: $parsedAmount)
///   TextField("percent", text: parser.amountProxy)
///       .onChange(of: amount) { _ in
///           // new values for amount & parsedAmount available now
///       }
/// }
/// ```
///
struct TextFieldNumberStyler {
	
	let formatter: NumberFormatter
	@Binding private var amount: String
	@Binding private var parsedAmount: Result<NSNumber, TextFieldNumberStylerError>
	
	/// This is called if the user manually edits the TextField.
	/// Which is distinct from `.onChange(of: amount)` which may be triggered via code.
	private let userDidEdit: (() -> Void)?
	
	init(
		formatter: NumberFormatter,
		amount: Binding<String>,
		parsedAmount: Binding<Result<NSNumber, TextFieldNumberStylerError>>,
		userDidEdit: (() -> Void)? = nil
	) {
		self.formatter = formatter
		self._amount = amount
		self._parsedAmount = parsedAmount
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
				let (newAmount, result) = TextFieldNumberStyler.parse(input: input, formatter: formatter)
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
	 */
	static func parse(
		input: String,
		formatter: NumberFormatter
	) -> (String, Result<NSNumber, TextFieldNumberStylerError>)
	{
		let Fail = {(_ error: TextFieldNumberStylerError) ->
			(String, Result<NSNumber, TextFieldNumberStylerError>) in
			
			log.debug("-> Fail")
			return (input, Result.failure(error))
		}
		
		let Succeed = {(_ amount: String, parsedAmount: NSNumber) ->
			(String, Result<NSNumber, TextFieldNumberStylerError>) in
			
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
		
		// Remove formatting characters from the input
		
		let RemoveGroupingCharacters = {(_ str: String) -> String in
			
			if formatter.usesGroupingSeparator {
				if let groupingSeparator = formatter.groupingSeparator {
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
			// There are a couple reasons why this happens.
			// See `TextFieldCurrencyStyler` for explanations and workarounds.
			//
			// Since most of the reasons and fixes occur within decimal places,
			// we haven't ported the fixes to this class, since we mostly deal with whole numbers here,
			// and with unformatted decimal numbers.
			
			log.debug("fallback")
			return Succeed(input, number)
		}
		
		log.debug("success !")
			
		return Succeed(formattedInput, number)
	}
}
