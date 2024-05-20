import SwiftUI

fileprivate let filename = "ChannelSizeImpactWarning"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ChannelSizeImpactWarning: View {
	
	@State var doNotShowAgain = false
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
			footer()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Channel size impacted")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 20) {
			
			Text(
			"""
			Funds sent on-chain are taken from your side of the channel, \
			reducing the channel size by the same amount. Your inbound \
			liquidity remains the same.
			"""
			)
			
			Toggle(isOn: $doNotShowAgain) {
				Text("Don't show this message again")
					.foregroundColor(.appAccent)
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
		}
		.padding(.horizontal)
		.padding(.top)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: .center, spacing: 0) {
			Spacer()
			Button("OK") {
				close()
			}
		}
		.font(.title3)
		.padding()
		.padding(.top)
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	func close() {
		log.trace("close()")
		
		if doNotShowAgain {
			Prefs.shared.doNotShowChannelImpactWarning = true
		}
		smartModalState.close()
	}
}
