import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TextFieldNumberParser"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum TextFieldNumberParserError: Error {
	case emptyInput
	case invalidInput
}

/// This tool provides a binding for TextField's,
/// allowing the value to be parsed **while typing**.
///
/// To use:
/// - create an instance of TextFieldNumberParser within your ViewBuilder code
/// - create TextField using the styler's `amountProxy`
///
/// ```
/// let formatter: NumberFormatter
/// @State var amount String
/// @State var parsedAmount: Result<NSNumber, TextFieldPercentParserError>
///
/// var body: some View {
///   let parser = TextFieldNumberParser(formatter: formatter, amount: $amount, parsedAmount: $parsedAmount)
///   TextField("percent", text: parser.amountProxy)
///       .onChange(of: amount) { _ in
///           // new values for amount & parsedAmount available now
///       }
/// }
/// ```
///
struct TextFieldNumberParser {
	
	let formatter: NumberFormatter
	@Binding private var amount: String
	@Binding private var parsedAmount: Result<NSNumber, TextFieldNumberParserError>
	
	init(
		formatter: NumberFormatter,
		amount: Binding<String>,
		parsedAmount: Binding<Result<NSNumber, TextFieldNumberParserError>>
	) {
		self.formatter = formatter
		self._amount = amount
		self._parsedAmount = parsedAmount
	}
	
	var amountProxy: Binding<String> {
		
		Binding<String>(
			get: {
				log.debug("<- get: \"\(self.amount)\"")
				return self.amount
			},
			set: { (input: String) in
				log.debug("-> set")
				self.parsedAmount = TextFieldNumberParser.parse(input: input, formatter: formatter)
				self.amount = input // contract: always change parsedAmount before amount
			}
		)
	}
	
	static func parse(
		input: String,
		formatter: NumberFormatter
	) -> Result<NSNumber, TextFieldNumberParserError>
	{
		let trimmedInput = input.trimmingCharacters(in: CharacterSet.whitespaces)
		if trimmedInput.isEmpty {
			return Result.failure(.emptyInput)
		}
		
		if let number = formatter.number(from: input) {
			return Result.success(number)
		} else {
			return Result.failure(.invalidInput)
		}
	}
}
