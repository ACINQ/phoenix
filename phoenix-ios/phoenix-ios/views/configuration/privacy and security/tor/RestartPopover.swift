import SwiftUI

fileprivate let filename = "RestartPopover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct RestartPopover: View {
	
	@EnvironmentObject var popoverState: PopoverState
	
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
			Text("Restart required")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			
			Spacer()
			
			Button {
				closeButtonTapped()
			} label: {
				Image(systemName: "xmark").imageScale(.medium).font(.title2)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(popoverState.dismissable)
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
			
			Text("You must restart Phoenix for the changes to take affect.")
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button {
					terminateApp()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Text("Terminate App").fontWeight(.medium)
					}
					.foregroundStyle(Color.appNegative)
					.font(.title3)
				}
			} // </HStack>
		} // </VStack>
		.padding(.all)
	}
	
	func terminateApp() {
		log.trace("terminateApp()")
		
		exit(0)
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		popoverState.close()
	}
}
