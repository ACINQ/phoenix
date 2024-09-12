import SwiftUI

/// Makes a GroupBox look like a Section within a List with style `.insetGrouped`
///
struct InsetGroupBoxStyle: GroupBoxStyle {
	
	func makeBody(configuration: GroupBoxStyleConfiguration) -> some View {
		VStack(alignment: .leading) {
			configuration.label
			configuration.content
		}
		.padding()
		.background(Color(.secondarySystemGroupedBackground))
		.cornerRadius(10)
		.padding(.horizontal)
	}
}
