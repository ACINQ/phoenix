import SwiftUI

enum ScrollingDismissesKeyboardMode {
	case automatic
	case immediately
	case interactively
	case never
	
	@available(iOS 16.0, *)
	func convert() -> ScrollDismissesKeyboardMode {
		switch self {
			case .automatic     : return ScrollDismissesKeyboardMode.automatic
			case .immediately   : return ScrollDismissesKeyboardMode.immediately
			case .interactively : return ScrollDismissesKeyboardMode.interactively
			case .never         : return ScrollDismissesKeyboardMode.never
		}
	}
}

struct ScrollingDismissesKeyboard: ViewModifier {
	let mode: ScrollingDismissesKeyboardMode
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.scrollDismissesKeyboard(mode.convert())
		} else {
			content
		}
	}
}

extension View {
	func scrollingDismissesKeyboard(_ mode: ScrollingDismissesKeyboardMode) -> some View {
		ModifiedContent(
			content: self,
			modifier: ScrollingDismissesKeyboard(mode: mode)
		)
	}
}
