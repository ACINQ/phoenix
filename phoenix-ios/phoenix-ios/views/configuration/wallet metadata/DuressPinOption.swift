import SwiftUI

fileprivate let filename = "DuressPinOption"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DuressPinOption: View {
	
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
			Text("Duress PIN option")
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			Text(
				"""
				You can enter the PIN for this wallet anytime in the lock screen, \
				and Phoenix will **switch** to this wallet.
				"""
			)
			.font(.body)
			
			Text(
				"""
				Even when Phoenix is prompting you for the PIN for a different wallet.
				"""
			)
			.font(.body)
			
			Text("(This means it works as a duress PIN.)")
				.font(.body)
				.foregroundStyle(.secondary)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button {
					closeSheet()
				} label: {
					Text("OK")
				}
			}
			.font(.title3)
			.padding(.top, 10)
		}
		.padding()
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func closeSheet() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
