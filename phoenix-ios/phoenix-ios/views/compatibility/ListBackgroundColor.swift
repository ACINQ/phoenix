import SwiftUI


struct ListBackgroundColor: ViewModifier {
	let color: Color
	
	@ViewBuilder
	func body(content: Content) -> some View {
		content
            .background(color)
            .scrollContentBackground(.hidden)
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
