import SwiftUI

fileprivate let filename = "NewCardSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct NewCardSheet: View {
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
		}
		.padding(.horizontal)
		.padding(.top, 8)
		.padding(.bottom, 12)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Your card is now ready to use.")
				.font(.title2.weight(.medium))
				.padding(.bottom, 8)
			
			Image(systemName: "checkmark.circle")
				.resizable()
				.scaledToFit()
				.frame(width: 96, height: 96)
				.foregroundColor(.appAccent)
				.padding(.bottom, 24)
			
			Text("Use this screen to manage your card anytime you need.")
				.font(.callout)
				.padding(.bottom, 24)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
				Text("Be your own bank").font(.headline)
				
				if #available(iOS 18, *) {
					Image(systemName: "bitcoinsign.bank.building")
				} else {
					Image(systemName: "bitcoinsign.circle")
				}
			}
			.padding(.bottom, 8)
			
			Text("Remember:")
				.textCase(.uppercase)
				.font(.subheadline)
				.foregroundStyle(Color.secondary)
				.padding(.bottom, 4)
			
			Text("Your bank (this device) needs to be online to process payments with your card.")
				.font(.subheadline)
				.foregroundStyle(Color.secondary)
				.padding(.bottom, 16)
		}
		.multilineTextAlignment(.center)
		.padding(.horizontal)
	}
	
	func closeButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
