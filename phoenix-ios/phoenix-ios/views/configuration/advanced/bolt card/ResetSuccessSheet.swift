import SwiftUI
import PhoenixShared

fileprivate let filename = "ResetSuccessSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ResetSuccessSheet: View {
	
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
			
			Text("Your card is now reset.")
				.font(.title2.weight(.medium))
				.padding(.bottom, 8)
			
			Image(systemName: "checkmark.circle")
				.resizable()
				.scaledToFit()
				.frame(width: 96, height: 96)
				.foregroundColor(.appAccent)
				.padding(.bottom, 24)
			
			Text("It can be linked again with any wallet.")
				.font(.callout)
				.padding(.bottom, 16)
		}
		.frame(maxWidth: .infinity)
		.multilineTextAlignment(.center)
		.padding(.horizontal)
	}
	
	func closeButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
}



