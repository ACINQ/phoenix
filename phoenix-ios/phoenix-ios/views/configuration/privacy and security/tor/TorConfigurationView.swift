import SwiftUI

fileprivate let filename = "TorConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct TorConfigurationView: View {

	@State var toggleState = GroupPrefs.shared.isTorEnabled
	@State var isTorEnabled = GroupPrefs.shared.isTorEnabled

	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Tor", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_toggle()
			if isTorEnabled {
				section_info()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_toggle() -> some View {
		
		Section {
			Toggle(isOn: $toggleState.animation()) {
				Text("Use Tor")
			}.onChange(of: toggleState) {
				self.toggleStateChanged($0)
			}
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				Text(
					"""
					With Tor enabled, Phoenix will require onion addresses \
					for Lightning and Electrum connections.
					"""
				)
				.padding(.top, 5)
				
				Text(
					"""
					A Tor VPN must be running on your device. \
					This VPN is not provided by Phoenix. \
					You need to install one.
					"""
				)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer()
					Button {
						openLink()
					} label: {
						Text("Learn more")
					}
				}
				.padding(.top, 5)
			}
			
		} header: {
			Text("How it works")
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------

	func toggleStateChanged(_ isEnabled: Bool) {
		log.trace("toggleStateChanged: \(isEnabled)")
		
		if isEnabled {
			smartModalState.display(dismissable: false) {
				UsingTorSheet(didConfirm: usingTorSheet_didConfirm)
			}
		} else {
			smartModalState.display(dismissable: false) {
				DisablingTorSheet(didConfirm: disablingTorSheet_didConfirm)
			}
		}
	}
	
	func usingTorSheet_didConfirm() {
		log.trace("usingTorSheet_didConfirm()")
		
		isTorEnabled = true
		GroupPrefs.shared.isTorEnabled = true
	}
	
	func disablingTorSheet_didConfirm() {
		log.trace("disablingTorSheet_didConfirm()")
		
		isTorEnabled = false
		GroupPrefs.shared.isTorEnabled = false
	}
	
	func openLink() {
		log.trace("openLink()")
		
		guard let link = URL(string: "https://phoenix.acinq.co/faq#how-to-use-tor-on-phoenix") else {
			return
		}
		
		if UIApplication.shared.canOpenURL(link) {
			UIApplication.shared.open(link)
		}
	}
}
