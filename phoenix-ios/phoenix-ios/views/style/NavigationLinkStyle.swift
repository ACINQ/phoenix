import SwiftUI

/// Used on iOS 16, when NavigationLink doesn't meet our purposes.
///
struct LabelStyle_NavigationLink: LabelStyle {
	
	@ViewBuilder
	func makeBody(configuration: LabelStyleConfiguration) -> some View {
		HStack(alignment: VerticalAlignment.center, spacing: 4) {
			Label(title: {
				configuration.title.foregroundColor(.primary)
			}, icon: {
				configuration.icon
			})
			Spacer(minLength: 0).layoutPriority(-1)
			NavigationLinkArrow()
		}
	}
}

/// Used on iOS 16, when NavigationLink doesn't meet our purposes.
///
struct ButtonStyle_NavigationLink: ButtonStyle {
	
	@ViewBuilder
	func makeBody(configuration: ButtonStyleConfiguration) -> some View {
		HStack(alignment: VerticalAlignment.center, spacing: 4) {
			configuration.label
			Spacer(minLength: 0).layoutPriority(-1)
			NavigationLinkArrow()
		}
	}
}

struct NavigationLinkArrow: View {
	@ViewBuilder
	var body: some View {
		Image(systemName: "chevron.forward")
		//	.font(Font.system(.footnote).weight(.semibold))
			.font(.footnote.bold())
			.foregroundColor(Color(UIColor.tertiaryLabel))
	}
}
