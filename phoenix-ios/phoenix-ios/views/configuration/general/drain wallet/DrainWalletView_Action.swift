import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "DrainWalletView_Action"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct DrainWalletView_Action: MVISubView {
	
	@ObservedObject var mvi: MVIState<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
	let expectedTxCount: Int
	let popToRoot: () -> Void
	
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
				
				let msg = (expectedTxCount > 1)
					? String(format: NSLocalizedString(
						"Expect to receive %d separate payments.",
						comment: "label text"
					), expectedTxCount)
					: NSLocalizedString(
						"The closing transaction is in your transactions list.",
						comment: "label text"
					)

				Label {
					Text(styled: msg)
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
		
		popToRoot()
	}
}
