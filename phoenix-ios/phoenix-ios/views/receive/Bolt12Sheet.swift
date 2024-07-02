import SwiftUI

fileprivate let filename = "InboundFeeSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Bolt12Sheet: View {
	
	@Environment(\.openURL) var openURL
	
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
			Text("Lightning Offers")
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			content_message()
			content_button()
		}
		.padding(.all)
	}
	
	@ViewBuilder
	func content_message() -> some View {
		
		Text(
			"""
			This is a lightning offer (also known as a BOLT 12 invoice) which uses \
			new bleeding-edge tech.
			
			Unlike regular Lightning invoices, it does not expire \
			and can be reused at will. It can be used as a tipping address and for donations.
			
			Currently, few wallets support this new tech. If you have issues, \
			use a regular Lightning invoice instead.
			"""
		)
	}
	
	@ViewBuilder
	func content_button() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				navigateToFAQ()
			} label: {
				Text("Learn more")
			} // </Button>
		} // </HStack>
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateToFAQ() {
		log.trace("navigateToFAQ()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
	}
}
