import SwiftUI
import PhoenixShared

fileprivate let filename = "ResetCardSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ResetCardSheet: View {
	
	let card: BoltCardInfo
	let didRequestReset: () -> Void
	
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
			Text("Reset card")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
			Spacer()
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
			
			Text("This will clear the card, allowing it to be linked again with any wallet.")
			
			if !card.isArchived {
				Text(
					"""
					Afterwards, the card will be Archived, and can never be activated again. \
					The card will remain in your list, but will be moved to the Archived section.
					"""
				)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button {
					cancelButtonTapped()
				} label: {
					Text("Cancel").font(.title3)
				}
				.padding(.trailing, 24)
				
				Button {
					resetButtonTapped()
				} label: {
					Text("Reset").font(.title3).foregroundStyle(Color.red)
				}
			}
			.padding(.top, 16) // extra padding
		}
		.padding(.top, 16)
		.padding(.horizontal)
	}
	
	func cancelButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
	
	func resetButtonTapped() {
		log.trace(#function)
		
		smartModalState.close(animationCompletion: {
			didRequestReset()
		})
	}
}


