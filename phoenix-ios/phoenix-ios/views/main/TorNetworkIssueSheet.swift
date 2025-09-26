import SwiftUI

fileprivate let filename = "TorNetworkIssueSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct TorNetworkIssueSheet: View {
	
	@Environment(\.openURL) var openURL
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("No access to the Tor network")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			
			Spacer()
			
			Button {
				closeSheet()
			} label: {
				Image(systemName: "xmark").imageScale(.medium).font(.title2)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			content_message()
				.padding(.bottom, 20)
			content_buttons()
		}
		.padding(.all)
	}
	
	@ViewBuilder
	func content_message() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			Text("Phoenix needs access to Tor to function properly.")
			Text("Make sure your Tor Proxy VPN app is up and running, and that it's connected to Tor.")
			Text("If you don't have a Tor VPN app, install one. We recommend Orbot.")
		}
		.font(.callout)
	}
	
	@ViewBuilder
	func content_buttons() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			Button {
				openTorSettings()
			} label: {
				Label("Open Tor settings", systemImage: "gearshape.fill")
					.frame(maxWidth: .infinity)
			}
			.buttonStyle(.borderedProminent)
			
			Button {
				openOrbotWebsite()
			} label: {
				Label("Open Orbot website", systemImage: "link")
					.frame(maxWidth: .infinity)
			}
			.buttonStyle(.bordered)
			
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func openTorSettings() {
		log.trace(#function)
		
		smartModalState.close()
		deepLinkManager.broadcast(DeepLink.torSettings)
	}
	
	func openOrbotWebsite() {
		log.trace(#function)
		
		if let url = URL(string: "https://orbot.app/") {
			openURL(url)
		}
	}
	
	func closeSheet() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
