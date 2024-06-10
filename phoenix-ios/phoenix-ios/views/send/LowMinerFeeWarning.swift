import SwiftUI

fileprivate let filename = "LowMinerFeeWarning"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LowMinerFeeWarning: View {
	
	@Binding var showLowMinerFeeWarning: Bool
	
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
			Text("Low feerate!")
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
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text(
			"""
			Transactions with insufficient feerate may linger for days or weeks without confirming.
			
			Choosing the feerate is your responsibility. \
			Once sent, this transaction cannot be cancelled, only accelerated with higher fees.
			
			Are you sure you want to proceed?
			"""
			)
		}
		.padding(.horizontal)
		.padding(.top)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: .center, spacing: 0) {
			Spacer()
			
			Button("Back") {
				cancelButtonTapped()
			}
			.padding(.trailing)
				
			Button("I understand") {
				confirmButtonTapped()
			}
		}
		.font(.title3)
		.padding()
		.padding(.top)
	}
	
	func cancelButtonTapped() {
		log.trace("cancelButtonTapped()")
		showLowMinerFeeWarning = false
	}
	
	func confirmButtonTapped() {
		log.trace("confirmButtonTapped()")
		smartModalState.close()
	}
}
