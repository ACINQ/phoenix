import SwiftUI

struct InlineSection<Header: View, Content: View>: View {
	
	let header: Header
	let content: Content
	
	init(
		@ViewBuilder header headerBuilder: () -> Header,
		@ViewBuilder content contentBuilder: () -> Content
	) {
		header = headerBuilder()
		content = contentBuilder()
	}
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 12) {
					content
				}
				Spacer(minLength: 0)
			}
			.padding(.vertical, 10)
			.padding(.horizontal, 16)
			.background {
				Color(UIColor.secondarySystemGroupedBackground).cornerRadius(10)
			}
			.padding(.horizontal, 16)
		}
		.padding(.vertical, 16)
	}
}
