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
				Phoenix now forces onion addresses for Lightning and Electrum \
				connections when Tor is enabled.
				
				However, the embedded Tor proxy has been removed ; instead you \
				need to install a third-party Tor Proxy VPN application such \
				as Orbot to connect.
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
