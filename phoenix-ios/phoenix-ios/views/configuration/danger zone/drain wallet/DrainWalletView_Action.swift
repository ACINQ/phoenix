import SwiftUI
import PhoenixShared

fileprivate let filename = "DrainWalletView_Action"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DrainWalletView_Action: MVISubView {
	
	@ObservedObject var mvi: MVIState<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
	let expectedTxCount: Int
	let popTo: (PopToDestination) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Drain wallet", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarBackButtonHidden()
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_sent()
			section_info()
			section_button()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_sending() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 5) {
					
					Image(systemName: "paperplane.fill")
						.imageScale(.medium)
						.foregroundColor(.gray)
					
					Text("Sending funds…")
					
				} // </HStack>
				.font(.title)
				
			} // </VStack>
			.padding(.vertical, 5)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_sent() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 5) {
					
					Image(systemName: "paperplane.fill")
						.imageScale(.medium)
						.foregroundColor(.appPositive)
					
					Text("Funds sent")
					
				} // </HStack>
				.font(.title)

			} // </VStack>
			.padding(.vertical, 5)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {

				Label {
					Group {
						if expectedTxCount > 1 {
							Text("Expect to receive \(expectedTxCount) separate payments.")
						} else {
							Text("The closing transaction is in your transactions list.")
						}
					}
					.fixedSize(horizontal: false, vertical: true)
				} icon: {
					Image(systemName: "archivebox")
						.imageScale(.medium)
				}
				
				Label {
					Text(
						"""
						If you're planning on deleting your wallet data, \
						we recommend waiting until after the closing transactions \
						have been confirmed on the blockchain.
						"""
					)
					.fixedSize(horizontal: false, vertical: true)
				} icon: {
					Image(systemName: "lightbulb")
						.imageScale(.medium)
						.foregroundColor(.appWarn)
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center) {
				
				Button {
					doneButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "checkmark")
							.imageScale(.medium)
						Text("Done")
					}
					.font(.headline)
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func doneButtonTapped() {
		log.trace("doneButtonTapped()")
		
		if #available(iOS 17, *) {
			navCoordinator.path.removeAll()
		} else {
			popTo(.RootView(followedBy: nil))
			presentationMode.wrappedValue.dismiss()
		}
	}
}
