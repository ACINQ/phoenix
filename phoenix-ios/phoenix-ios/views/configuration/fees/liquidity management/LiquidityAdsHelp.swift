import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LiquidityAdsHelp"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct LiquidityAdsHelp: View {
	
	@Binding var isShowing: Bool
	
	@ViewBuilder
	var body: some View {
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
			Spacer()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		// close button
		// (required for landscapse mode, where swipe-to-dismiss isn't possible)
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				close()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
		} // </HStack>
		.padding()
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 24) {
			
			Text("**Imagine that your wallet is a bucket**, and your balance is the water in the bucket.")
			
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				Image("bucket")
					.resizable()
					.scaledToFit()
					.frame(maxWidth: 50, maxHeight: 50)
				
				Text(
					"""
					Spending = pouring water out
					Receiving = adding more water
					"""
				)
			} // </HStack>
			
			Text(
			"""
			If the bucket needs to be resized to allow for more water, \
			that will require an on-chain operation with a mining fee.
			""")
			
			Text(
			"""
			You may be able to reduce your fees by increasing your bucket size \
			ahead of a series of incoming payments.
			""")
			
			HStack(alignment: .center, spacing: 0) {
				Spacer()
				Text("\(Image(systemName: "link")) [more info](https://phoenix.acinq.co/faq#what-is-inbound-liquidity)")
				Spacer()
			}
			.padding(.top)
			
		} // </VStack>
		.frame(maxWidth: .infinity, alignment: .center)
		.padding(.horizontal)
		.padding(.horizontal)
		.padding(.bottom)
	}
	
	func close() {
		log.trace("close()")
		
		isShowing = false
	}
}
