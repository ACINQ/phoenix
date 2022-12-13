import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ForceCloseChannelsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ForceCloseChannelsView : MVIView {
	
	@StateObject var mvi = MVIState({ $0.forceCloseChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Force close channels", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if mvi.model is CloseChannelsConfiguration.ModelLoading {
				section_loading()

			} else if mvi.model is CloseChannelsConfiguration.ModelReady {
				if channelsCount() == 0 {
					section_zeroChannels()
				} else {
					section_disclaimer()
					if let address = address() {
						section_address(address)
					}
					section_button()
				}

			} else if mvi.model is CloseChannelsConfiguration.ModelChannelsClosed {
				section_done()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_loading() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle())
				Text("Loading wallet...")
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_zeroChannels() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 16) {
				
				Text("You currently don't have any channels that can be closed.")
				
				Text("Payment channels are automatically created when you receive payments.")
					.font(.subheadline)
					.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.vertical, 8)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_disclaimer() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 35) {
				
				Text("Force close allows you to unilaterally close your channels.")
				
				Text(styled: NSLocalizedString(
					"""
					This feature is not a "fix everything magic button". \
					It is here as a safety measure and \
					**should only be used in extreme scenarios**. \
					For example, if your peer (ACINQ) disappears permanently, \
					preventing you from spending your money. In all other cases, \
					**if you experience issues with Phoenix you should contact support**.
					""",
					comment: "ForceCloseChannelsView"
				))
				
				Text(styled: NSLocalizedString(
					"""
					Force closing channels will cost you money (to cover the on-chain fees) \
					and will cause your funds to be locked for days. \
					**Do not uninstall the app until your channels are fully closed or you will lose money.**
					""",
					comment: "ForceCloseChannelsView"
				))
				
				Text("Do not use this feature if you don't fully understand what it does.")
					.bold()
				
			} // </VStack>
			.padding(.vertical, 8)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_address(_ address: String) -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Text("Funds will eventually be sent to:")
					.padding(.bottom)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					Image(systemName: "link")
						.imageScale(.large)
						.padding(.trailing, 4)
					
					Text(address)
						.font(.system(.body, design: .monospaced))
						.lineLimit(nil)
						.minimumScaleFactor(0.5) // problems with truncation
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = address
							}) {
								Text("Copy")
							}
						}
				}
				.padding(.bottom)
				
				Text("This address is derived from your seed and belongs to you.")
					.font(.subheadline)
					.foregroundColor(.secondary)
				
			} // </VStack>
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Button {
					forceCloseChannels()
				} label: {
					Label {
						Text("Force close channels")
					} icon: {
						Image(systemName: "cross.circle")
					}
					.foregroundColor(.appNegative)
				}
				.padding(.vertical, 8)
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_done() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Image(systemName: "paperplane.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 64, height: 64)
					.foregroundColor(Color.appPositive)
					.padding(.bottom, 10)

				Text("Funds sent")
					.font(.title)
					.padding(.bottom, 30)

				let expectedTxCount = nonZeroChannelsCount()
				let msg = (expectedTxCount > 1)
					? String(format: NSLocalizedString(
						"Expect to receive %d separate payments.",
						comment:	"label text"
					), expectedTxCount)
					: NSLocalizedString(
						"The closing transaction is in your transactions list.",
						comment: "label text"
					)
					
				Text(styled: msg)
					.multilineTextAlignment(.leading)
					.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs

			} // </VStack>
			.padding(.vertical, 8)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	func address() -> String? {
		
		if let model = mvi.model as? CloseChannelsConfiguration.ModelReady {
			return model.address
		} else {
			return nil
		}
	}
	
	func channels() -> [CloseChannelsConfiguration.ModelChannelInfo]? {
		
		if let model = mvi.model as? CloseChannelsConfiguration.ModelReady {
			return model.channels
		} else if let model = mvi.model as? CloseChannelsConfiguration.ModelChannelsClosed {
			return model.channels
		} else {
			return nil
		}
	}
	
	func channelsCount() -> Int {
		
		if let channels = channels() {
			return channels.count
		} else {
			return 0
		}
	}
	
	func nonZeroChannelsCount() -> Int {
		
		if let channels = channels() {
			return channels.filter { $0.balance > 0 }.count
		} else {
			return 0
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func forceCloseChannels() -> Void {
		log.debug("forceCloseChannels()")
		
		popoverState.display(dismissable: false) {
			ConfirmationPopover(
				confirmAction: confirmForceCloseChannels
			)
		}
	}
	
	func confirmForceCloseChannels() -> Void {
		log.trace("confirmForceCloseChannels()")
		
		mvi.intent(CloseChannelsConfiguration.IntentForceCloseAllChannels())
	}
}

fileprivate struct ConfirmationPopover : View {
	
	let confirmAction: () -> Void
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
		
			VStack(alignment: .leading) {
				Text("Are you sure you want to proceed?")
			}
			.padding(.bottom, 20)
			
			HStack {
				Button {
					didTapCancel()
				} label : {
					Text("Cancel")
				}
				.padding(.trailing, 10)
				
				Button {
					didTapConfirm()
				} label : {
					Text("Confirm")
				}
			}
			
		} // </VStack>
		.padding()
	}
	
	func didTapCancel() -> Void {
		log.trace("cancel()")
		popoverState.close()
	}
	
	func didTapConfirm() -> Void {
		log.trace("confirm()")
		popoverState.close()
		confirmAction()
	}
}

class ForceCloseChannelsView_Previews: PreviewProvider {
	
	static var previews: some View {
		
		NavigationView {
			ForceCloseChannelsView().mock(
				CloseChannelsConfiguration.ModelLoading()
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			ForceCloseChannelsView().mock(
				CloseChannelsConfiguration.ModelReady(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.normal
					)
				], address: "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			ForceCloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				], closing: Set<Bitcoin_kmpByteVector32>())
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			ForceCloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					),
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				], closing: Set<Bitcoin_kmpByteVector32>())
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		ConfirmationPopover(confirmAction: {})
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
	}
}
