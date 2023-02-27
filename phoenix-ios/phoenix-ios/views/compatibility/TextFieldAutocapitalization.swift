import SwiftUI

enum TextFieldAutocapitalization {
	case characters
	case sentences
	case words
	case never;
	
	@available(iOS 15.0, *)
	func mapped() -> TextInputAutocapitalization {
		switch self {
			case .characters : return .characters
			case .sentences  : return .sentences
			case .words      : return .words
			case .never      : return .never
		}
	}

}

struct TextFieldAutocapitalizationModifier: ViewModifier {
	let value: TextFieldAutocapitalization
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 15.0, *) {
			content
				.textInputAutocapitalization(value.mapped())
		} else {
			content
		}
	}
}

extension View {
	func textFieldAutocapitalization(_ value: TextFieldAutocapitalization) -> some View {
		ModifiedContent(
			content: self,
			modifier: TextFieldAutocapitalizationModifier(value: value)
		)
	}
}
