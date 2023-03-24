import SwiftUI


struct ListBackgroundColor: ViewModifier {
	let color: Color
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.background(color)
				.scrollContentBackground(.hidden)
		} else {
			content
		}
	}
}

extension View {
	func listBackgroundColor(_ color: Color) -> some View {
		ModifiedContent(
			content: self,
			modifier: ListBackgroundColor(color: color)
		)
	}
}
