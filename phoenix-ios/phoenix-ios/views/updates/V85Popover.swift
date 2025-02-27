import SwiftUI

fileprivate let filename = "V85Popover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct V85Popover: View {
	
	@EnvironmentObject var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text("Version 2.5.0: Tor changes")
				.font(.headline)
			
			Text(
				"""
				Enabling Tor now requires installing a third-party Tor Proxy VPN app such as Orbot.
				
				This makes background payments much more reliable with Tor.
				
				Also Phoenix now always uses onion addresses for Lightning and Electrum \
				connections when Tor is enabled.
				"""
			)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Button {
					popoverState.close()
				} label: {
					Text("OK").font(.title3)
				}
			}
		}
		.padding(.all)
	}
}
