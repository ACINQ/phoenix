import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BgRefreshDisabledPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct BgRefreshDisabledPopover: View {
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading, spacing: 0) {
				Text("Watchtower Disabled")
					.font(.title3)
					.padding(.bottom)
				
				Text(
					"""
					The watchtower occasionally runs in the background (every few days) \
					and monitors your funds to ensure everything is safe.
					"""
				)
				.lineLimit(nil)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				.padding(.bottom, 15)
				
				Text(
					"""
					You disabled "background app refresh" for Phoenix which prevents the watchtower \
					from running. Please enable it to ensure your funds remain safe.
					"""
				)
				.lineLimit(nil)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close()
				} label : {
					Text("Ignore").font(.title3)
				}
				.padding(.trailing)
				Button {
					openIosSettings()
				} label: {
					Text("Fix It").font(.title3)
				}
			}
		}
		.padding()
	}
	
	func openIosSettings() {
		log.trace("openIosSettings()")
		
		if let bundleIdentifier = Bundle.main.bundleIdentifier,
			let url = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
		{
			if UIApplication.shared.canOpenURL(url) {
				UIApplication.shared.open(url)
			}
		}
		
		popoverState.close()
	}
}
