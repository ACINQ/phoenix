import SwiftUI

struct ComingSoonView: View {
	
	@ViewBuilder
	var body: some View {
		
		List {
			Text("Coming Soon")
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
}
