import SwiftUI

struct ComingSoonView: View {
	
	let title: String
	
	var body: some View {
		
		List {
			Section {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer()
					Text("Coming Soon")
					Spacer()
				}
			}
		}
		.listStyle(.insetGrouped)
		.navigationTitle(title)
		.navigationBarTitleDisplayMode(.inline)
	}
}
