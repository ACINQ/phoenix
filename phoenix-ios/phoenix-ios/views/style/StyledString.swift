/**
 * Credits:
 * https://www.swiftbysundell.com/articles/styled-localized-strings-in-swift/
 * https://www.swiftbysundell.com/articles/string-parsing-in-swift/
*/

import Foundation
import UIKit
import SwiftUI

/// Allows a string to be styled according to symbols in the text:
/// - `This is **bold** text`
/// - `This is __italic__ text`
/// - `This is **__bold and italic__** text`
///
/// Purpose: To facilitate and simplify the localization process.
///
struct StyledString: ExpressibleByStringLiteral {
	
	// NB: static variables are implicitly lazy
	static let swiftUITextCache = Cache<String, Text>(countLimit: 50)
	
	var text: String
	
	init(_ text: String) {
		self.text = text
	}
	init(stringLiteral value: StringLiteralType) {
		text = value
	}
	
	private struct Token {
		var string: String
		var isBold: Bool
		var isItalic: Bool
	}
	
	private func render<T>(
		into initialResult: T,
		cache: Cache<String, T>,
		handler: (inout T, Token) -> Void
	) -> T {
		if let cached = cache[text] {
			 return cached
		}
		
		var buffer = ""
		var isBold = false
		var isItalic = false
		var tokens = [Token]()
		
		func addToken(dropCount: Int) {
			
			var string: String
			if dropCount > 0 {
				
				let endIdx = buffer.index(buffer.endIndex, offsetBy: -dropCount)
				let substr = buffer[buffer.startIndex ..< endIdx]
				string = String(substr)
				
			} else {
				string = buffer
			}
			
			if string.count > 0 {
				tokens.append(Token(string: string, isBold: isBold, isItalic: isItalic))
			}
		}
		
		func parse(_ character: Character) {
			
			buffer.append(character)
			
			if buffer.hasSuffix("**") {
				
				addToken(dropCount: 2)
				isBold = !isBold
				buffer = ""
				
			} else if buffer.hasSuffix("__") {
				
				addToken(dropCount: 2)
				isItalic = !isItalic
				buffer = ""
			}
		}
		
		text.forEach(parse)
		addToken(dropCount: 0)
		
		var result = initialResult
		for token in tokens {
			handler(&result, token)
		}
		
		cache[text] = result
		return result
	}
	
	func styledText() -> Text {
		render(
			into: Text(""),
			cache: Self.swiftUITextCache,
			handler: { fullText, token in
				var text = Text(token.string)
				if token.isBold {
					text = text.bold()
				}
				if token.isItalic {
					text = text.italic()
				}
				
				fullText = fullText + text
			}
		)
	}
}

extension Text {
	init(styled styledString: StyledString) {
		self = styledString.styledText()
	}
	init(styled string: String) {
		self.init(styled: StyledString(string))
	}
}
