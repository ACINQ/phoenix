import SwiftUI

fileprivate let filename = "WhichPinSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WhichPinSheet: View {
	
	enum Choice {
		case systemPasscode
		case customPin
	}
	
	let currentChoice: Choice
	
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
			Text("Which PIN?")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeSheet()
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
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Group {
				if currentChoice == .systemPasscode {
					Text(
						"""
						To enable a custom PIN, you must first disable the "allow passcode fallback" option.
						"""
					)
				} else {
					Text(
						"""
						To enable the "allow passcode fallback" option, you must first disable the custom PIN.
						"""
					)
				}
			} // </Group>
			.font(.title3)
		}
		.padding()
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
	}
}
