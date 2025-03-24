import SwiftUI

fileprivate let filename = "PrerequisitesSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PrerequisitesSheet: View {
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
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
			Text("Prerequisites")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
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
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 16) {
			
			Text(
				"""
				You need a BIP-353 address before you can create a card.
				"""
			)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Button {
					goToExperimental()
				} label: {
					Text("Get address")
				}
			}
		}
		.padding(.top, 16)
		.padding(.horizontal)
	}
	
	func goToExperimental() {
		log.trace("goToExperimental()")
		
		smartModalState.close(animationCompletion: {
			deepLinkManager.broadcast(.bip353Registration)
		})
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
