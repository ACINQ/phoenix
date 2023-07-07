import SwiftUI

struct InfoPopoverWindow<Content: View>: View {
	
	let content: () -> Content
	
	@ViewBuilder
	var body: some View {
		
		content()
			.padding(.all, 16)
			.background(Color(.systemBackground))
			.cornerRadius(12)
			.shadow(
				 color: Color(.label.withAlphaComponent(0.25)),
				 radius: 40,
				 x: 0,
				 y: 4
			)
	}
}
