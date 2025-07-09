import SwiftUI

fileprivate let filename = "WalletIconPicker"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletIconPicker: View {
	
	let didSelect: (WalletIcon) -> Void
	
	@EnvironmentObject var deviceInfo: DeviceInfo
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
			Text("Select built-in icon")
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
		
		ScrollView {
			LazyVGrid(columns: [GridItem(.adaptive(minimum: (48+(8*2))))], spacing: 0) {
				ForEach(WalletIcon.allCases, id: \.self) { icon in
					WalletImage(filename: icon.filename, size: 48)
						.onTapGesture {
							select(icon)
						}
						.padding(8)
				}
			} // </LazyVGrid>
			.padding(.horizontal)
			
		} // </ScrollView>
		.frame(maxHeight: scrollViewMaxHeight)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var scrollViewMaxHeight: CGFloat {
		
		if deviceInfo.isShortHeight {
			return CGFloat.infinity
		} else {
			return deviceInfo.windowSize.height * 0.6
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func select(_ icon: WalletIcon) {
		log.trace("select(\(icon))")
		
		didSelect(icon)
		smartModalState.close()
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
	}
}
