import SwiftUI

struct TextTracking: ViewModifier {
	let amount: CGFloat
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.tracking(amount)
		} else {
			content
		}
	}
}

extension View {
	func textTracking(_ amount: CGFloat) -> some View {
		ModifiedContent(
			content: self,
			modifier: TextTracking(amount: amount)
		)
	}
}
