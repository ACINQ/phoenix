import SwiftUI

struct PriorityBoxStyle: GroupBoxStyle {
	
	let width: CGFloat?
	let height: CGFloat?
	let disabled: Bool
	let selected: Bool
	let tapped: () -> Void
	
	func makeBody(configuration: GroupBoxStyleConfiguration) -> some View {
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			configuration.label
				.font(.headline)
			configuration.content
		}
		.frame(width: width?.advanced(by: -16.0), height: height?.advanced(by: -16.0))
		.padding(.all, 8)
		.background(RoundedRectangle(cornerRadius: 8, style: .continuous)
			.fill(Color(UIColor.quaternarySystemFill)))
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(selected ? Color.appAccent : Color(UIColor.quaternarySystemFill), lineWidth: 1)
		)
		.onTapGesture {
			tapped()
		}
	}
}
