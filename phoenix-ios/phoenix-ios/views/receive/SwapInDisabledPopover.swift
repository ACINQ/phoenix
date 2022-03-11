import SwiftUI

struct SwapInDisabledPopover: View {
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Some Services Disabled")
					.font(.title3)
					.padding(.bottom)
				
				Text(
					"""
					The bitcoin mempool is congested and fees are very high. \
					The on-chain swap service has been temporarily disabled.
					"""
				)
				.lineLimit(nil)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close()
				} label : {
					Text("Try again later").font(.headline)
				}
			}
		}
		.padding()
	}
}
