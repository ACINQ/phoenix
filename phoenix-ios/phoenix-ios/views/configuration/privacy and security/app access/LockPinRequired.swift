import SwiftUI

fileprivate let filename = "LockPinRequired"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LockPinRequired: View {
	
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
			Text("Lock PIN Required")
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
			
			Text("This wallet is **hidden**, and a hidden wallet requires a Lock PIN to be set.")
				.font(.title3)
			
			Text("(The only way to access a hidden wallet is by typing in the Lock PIN)")
				.font(.body)
				.foregroundStyle(.secondary)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button {
					navigateToWalletMetadata()
				} label: {
					Text("Change Hidden Wallet")
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
	
	func navigateToWalletMetadata() {
		log.trace(#function)
		
		smartModalState.close(animationCompletion: {
			deepLinkManager.broadcast(.walletMetadata)
		})
	}
	
	func closeSheet() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
