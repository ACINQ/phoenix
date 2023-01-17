import SwiftUI

struct TorConfigurationView: View {

	@State var isTorEnabled = GroupPrefs.shared.isTorEnabled

	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Tor", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			Section {

				Toggle(isOn: $isTorEnabled.animation()) {
					if isTorEnabled {
						Text("Tor is enabled")
					} else {
						Text("Tor is disabled")
					}
				}.onChange(of: isTorEnabled) { newValue in
					self.toggleTor(newValue)
				}

				Text(
					"""
					You can improve your privacy by only using Tor when connecting to an Electrum server or \
					to your Lightning peer. This will slightly slow down your transactions.
					"""
				)
				.font(.callout)
				.lineLimit(nil)          // SwiftUI bugs
				.minimumScaleFactor(0.5) // Truncating text
				.foregroundColor(Color.secondary)
				.padding(.top, 8)
				.padding(.bottom, 4)
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}

	func toggleTor(_ isEnabled: Bool) {
		GroupPrefs.shared.isTorEnabled = isEnabled
	}
}
